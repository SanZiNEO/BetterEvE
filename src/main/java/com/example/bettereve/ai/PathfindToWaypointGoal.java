package com.example.bettereve.ai;

import com.example.bettereve.crystal.CrystalCollisionRegistry;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.waypoint.Route;
import com.example.bettereve.waypoint.RouteManager;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointProgressRegistry;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 兵线寻路 AI（新版路线系统）。
 * <p>
 * 生物只绑定一条 route，走完即终点：
 * - 本阵营 waypoint 按 route 列表顺序走；
 * - 敌方 waypoint 按 route 列表逆序走。
 * 战斗优先：本 goal 只在 mob.getTarget() == null 时运行。
 */
public class PathfindToWaypointGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/PathGoal");

    /** 寻路调试日志总开关：排查目标问题时置为 false，减少刷屏 */
    private static final boolean DEBUG_LOGGING = false;

    /** 生物 NBT 持久化键：绑定的路线 id（卸载重生后仍走原路线） */
    private static final String NBT_ROUTE_KEY = "bettereve:route_id";

    /** 调试日志上下文 */
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

    /** 当前 route 的实际行走计划（已按本阵营顺序/敌方逆序展开） */
    private List<WaypointRegion> plan = new ArrayList<>();
    private int nextIndex;
    private WaypointRegion currentTarget;
    private int repathTimer;

    private boolean finished;
    private WaypointProgressRegistry.Progress progress;
    private String routeId;

    public PathfindToWaypointGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (finished) return false;
        String fid = FactionHelper.getEntityFactionId(mob);
        return fid != null && !fid.isEmpty(); // 只有有阵营的生物参与兵线寻路
    }

    @Override
    public boolean canContinueToUse() {
        return currentTarget != null && mob.getTarget() == null;
    }

    @Override
    public void start() {
        String selfId = FactionHelper.getEntityFactionId(mob);
        this.progress = WaypointProgressRegistry.getOrCreate(mob);
        this.routeId = resolveRoute(selfId);
        this.nextIndex = progress.nextIndex;
        this.finished = progress.finished;

        buildPlan(selfId, routeId);

        if (!finished) {
            fastForwardByPosition();
            selectNextTarget();
            advanceWhileInside();
            if (currentTarget != null) {
                navigateToWaypoint(currentTarget);
            }
        }
        if (DEBUG_LOGGING) LOGGER.debug("{} 寻路开始 route={} nextIndex={} planSize={} 进度={}",
                logContext(), routeId, nextIndex, plan.size(), progress);
    }

    @Override
    public void stop() {
        if (mob.getTarget() != null) {
            if (DEBUG_LOGGING) LOGGER.debug("{} 战斗中让位（stop）", logContext());
        }
        navigation.stop();
    }

    @Override
    public void tick() {
        if (mob.getTarget() != null) return;
        if (currentTarget == null) {
            selectNextTarget();
            if (currentTarget != null) navigateToWaypoint(currentTarget);
            return;
        }
        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            advanceWhileInside();
            return;
        }
        if (navigation.isDone()) {
            if (++repathTimer >= 20) {
                repathTimer = 0;
                navigateToWaypoint(currentTarget);
            }
        }
    }

    /** 根据绑定 route 生成当前阵营的实际行走计划 */
    private void buildPlan(String selfId, String routeId) {
        plan.clear();
        if (routeId == null) {
            finished = true;
            currentTarget = null;
            syncProgress();
            return;
        }
        Route route = RouteManager.getRoute(routeId);
        if (route == null) {
            LOGGER.warn("{} 绑定的路线 {} 不存在，寻路结束", logContext(), routeId);
            finished = true;
            currentTarget = null;
            syncProgress();
            return;
        }
        plan = route.getPlanForFaction(selfId);
        if (plan.isEmpty()) {
            LOGGER.warn("{} 路线 {} 对阵营 {} 没有可用 waypoint，寻路结束", logContext(), routeId, selfId);
            finished = true;
            currentTarget = null;
            syncProgress();
        }
    }

    /**
     * 绑定路线优先级：进度缓存 → 实体 NBT → 就近选择。
     * 绑定后写入 NBT 持久化。
     */
    private String resolveRoute(String selfId) {
        if (progress.routeId != null && RouteManager.hasRoute(progress.routeId)) {
            return progress.routeId;
        }
        String nbtRoute = mob.getPersistentData().getString(NBT_ROUTE_KEY);
        if (!nbtRoute.isEmpty() && RouteManager.hasRoute(nbtRoute)) {
            progress.routeId = nbtRoute;
            return nbtRoute;
        }
        String route = RouteManager.selectNearestRoute(selfId, mob.getX(), mob.getZ());
        progress.routeId = route;
        if (route != null) {
            mob.getPersistentData().putString(NBT_ROUTE_KEY, route);
        }
        return route;
    }

    /**
     * 位置快进：从当前进度向后扫描计划，只要当前位置已踏入后续 waypoint，
     * 就推进到该 waypoint 之后，防止战斗拖拽/中途生成导致走回头路。
     */
    private void fastForwardByPosition() {
        boolean advanced;
        do {
            advanced = false;
            for (int i = nextIndex; i < plan.size(); i++) {
                if (plan.get(i).isInside(mob.getX(), mob.getZ())) {
                    nextIndex = i + 1;
                    advanced = true;
                    break;
                }
            }
        } while (advanced);
        syncProgress();
    }

    /** 到达后推进：更新进度 → 选下一个 → 导航 */
    private void advanceWhileInside() {
        while (currentTarget != null && currentTarget.isInside(mob.getX(), mob.getZ())) {
            nextIndex++;
            syncProgress();
            selectNextTarget();
            if (currentTarget != null) {
                navigateToWaypoint(currentTarget);
            }
        }
    }

    private void selectNextTarget() {
        if (nextIndex >= plan.size()) {
            finished = true;
            currentTarget = null;
            syncProgress();
            if (DEBUG_LOGGING) LOGGER.debug("{} 路线走完，寻路结束", logContext());
            return;
        }
        currentTarget = plan.get(nextIndex);
    }

    private void syncProgress() {
        if (progress != null) {
            progress.routeId = routeId;
            progress.nextIndex = nextIndex;
            progress.finished = finished;
        }
    }

    /**
     * 导航到 waypoint 区域：生成一组安全候选目标点，交给原版 A* 多目标寻路，
     * 由 A* 自动选择当前最优可达点，避免“最远边绕路”和“最近边早停”。
     */
    private void navigateToWaypoint(WaypointRegion target) {
        Set<BlockPos> targets = buildCandidateTargets(target);
        Path path = navigation.createPath(targets, 2);
        if (path != null) {
            navigation.moveTo(path, 1.0);
            if (DEBUG_LOGGING) LOGGER.debug("{} 导航->区域={} 候选点={} 路径长度={}",
                    logContext(), target.getId(), targets.size(), path.getNodeCount());
        } else {
            // A* 找不到多目标路径时，退回旧的最远边单点方案，保证至少有目标可走
            navigateToFallbackPoint(target);
        }
    }

    /**
     * 在 waypoint 区域内生成候选目标点：3x3 网格（25%/50%/75%），
     * 剔除落在基地碰撞箱内的点；全部被剔除时退回区域中心。
     * Y 坐标自动按地面计算，交给原版导航处理。
     */
    private Set<BlockPos> buildCandidateTargets(WaypointRegion target) {
        Set<BlockPos> targets = new HashSet<>();
        double x1 = target.getX1(), x2 = target.getX2();
        double z1 = target.getZ1(), z2 = target.getZ2();

        double[] fractions = {0.25, 0.5, 0.75};
        for (double fx : fractions) {
            for (double fz : fractions) {
                int bx = (int) Math.floor(x1 + (x2 - x1) * fx);
                int bz = (int) Math.floor(z1 + (z2 - z1) * fz);
                if (CrystalCollisionRegistry.isBlockedAt(bx, bz)) {
                    continue;
                }
                int by = getTargetY(bx + 0.5, bz + 0.5);
                targets.add(new BlockPos(bx, by, bz));
            }
        }

        if (targets.isEmpty()) {
            int bx = (int) Math.floor((x1 + x2) / 2.0);
            int bz = (int) Math.floor((z1 + z2) / 2.0);
            int by = getTargetY(bx + 0.5, bz + 0.5);
            targets.add(new BlockPos(bx, by, bz));
        }
        return targets;
    }

    /**
     * 计算 (x,z) 处的地面站立 Y：从当前实体高度向下找第一个非空气方块，站到它上方。
     */
    private int getTargetY(double x, double z) {
        BlockPos pos = BlockPos.containing(x, mob.getY(), z);
        var level = mob.level();
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            pos = pos.below();
        }
        return pos.getY() + 1;
    }

    /**
     * 导航到目标区域的"最远边缘垂足点"（向区域内偏移 0.5 格）。
     * 作为多目标寻路失败时的兜底方案。
     */
    private void navigateToFallbackPoint(WaypointRegion target) {
        double mx = mob.getX(), mz = mob.getZ();
        double bestDist = -1;
        double bestX = 0, bestZ = 0;
        double ex1 = 0, ez1 = 0, ex2 = 0, ez2 = 0;

        double d = Math.abs(mx - target.getX1());
        if (d > bestDist) { bestDist = d; bestX = target.getX1() + 0.5; bestZ = Math.max(target.getZ1(), Math.min(mz, target.getZ2())); ex1 = target.getX1(); ez1 = target.getZ1(); ex2 = target.getX1(); ez2 = target.getZ2(); }
        d = Math.abs(mx - target.getX2());
        if (d > bestDist) { bestDist = d; bestX = target.getX2() - 0.5; bestZ = Math.max(target.getZ1(), Math.min(mz, target.getZ2())); ex1 = target.getX2(); ez1 = target.getZ1(); ex2 = target.getX2(); ez2 = target.getZ2(); }
        d = Math.abs(mz - target.getZ1());
        if (d > bestDist) { bestDist = d; bestX = Math.max(target.getX1(), Math.min(mx, target.getX2())); bestZ = target.getZ1() + 0.5; ex1 = target.getX1(); ez1 = target.getZ1(); ex2 = target.getX2(); ez2 = target.getZ1(); }
        d = Math.abs(mz - target.getZ2());
        if (d > bestDist) { bestDist = d; bestX = Math.max(target.getX1(), Math.min(mx, target.getX2())); bestZ = target.getZ2() - 0.5; ex1 = target.getX1(); ez1 = target.getZ2(); ex2 = target.getX2(); ez2 = target.getZ2(); }

        double[] recommended = recommendSafeTargetXZ(mx, mz, bestX, bestZ, ex1, ez1, ex2, ez2);
        bestX = recommended[0];
        bestZ = recommended[1];

        if (DEBUG_LOGGING) LOGGER.debug("{} 导航->区域={} 目标=({},{})", logContext(), target.getId(), String.format("%.0f", bestX), String.format("%.0f", bestZ));
        navigation.moveTo(bestX, mob.getY(), bestZ, 1.0);
    }

    private double[] recommendSafeTargetXZ(double mx, double mz, double footX, double footZ,
                                           double ex1, double ez1, double ex2, double ez2) {
        List<CrystalCollisionRegistry.CrystalFootprint> footprints = CrystalCollisionRegistry.getFootprints();
        if (footprints.isEmpty()) return new double[]{footX, footZ};

        double[][] candidates = {{footX, footZ}, {ex1, ez1}, {ex2, ez2}};
        for (double[] cand : candidates) {
            boolean blocked = false;
            for (CrystalCollisionRegistry.CrystalFootprint fp : footprints) {
                double half = fp.edgeSize();
                if (segmentIntersectsRect(mx, mz, cand[0], cand[1],
                        fp.centerX() - half, fp.centerZ() - half,
                        fp.centerX() + half, fp.centerZ() + half)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                return cand;
            }
        }
        return new double[]{footX, footZ};
    }

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
