package com.example.bettereve.waypoint;

import com.example.bettereve.scenario.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路线管理器 — 从剧本加载集中定义的 routes[]。
 * <p>
 * route 是所有阵营共用的路线分类；生物通过 RouteManager 查询/绑定。
 */
public class RouteManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RouteManager.class);
    private static final Map<String, Route> ROUTES = new HashMap<>();
    private static boolean loaded = false;

    public static void loadFromScenario(Scenario scenario) {
        ROUTES.clear();
        loaded = false;
        if (scenario == null || scenario.getRoutes() == null) {
            return;
        }
        for (Scenario.RouteDef def : scenario.getRoutes()) {
            if (def.getId() == null || def.getId().isEmpty()) {
                LOGGER.warn("Skipping route with missing id");
                continue;
            }
            Route route = new Route(def.getId(), def.getWaypoints());
            ROUTES.put(def.getId(), route);
        }
        loaded = true;
        LOGGER.info("Loaded {} routes from scenario {}", ROUTES.size(), scenario.getId());
    }

    public static Route getRoute(String id) {
        return id == null ? null : ROUTES.get(id);
    }

    public static boolean hasRoute(String id) {
        return id != null && ROUTES.containsKey(id);
    }

    public static List<Route> getAllRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(ROUTES.values()));
    }

    /**
     * 就近选择一条 route：比较生成位置到该 route 中“本阵营第一个 waypoint”的距离。
     */
    public static String selectNearestRoute(String factionId, double x, double z) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (Route route : ROUTES.values()) {
            WaypointRegion entry = route.getFirstWaypointForFaction(factionId);
            if (entry == null) {
                continue;
            }
            double d = entry.distanceToSqr(x, z);
            if (d < bestDist) {
                bestDist = d;
                best = route.getId();
            }
        }
        return best;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
