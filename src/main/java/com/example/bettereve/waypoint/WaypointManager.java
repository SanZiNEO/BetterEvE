package com.example.bettereve.waypoint;

import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 路径点管理器 — 从剧本加载并查询 Waypoint 区域。
 */
public class WaypointManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointManager.class);
    private static final List<WaypointRegion> waypoints = new ArrayList<>();
    private static boolean loaded = false;

    /**
     * 从当前剧本加载 Waypoint 区域。
     */
    public static void loadFromScenario(Scenario scenario) {
        waypoints.clear();
        loaded = false;
        // 剧本重载：全局兵线进度一并重置（区域定义可能变化）
        WaypointProgressRegistry.clearAll();
        
        if (scenario == null || scenario.getWaypoints() == null) return;
        
        for (Scenario.WaypointRegionDef def : scenario.getWaypoints()) {
            WaypointRegion region = new WaypointRegion(
                def.getId(), def.getFactionId(), def.getOrder(),
                def.getX1(), def.getZ1(), def.getX2(), def.getZ2()
            );
            waypoints.add(region);
        }
        
        loaded = true;
        LOGGER.info("Loaded {} waypoint regions from scenario {}", waypoints.size(), scenario.getId());
    }

    /**
     * 获取指定阵营的下一个 Waypoint 区域。
     */
    public static WaypointRegion getNextWaypoint(String factionId, int currentOrder) {
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()) && g.getOrder() > currentOrder)
                .min(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营 order 最大的 waypoint（阵营结束点）。
     */
    public static WaypointRegion getLastWaypoint(String factionId) {
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()))
                .max(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营中 order 小于 currentOrder 的最大 waypoint（降序阶段的下一步）。
     */
    public static WaypointRegion getPreviousWaypoint(String factionId, int currentOrder) {
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()) && g.getOrder() < currentOrder)
                .max(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取所有 Waypoint 区域。
     */
    public static List<WaypointRegion> getAllWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    /**
     * 检查是否已加载。
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
