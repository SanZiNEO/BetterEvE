package com.example.bettereve.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * 自定义地面导航器 — 使用 FactionNodeEvaluator 让 A* 路径绕过基地碰撞箱。
 */
public class FactionGroundNavigator extends GroundPathNavigation {
    public FactionGroundNavigator(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FactionNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
