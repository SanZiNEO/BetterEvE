package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;

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
    
    private final PathfinderMob mob;
    private final PathNavigation navigation;
    private WaypointRegion currentTarget = null;
    private int currentOrder = 0;
    private boolean arrived = false;
    
    // 重试间隔
    private static final int REPATH_INTERVAL = 40; // 2秒
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
        if (arrived || mob.getTarget() != null) {
            return false;
        }
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

        // 检查是否到达当前目标（只要踏入区域边界就算到达）
        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            currentOrder = currentTarget.getOrder();
            mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            selectNextTarget();
            // 立即导航到下一个目标，不等重路由周期
            if (currentTarget != null) {
                navigation.moveTo(currentTarget.getCenterX(), mob.getY(), currentTarget.getCenterZ(), 1.0);
                repathTimer = 0;
            }
            return;
        }

        // 定期重新计算路径
        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            double cx = currentTarget.getCenterX();
            double cz = currentTarget.getCenterZ();
            navigation.moveTo(cx, mob.getY(), cz, 1.0);
        }
    }

    /**
     * 检查实体当前位置是否在某个更远的路径点区域内（战斗中可能被推进去）。
     * 如果是则跳过该点，避免战斗结束后往回走。
     */
    private void checkCurrentPositionForWaypoint() {
        List<WaypointRegion> all = WaypointManager.getAllWaypoints();
        for (WaypointRegion r : all) {
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

        WaypointRegion next = null;
        
        // 1. 先找自己阵营的路径点（从小到大）
        next = WaypointManager.getNextWaypoint(factionId, currentOrder);
        if (next != null) {
            currentTarget = next;
            repathTimer = REPATH_INTERVAL; // 立即可达
            return;
        }
        
        // 2. 己方走完了，找敌方路径点
        if (findEnemyGoal()) {
            return;
        }
        
        // 3. 没有更多路径点了，到达终点
        arrived = true;
        currentTarget = null;
    }

    /**
     * 查找敌方阵营的下一个路径点。
     * 策略：遍历所有非本阵营的路径点，选择 order 大于当前 order 的最小值（从边界向敌阵推进）。
     */
    private boolean findEnemyGoal() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return false;

        List<WaypointRegion> allWaypoints = WaypointManager.getAllWaypoints();
        WaypointRegion best = null;
        int bestOrder = Integer.MAX_VALUE;

        for (WaypointRegion region : allWaypoints) {
            if (factionId.equals(region.getFactionId())) continue;
            if (region.getOrder() > currentOrder && region.getOrder() < bestOrder) {
                best = region;
                bestOrder = region.getOrder();
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
