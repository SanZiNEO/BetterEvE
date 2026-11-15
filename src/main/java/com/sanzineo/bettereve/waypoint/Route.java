package com.sanzineo.bettereve.waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 路线定义 — 一条路线是所有阵营共用的 waypoint 引用列表。
 * <p>
 * 行走规则：
 * - 本阵营 waypoint：按 route 列表中的顺序走；
 * - 非本阵营 waypoint：按 route 列表中的逆序走。
 * 生物只绑定一条 route，走完即终点，不串其他路线、不走回头路。
 */
public class Route {

    private final String id;
    private final List<String> waypointIds;
    private final List<WaypointRegion> resolvedWaypoints;

    public Route(String id, List<String> waypointIds) {
        this.id = id;
        this.waypointIds = waypointIds == null ? new ArrayList<>() : new ArrayList<>(waypointIds);
        this.resolvedWaypoints = new ArrayList<>();
        for (String wpId : this.waypointIds) {
            WaypointRegion region = WaypointManager.getWaypointById(wpId);
            if (region != null) {
                resolvedWaypoints.add(region);
            }
        }
    }

    public String getId() {
        return id;
    }

    public List<String> getWaypointIds() {
        return Collections.unmodifiableList(waypointIds);
    }

    public List<WaypointRegion> getResolvedWaypoints() {
        return Collections.unmodifiableList(resolvedWaypoints);
    }

    /**
     * 生成某阵营生物沿此 route 的实际行走顺序：
     * 本阵营按列表顺序，非本阵营按列表逆序。
     */
    public List<WaypointRegion> getPlanForFaction(String factionId) {
        List<WaypointRegion> own = new ArrayList<>();
        List<WaypointRegion> others = new ArrayList<>();
        for (WaypointRegion region : resolvedWaypoints) {
            if (factionId.equals(region.getFactionId())) {
                own.add(region);
            } else {
                others.add(region);
            }
        }
        Collections.reverse(others);
        List<WaypointRegion> plan = new ArrayList<>(own.size() + others.size());
        plan.addAll(own);
        plan.addAll(others);
        return plan;
    }

    /**
     * 获取该 route 中指定阵营的第一个 waypoint（就近选路用）。
     */
    public WaypointRegion getFirstWaypointForFaction(String factionId) {
        for (WaypointRegion region : resolvedWaypoints) {
            if (factionId.equals(region.getFactionId())) {
                return region;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Route{" + id + ", waypoints=" + waypointIds + "}";
    }
}
