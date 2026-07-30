package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * 寻路 AI — 使阵营实体沿着预设的路径点区域推进。
 * 
 * 推进规则：
 * 1. 先走自己阵营的路径点（从小到大）
 * 2. 走到终点后走敌方路径点（从大到小）
 * 3. 战斗优先于赶路（优先级低于战斗 Goal）
 */
public class PathfindToWaypointGoal extends Goal {
    private static final String WAYPOINT_ORDER_KEY = "BetterEvE:LastWaypointOrder";
    private static final Random RANDOM = new Random();
    
    private final PathfinderMob mob;
    private final PathNavigation navigation;
    private WaypointRegion currentTarget = null;
    private int currentOrder = 0;
    private boolean arrived = false;
    
    private static final int REPATH_INTERVAL = 40;
    private int repathTimer = 0;

    public PathfindToWaypointGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null || "neutral".equals(factionId)) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (arrived || mob.getTarget() != null) return false;
        return currentTarget != null && !navigation.isDone();
    }

    @Override
    public void start() {
        currentOrder = mob.getPersistentData().getInt(WAYPOINT_ORDER_KEY);
        arrived = false;
        checkCurrentPositionForWaypoint();
        selectNextTarget();
    }

    @Override
    public void stop() {
        mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
        navigation.stop();
    }

    @Override
    public void tick() {
        if (currentTarget == null) {
            selectNextTarget();
            return;
        }

        // 到达判定：踏入区域边界就算到达
        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            currentOrder = currentTarget.getOrder();
            mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            selectNextTarget();
            // 立即导航到下一个目标（区域内随机点），不等重路由
            if (currentTarget != null) {
                navigateToRandom(currentTarget);
                repathTimer = 0;
            }
            return;
        }

        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            navigateToRandom(currentTarget);
        }
    }

    /**
     * 导航到指定路径点区域内的随机点，避免全部挤向中心。
     */
    private void navigateToRandom(WaypointRegion region) {
        double x = region.getX1() + RANDOM.nextDouble() * (region.getX2() - region.getX1());
        double z = region.getZ1() + RANDOM.nextDouble() * (region.getZ2() - region.getZ1());
        navigation.moveTo(x, mob.getY(), z, 1.0);
    }

    /**
     * 检查实体当前位置是否在某个更远的路径点区域内（战斗中可能被推进去）。
     */
    private void checkCurrentPositionForWaypoint() {
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (r.getOrder() > currentOrder && r.isInside(mob.getX(), mob.getZ())) {
                currentOrder = r.getOrder();
                mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            }
        }
    }
    private void selectNextTarget() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) {
            arrived = true;
            return;
        }
        
        // 1. 先找自己阵营的路径点（从小到大）
        WaypointRegion next = WaypointManager.getNextWaypoint(factionId, currentOrder);
        if (next != null) {
            currentTarget = next;
            repathTimer = REPATH_INTERVAL;
            return;
        }
        
        // 2. 己方走完了，找敌方路径点（从大到小）
        // 临时提高 currentOrder，让 findEnemyGoal 能找到所有敌方路径点
        int saved = currentOrder;
        currentOrder = Integer.MAX_VALUE;
        boolean found = findEnemyGoal();
        currentOrder = saved;
        if (found) return;
        
        // 3. 没有更多路径点了
        arrived = true;
        currentTarget = null;
    }

    /**
     * 查找敌方阵营的下一个路径点（从大到小）。
     * 到达敌方路径点后 currentOrder 会被更新，
     * 后续调用自动找 order 更低的敌方路径点。
     */
    private boolean findEnemyGoal() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return false;

        WaypointRegion best = null;
        for (WaypointRegion region : WaypointManager.getAllWaypoints()) {
            if (factionId.equals(region.getFactionId())) continue;
            // 从大到小：选 order 小于当前 order 的最高 order
            if (region.getOrder() < currentOrder && (best == null || region.getOrder() > best.getOrder())) {
                best = region;
            }
        }

        if (best != null) {
            currentTarget = best;
            repathTimer = REPATH_INTERVAL;
            return true;
        }
        return false;
    }
}
