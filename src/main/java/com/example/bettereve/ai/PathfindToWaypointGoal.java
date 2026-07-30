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
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 只有非中立阵营的实体才使用路径点推进
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null || "neutral".equals(factionId)) {
            return false;
        }
        
        // 检查是否有路径点
        List<WaypointRegion> waypoints = WaypointManager.getWaypointRegionsByFaction(factionId);
        return waypoints != null && !waypoints.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        // 如果已经到达最终目标，不再继续
        if (arrived) {
            return false;
        }
        return currentTarget != null && !navigation.isDone();
    }

    @Override
    public void start() {
        currentOrder = 0;
        arrived = false;
        selectNextTarget();
    }

    @Override
    public void stop() {
        navigation.stop();
    }

    @Override
    public void tick() {
        if (currentTarget == null) {
            selectNextTarget();
            return;
        }

        // 检查是否到达当前目标
        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            // 到达当前目标，选择下一个
            currentOrder = currentTarget.getOrder();
            selectNextTarget();
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

    private void selectNextTarget() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return;

        // 先尝试获取己方阵营的下一个路径点
        WaypointRegion next = WaypointManager.getNextWaypoint(factionId, currentOrder);
        if (next != null) {
            currentTarget = next;
            double cx = currentTarget.getCenterX();
            double cz = currentTarget.getCenterZ();
            navigation.moveTo(cx, mob.getY(), cz, 1.0);
            repathTimer = 0;
            return;
        }

        // 己方路径点走完了，尝试走敌方路径点（从大到小）
        if (findEnemyGoal()) {
            return;
        }

        // 没有更多路径点了，标记为到达
        arrived = true;
        currentTarget = null;
        navigation.stop();
    }

    private boolean findEnemyGoal() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return false;

        // 获取所有阵营
        List<WaypointRegion> allWaypoints = WaypointManager.getAllWaypoints();
        
        // 找到最大的order（敌方路径点从大到小走）
        WaypointRegion bestTarget = null;
        int bestOrder = Integer.MAX_VALUE;
        
        for (WaypointRegion region : allWaypoints) {
            if (!region.getFactionId().equals(factionId)) {
                int order = region.getOrder();
                // 敌方路径点：从大到小
                if (order > currentOrder && order < bestOrder) {
                    bestOrder = order;
                    bestTarget = region;
                }
            }
        }
        
        if (bestTarget != null) {
            currentTarget = bestTarget;
            double cx = currentTarget.getCenterX();
            double cz = currentTarget.getCenterZ();
            navigation.moveTo(cx, mob.getY(), cz, 1.0);
            repathTimer = 0;
            return true;
        }
        
        return false;
    }
}
