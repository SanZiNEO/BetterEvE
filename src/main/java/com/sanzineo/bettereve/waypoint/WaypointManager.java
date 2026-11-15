package com.sanzineo.bettereve.waypoint;

import com.sanzineo.bettereve.faction.Faction;
import com.sanzineo.bettereve.faction.FactionRegistry;
import com.sanzineo.bettereve.scenario.Scenario;
import com.sanzineo.bettereve.scenario.ScenarioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

        if (scenario == null || scenario.getWaypoints() == null) {
            RouteManager.loadFromScenario(null);
            loaded = true;
            return;
        }

        for (Scenario.WaypointRegionDef def : scenario.getWaypoints()) {
            WaypointRegion region = new WaypointRegion(
                def.getId(), def.getFactionId(), def.getLane(),
                def.getOrder(),
                def.getX1(), def.getZ1(), def.getX2(), def.getZ2()
            );
            waypoints.add(region);
        }

        // 先加载 waypoint 区域，再加载 routes（Route 构造时需要按 id 解析区域）
        RouteManager.loadFromScenario(scenario);

        loaded = true;
        LOGGER.info("Loaded {} waypoint regions from scenario {}", waypoints.size(), scenario.getId());
    }

    /**
     * 按 id 查找 waypoint 区域。
     */
    public static WaypointRegion getWaypointById(String id) {
        if (id == null) return null;
        for (WaypointRegion region : waypoints) {
            if (id.equals(region.getId())) {
                return region;
            }
        }
        return null;
    }

    /**
     * 获取指定阵营指定路线的下一个 Waypoint 区域（order 升序方向）。
     */
    public static WaypointRegion getNextWaypoint(String factionId, String lane, int currentOrder) {
        String l = normLane(lane);
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId())
                        && g.getLane().equals(l)
                        && g.getOrder() > currentOrder)
                .min(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营指定路线 order 最大的 waypoint（路线终点 = 升序末端 / 降序入口）。
     */
    public static WaypointRegion getLastWaypoint(String factionId, String lane) {
        String l = normLane(lane);
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()) && g.getLane().equals(l))
                .max(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营指定路线 order 最小的 waypoint（路线入口 = 升序起点）。
     */
    public static WaypointRegion getFirstWaypoint(String factionId, String lane) {
        String l = normLane(lane);
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()) && g.getLane().equals(l))
                .min(Comparator.comparingInt(WaypointRegion::getOrder))
                .orElse(null);
    }

    /**
     * 获取指定阵营的全部路线（lane 去重，字典序保证确定性）。
     */
    public static List<String> getLanes(String factionId) {
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId()))
                .map(WaypointRegion::getLane)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 指定阵营是否存在该路线（校验 NBT/进度缓存的路线锁定是否仍有效）。
     */
    public static boolean hasLane(String factionId, String lane) {
        String l = normLane(lane);
        return waypoints.stream()
                .anyMatch(g -> factionId.equals(g.getFactionId()) && g.getLane().equals(l));
    }

    /** 路线引用（阵营 + 路线），对面路线选择结果 */
    public record LaneRef(String factionId, String lane) {}

    /**
     * 就近选路：离 (x,z) 最近的路线入口（order 最小点）；阵营无任何路线时返回 null。
     */
    public static String selectNearestLane(String factionId, double x, double z) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String lane : getLanes(factionId)) {
            WaypointRegion entry = getFirstWaypoint(factionId, lane);
            if (entry == null) continue;
            double d = entry.distanceToSqr(x, z);
            if (d < bestDist) {
                bestDist = d;
                best = lane;
            }
        }
        return best;
    }

    /**
     * 对面最近路线：离本阵营路线终点最近的（其他阵营, 路线），
     * 用各自终点（order 最大点，即降序入口）代表路线；无其他阵营路线时返回 null。
     */
    public static LaneRef selectNearestEnemyLane(String selfId, String selfLane) {
        WaypointRegion selfEnd = getLastWaypoint(selfId, selfLane);
        if (selfEnd == null) return null;
        Set<String> others = new HashSet<>();
        for (WaypointRegion r : waypoints) {
            if (!selfId.equals(r.getFactionId())) others.add(r.getFactionId());
        }
        String bestFaction = null;
        String bestLane = null;
        double bestDist = Double.MAX_VALUE;
        for (String fid : others) {
            for (String lane : getLanes(fid)) {
                WaypointRegion end = getLastWaypoint(fid, lane);
                if (end == null) continue;
                double d = end.distanceToSqr(selfEnd.getCenterX(), selfEnd.getCenterZ());
                if (d < bestDist) {
                    bestDist = d;
                    bestFaction = fid;
                    bestLane = lane;
                }
            }
        }
        return bestFaction == null ? null : new LaneRef(bestFaction, bestLane);
    }

    /** null 归一化为默认路线 */
    private static String normLane(String lane) {
        return (lane == null || lane.isEmpty()) ? WaypointRegion.DEFAULT_LANE : lane;
    }

    /**
     * 获取指定阵营指定路线中 order 小于 currentOrder 的最大 waypoint（降序阶段的下一步）。
     */
    public static WaypointRegion getPreviousWaypoint(String factionId, String lane, int currentOrder) {
        String l = normLane(lane);
        return waypoints.stream()
                .filter(g -> factionId.equals(g.getFactionId())
                        && g.getLane().equals(l)
                        && g.getOrder() < currentOrder)
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
