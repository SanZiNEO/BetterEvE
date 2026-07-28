package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.goal.GoalManager;
import com.example.bettereve.goal.GoalRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;

/**
 * 寻路 AI — 使阵营实体沿着预设的 Goal 区域路径推进。
 * 
 * 推进规则：
 * 1. 先走自己阵营的 Goal（从小到大）
 * 2. 走到终点后走敌方 Goal（从大到小）
 * 3. 战斗优先于赶路（优先级低于战斗 Goal）
 */
public class PathfindToGoalGoal extends Goal {
    private final PathfinderMob mob;
    private final PathNavigation navigation;
    private GoalRegion currentTarget = null;
    private int currentOrder = 0;
    private boolean arrived = false;
    
    // 重试间隔
    private static final int REPATH_INTERVAL = 40; // 2秒
    private int repathTimer = 0;

    public PathfindToGoalGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 只有在阵营系统启用且有 Goal 区域时工作
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null || "neutral".equals(factionId)) return false;
        
        List<GoalRegion> goals = GoalManager.getGoalRegionsByFaction(factionId);
        if (goals.isEmpty()) return false;
        
        // 检查是否已经到达了所有自己阵营的 Goal
        if (currentOrder >= goals.size()) {
            // 尝试找敌方 Goal
            return findEnemyGoal();
        }
        
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (currentTarget == null) return false;
        
        // 如果实体正在攻击，暂停寻路
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return false;
        }
        
        return true;
    }

    @Override
    public void start() {
        repathTimer = 0;
        arrived = false;
        selectNextTarget();
    }

    @Override
    public void stop() {
        navigation.stop();
    }

    @Override
    public void tick() {
        if (currentTarget == null) return;
        
        // 如果有敌对目标，暂停寻路
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return;
        
        repathTimer++;
        
        // 检查是否已到达当前 Goal
        if (!arrived && currentTarget.isInside(mob.getX(), mob.getZ())) {
            arrived = true;
            currentOrder = currentTarget.getOrder();
            selectNextTarget();
            return;
        }
        
        // 每 40 tick 重新规划路径
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            navigation.moveTo(currentTarget.getCenterX(), mob.getY(), currentTarget.getCenterZ(), 1.0);
        }
    }

    private void selectNextTarget() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        List<GoalRegion> goals = GoalManager.getGoalRegionsByFaction(factionId);
        
        // 先走自己阵营的 Goal
        if (!goals.isEmpty()) {
            if (currentOrder < goals.size()) {
                currentTarget = goals.get(Math.min(currentOrder, goals.size() - 1));
                navigation.moveTo(currentTarget.getCenterX(), mob.getY(), currentTarget.getCenterZ(), 1.0);
                return;
            }
        }
        
        // 已走完所有自己阵营的 Goal，走向敌方
        findEnemyGoal();
    }
    
    private boolean findEnemyGoal() {
        // 简化：走向最近的敌方 Goal
        List<GoalRegion> allGoals = GoalManager.getAllGoals();
        GoalRegion nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (GoalRegion goal : allGoals) {
            String goalFaction = goal.getFactionId();
            String mobFaction = FactionHelper.getEntityFactionId(mob);
            if (goalFaction != null && !goalFaction.equals(mobFaction)) {
                double dist = mob.distanceToSqr(goal.getCenterX(), mob.getY(), goal.getCenterZ());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = goal;
                }
            }
        }
        
        if (nearest != null) {
            currentTarget = nearest;
            navigation.moveTo(nearest.getCenterX(), mob.getY(), nearest.getCenterZ(), 1.0);
            return true;
        }
        return false;
    }
}
