package com.example.bettereve.ai;

import com.example.bettereve.crystal.CrystalCollisionRegistry;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointProgressRegistry;
import net.minecraft.world.entity.LivingEntity;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 兵线寻路 AI — 分 N 个阵营阶段，线性不循环、不走回头路：
 * 阶段 1（永远是本阵营）：本阵营 waypoint 按 order 从小到大依次走完；
 * 阶段 2..N（其他阵营，一次一个阵营，不混合）：阵营顺序按
 * "本阵营结束点（order 最大的 waypoint）→ 候选阵营结束点"的距离升序排列；
 * 其他阵营内部按 order 从大到小走完。
 * 全部阵营走完即结束。战斗优先：本 goal 只在 mob.getTarget() == null 时运行
 * （攻击目标选择器优先级 0 独立生效）。
 */
public class PathfindToWaypointGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/PathGoal");

    // 路线推荐：目标点与基地禁区之间的最小间距（生物半宽之外再留的余量，格）
    private static final double BASE_CLEARANCE_MARGIN = 1.0;

    /** 调试日志上下文：生物名称、位置、阵营、当前攻击目标（id/名称/阵营/位置） */
    private String logContext() {
        LivingEntity t = mob.getTarget();
        String targetDesc = t == null ? "null"
                : String.format("#%d %s faction=%s @(%.1f, %.1f, %.1f)", t.getId(),
                        t.getDisplayName().getString(), FactionHelper.getEntityFactionId(t),
                        t.getX(), t.getY(), t.getZ());
        return String.format("[%s] pos=(%.1f, %.1f, %.1f) faction=%s target=%s",
                mob.getDisplayName().getString(), mob.getX(), mob.getY(), mob.getZ(),
                FactionHelper.getEntityFactionId(mob), targetDesc);
    }

    private final PathfinderMob mob;
    private final PathNavigation navigation;

    private final List<String> stageOrder = new ArrayList<>(); // 阵营行走顺序（下标 0 = 本阵营）
    private WaypointRegion currentTarget;      // 当前导航目标
    private int repathTimer;                   // 导航完成后的重导航防抖（tick）

    // 进度字段为运行期快照，权威数据在 WaypointProgressRegistry（方案 B：全局管理，防走回头路）
    private int stageIndex;                    // 当前阶段
    private int currentOrder;                  // 当前阶段内已到达的 order（升序起点 0；降序起点 MAX_VALUE）
    private boolean finished;                  // 全部走完（线性终点）
    private WaypointProgressRegistry.Progress progress; // 注册表条目缓存（start 时获取）

    public PathfindToWaypointGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // 战斗优先：有攻击目标就不赶路；走完或中立则不再启动
    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (finished) return false;
        String fid = FactionHelper.getEntityFactionId(mob);
        return fid != null && !"neutral".equals(fid);
    }

    @Override
    public boolean canContinueToUse() {
        // 导航完成/路径中断不在此停止，由 tick() 统一处理重导航；战斗打断立即让位
        return currentTarget != null && mob.getTarget() == null;
    }

    @Override
    public void start() {
        String selfId = FactionHelper.getEntityFactionId(mob);
        // 全局进度（方案 B）：换阵营/首次自动重置，否则保留跨战斗打断的进度
        this.progress = WaypointProgressRegistry.getOrCreate(mob);
        stageIndex = progress.stageIndex;
        currentOrder = progress.currentOrder;
        finished = progress.finished;

        // 阵营顺序每次重算（确定性，无副作用）
        rebuildStageOrder(selfId);

        if (!finished) {
            // 位置快进：当前位置已踏入后续区域（战斗拖拽/中途生成）→ 推进进度，不走回头路
            fastForwardByPosition();
            selectNextTarget();
            advanceWhileInside();
            if (currentTarget != null) {
                navigateToFarthestEdge(currentTarget);
            }
        }
        LOGGER.debug("{} 寻路开始 阵营顺序={} 阶段={} order={} 进度={}",
                logContext(), stageOrder, stageIndex, currentOrder, progress);
    }

    @Override
    public void stop() {
        if (mob.getTarget() != null) {
            LOGGER.debug("{} 战斗中让位（stop）", logContext());
        }
        navigation.stop();
    }

    @Override
    public void tick() {
        if (mob.getTarget() != null) return; // 战斗中，等待 canContinueToUse 停止本 goal
        if (currentTarget == null) {
            selectNextTarget();
            if (currentTarget != null) navigateToFarthestEdge(currentTarget);
            return;
        }
        // 到达区域 → 推进到下一个目标（可能连续跨越多个重叠区域）
        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            advanceWhileInside();
            return;
        }
        // 导航完成但尚未踏入区域（路径中断/被卡住）→ 防抖重导航
        if (navigation.isDone()) {
            if (++repathTimer >= 20) {
                repathTimer = 0;
                navigateToFarthestEdge(currentTarget);
            }
        }
    }

    /** 当前阶段是否升序（阶段 0 本阵营升序；其他阵营降序） */
    private boolean isAscendingStage() {
        return stageIndex == 0;
    }

    /** 到达后推进：更新当前阶段进度 → 选下一个（或进入下一阵营）→ 导航 */
    private void advanceWhileInside() {
        while (currentTarget != null && currentTarget.isInside(mob.getX(), mob.getZ())) {
            currentOrder = currentTarget.getOrder();
            syncProgress();
            selectNextTarget();
            if (currentTarget != null) {
                navigateToFarthestEdge(currentTarget);
            }
        }
    }

    private void selectNextTarget() {
        if (stageIndex >= stageOrder.size()) {
            finished = true;
            currentTarget = null;
            syncProgress();
            LOGGER.debug("{} 所有阵营路径点已走完，寻路结束", logContext());
            return;
        }
        String stageFaction = stageOrder.get(stageIndex);
        // 本阵营（阶段 0）：order 升序；其他阵营：order 降序
        WaypointRegion next = isAscendingStage()
                ? WaypointManager.getNextWaypoint(stageFaction, currentOrder)
                : WaypointManager.getPreviousWaypoint(stageFaction, currentOrder);
        if (next != null) {
            currentTarget = next;
            return;
        }
        // 当前阵营走完 → 进入下一阵营（重置该阶段的 order 起点）
        stageIndex++;
        currentOrder = isAscendingStage() ? 0 : Integer.MAX_VALUE;
        syncProgress();
        LOGGER.debug("{} 阵营 {} 路径点走完，进入下一阵营 {}",
                logContext(), stageFaction,
                stageIndex < stageOrder.size() ? stageOrder.get(stageIndex) : "（终点）");
        selectNextTarget(); // 递归：跳过无 waypoint 的阵营 / 到达终点
    }

    /** 重算阵营行走顺序：本阵营 → 其余按"本阵营结束点 → 候选阵营结束点"距离升序（相等按 id 字典序） */
    private void rebuildStageOrder(String selfId) {
        stageOrder.clear();
        stageOrder.add(selfId);
        WaypointRegion selfEnd = WaypointManager.getLastWaypoint(selfId);
        Set<String> others = new HashSet<>();
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (!selfId.equals(r.getFactionId())) others.add(r.getFactionId());
        }
        List<String> sortedOthers = new ArrayList<>(others);
        sortedOthers.sort(Comparator
                .comparingDouble((String fid) -> {
                    WaypointRegion end = WaypointManager.getLastWaypoint(fid);
                    if (end == null || selfEnd == null) return 0.0;
                    return end.distanceToSqr(selfEnd.getCenterX(), selfEnd.getCenterZ());
                })
                .thenComparing(Comparator.naturalOrder()));
        stageOrder.addAll(sortedOthers);
    }

    /**
     * 位置快进：从当前进度起向后扫描所有阶段的未到达区域，
     * 只要当前位置已踏入其中（沿行进方向最靠前的），就把进度推进到该区域。
     * 防止战斗拖拽/中途生成导致的走回头路（如被目标吸引拖到敌后，恢复后直接打敌后而非掉头重走）。
     */
    private void fastForwardByPosition() {
        boolean advanced;
        do {
            advanced = false;
            if (stageIndex >= stageOrder.size()) break;
            String sf = stageOrder.get(stageIndex);
            boolean asc = isAscendingStage();
            // 本阶段所有未到达区域中，沿行进方向第一个包含当前位置的
            WaypointRegion hit = null;
            for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
                if (!sf.equals(r.getFactionId())) continue;
                boolean unarrived = asc ? r.getOrder() > currentOrder : r.getOrder() < currentOrder;
                if (!unarrived || !r.isInside(mob.getX(), mob.getZ())) continue;
                if (hit == null || (asc ? r.getOrder() < hit.getOrder() : r.getOrder() > hit.getOrder())) {
                    hit = r; // 升序取最小 order（下一步），降序取最大 order（下一步）
                }
            }
            if (hit != null) {
                currentOrder = hit.getOrder();
                advanced = true;
                continue;
            }
            // 本阶段无可快进区域 → 检查后续阶段（后续阶段均为降序）：任一区域包含当前位置则进入该阶段
            for (int i = stageIndex + 1; i < stageOrder.size(); i++) {
                String nf = stageOrder.get(i);
                WaypointRegion stageHit = null;
                for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
                    if (nf.equals(r.getFactionId()) && r.isInside(mob.getX(), mob.getZ())
                            && (stageHit == null || r.getOrder() > stageHit.getOrder())) {
                        stageHit = r; // 降序阶段入口 = order 最大者
                    }
                }
                if (stageHit != null) {
                    stageIndex = i;
                    currentOrder = stageHit.getOrder();
                    advanced = true;
                    break;
                }
            }
        } while (advanced);
        syncProgress();
    }

    /** 将运行期进度快照写回全局注册表 */
    private void syncProgress() {
        if (progress != null) {
            progress.stageIndex = stageIndex;
            progress.currentOrder = currentOrder;
            progress.finished = finished;
        }
    }

    /**
     * 导航到目标区域的"最远边缘垂足点"（向区域内偏移 0.5 格）。
     * 垂足点保证实体到达时已踏入区域（isInside 为 true），
     * 0.5 格偏移防止停在边缘线上导致 navigation.isDone() 与 isInside 永远不一致。
     * 原版 A* 寻路不感知实体，基地实体会挡住生物去路（互推卡死）；
     * 因此额外做 XZ 路线推荐：若"生物当前位置 → 垂足点"的直线走廊穿过任一基地禁区
     * （基地 a×a 占地 + 生物半宽 + 余量），则将目标点沿该边缘滑动到安全候选点，
     * 让生物从基地侧面绕行进入区域。Y 轴完全交给原版导航（getGroundY 贴地）。
     */
    private void navigateToFarthestEdge(WaypointRegion target) {
        double mx = mob.getX(), mz = mob.getZ();
        double bestDist = -1;
        double bestX = 0, bestZ = 0;
        // 被选中最远边的两个端点（路线推荐候选点滑动用）
        double ex1 = 0, ez1 = 0, ex2 = 0, ez2 = 0;

        double d = Math.abs(mx - target.getX1());
        if (d > bestDist) { bestDist = d; bestX = target.getX1() + 0.5; bestZ = Math.max(target.getZ1(), Math.min(mz, target.getZ2())); ex1 = target.getX1(); ez1 = target.getZ1(); ex2 = target.getX1(); ez2 = target.getZ2(); }
        d = Math.abs(mx - target.getX2());
        if (d > bestDist) { bestDist = d; bestX = target.getX2() - 0.5; bestZ = Math.max(target.getZ1(), Math.min(mz, target.getZ2())); ex1 = target.getX2(); ez1 = target.getZ1(); ex2 = target.getX2(); ez2 = target.getZ2(); }
        d = Math.abs(mz - target.getZ1());
        if (d > bestDist) { bestDist = d; bestX = Math.max(target.getX1(), Math.min(mx, target.getX2())); bestZ = target.getZ1() + 0.5; ex1 = target.getX1(); ez1 = target.getZ1(); ex2 = target.getX2(); ez2 = target.getZ1(); }
        d = Math.abs(mz - target.getZ2());
        if (d > bestDist) { bestDist = d; bestX = Math.max(target.getX1(), Math.min(mx, target.getX2())); bestZ = target.getZ2() - 0.5; ex1 = target.getX1(); ez1 = target.getZ2(); ex2 = target.getX2(); ez2 = target.getZ2(); }

        // XZ 路线推荐：直线走廊穿过基地禁区则沿边滑动目标点
        double[] recommended = recommendSafeTargetXZ(mx, mz, bestX, bestZ, ex1, ez1, ex2, ez2);
        bestX = recommended[0];
        bestZ = recommended[1];

        LOGGER.debug("{} 导航->区域={} order={} 目标=({},{})",
                logContext(), target.getId(), target.getOrder(),
                String.format("%.0f", bestX), String.format("%.0f", bestZ));
        navigation.moveTo(bestX, mob.getY(), bestZ, 1.0);
    }

    /**
     * XZ 路线推荐：候选点（原始垂足点 + 该边两个端点）中，取第一个
     * "生物当前位置 → 候选点"直线走廊不穿过任何基地禁区（基地 a×a + 生物半宽 + 余量）的点。
     * 找不到安全点（如生物已位于禁区内）时回退原始垂足点，行为不劣于原逻辑。
     */
    private double[] recommendSafeTargetXZ(double mx, double mz, double footX, double footZ,
                                           double ex1, double ez1, double ex2, double ez2) {
        List<CrystalCollisionRegistry.CrystalFootprint> footprints = CrystalCollisionRegistry.getFootprints();
        if (footprints.isEmpty()) return new double[]{footX, footZ};

        double clearance = mob.getBbWidth() / 2.0 + BASE_CLEARANCE_MARGIN;
        double[][] candidates = {{footX, footZ}, {ex1, ez1}, {ex2, ez2}};
        for (double[] cand : candidates) {
            boolean blocked = false;
            for (CrystalCollisionRegistry.CrystalFootprint fp : footprints) {
                double half = fp.edgeSize() / 2.0 + clearance;
                if (segmentIntersectsRect(mx, mz, cand[0], cand[1],
                        fp.centerX() - half, fp.centerZ() - half,
                        fp.centerX() + half, fp.centerZ() + half)) {
                    LOGGER.debug("{} 候选点 ({},{}) 被基地 #{}（faction={} 中心=({},{}) a={}）禁区遮挡",
                            logContext(), String.format("%.1f", cand[0]), String.format("%.1f", cand[1]),
                            fp.crystalId(), fp.factionId(),
                            String.format("%.1f", fp.centerX()), String.format("%.1f", fp.centerZ()), fp.edgeSize());
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                return cand;
            }
        }
        LOGGER.debug("{} 所有候选点均被基地禁区遮挡，回退原始垂足点 ({},{})",
                logContext(), String.format("%.1f", footX), String.format("%.1f", footZ));
        return new double[]{footX, footZ};
    }

    /** 线段与 XZ 平面轴对齐矩形是否相交（Liang-Barsky 裁剪，含端点在矩形内） */
    private static boolean segmentIntersectsRect(double x1, double z1, double x2, double z2,
                                                 double minX, double minZ, double maxX, double maxZ) {
        double dx = x2 - x1, dz = z2 - z1;
        double t0 = 0.0, t1 = 1.0;
        double[] p = {-dx, dx, -dz, dz};
        double[] q = {x1 - minX, maxX - x1, z1 - minZ, maxZ - z1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return false;
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0.0) {
                    if (t > t1) return false;
                    if (t > t0) t0 = t;
                } else {
                    if (t < t0) return false;
                    if (t < t1) t1 = t;
                }
            }
        }
        return true;
    }
}
