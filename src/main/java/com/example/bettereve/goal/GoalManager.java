package com.example.bettereve.goal;

import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 目标区域管理器 — 从剧本加载并查询 Goal 区域。
 */
public class GoalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoalManager.class);
    private static final List<GoalRegion> goals = new ArrayList<>();
    private static boolean loaded = false;

    /**
     * 从当前剧本加载 Goal 区域。
     */
    public static void loadFromScenario(Scenario scenario) {
        goals.clear();
        loaded = false;
        
        if (scenario == null || scenario.getGoals() == null) return;
        
        for (Scenario.GoalRegionDef def : scenario.getGoals()) {
            GoalRegion region = new GoalRegion(
                def.getId(), def.getFactionId(), def.getOrder(),
                def.getX1(), def.getZ1(), def.getX2(), def.getZ2()
            );
            goals.add(region);
        }
        
        loaded = true;
        LOGGER.info("Loaded {} goal regions from scenario {}", goals.size(), scenario.getId());
    }

    /**
     * 获取指定阵营的 Goal 区域（按 order 排序）。
     */
    public static List<GoalRegion> getGoalRegionsByFaction(String factionId) {
        return goals.stream()
                .filter(g -> factionId.equals(g.getFactionId()))
                .sorted(Comparator.comparingInt(GoalRegion::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定阵营的下一个 Goal 区域。
     */
    public static GoalRegion getNextGoal(String factionId, int currentOrder) {
        return goals.stream()
                .filter(g -> factionId.equals(g.getFactionId()) && g.getOrder() > currentOrder)
                .min(Comparator.comparingInt(GoalRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营的第一个 Goal 区域。
     */
    public static GoalRegion getFirstGoal(String factionId) {
        return goals.stream()
                .filter(g -> factionId.equals(g.getFactionId()))
                .min(Comparator.comparingInt(GoalRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取所有 Goal 区域。
     */
    public static List<GoalRegion> getAllGoals() {
        return Collections.unmodifiableList(goals);
    }

    /**
     * 检查是否已加载。
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
