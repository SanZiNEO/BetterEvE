package com.sanzineo.bettereve.waypoint;

import com.sanzineo.bettereve.scenario.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路线顺序测试：waypoint 解析、本阵营顺序 / 其他阵营逆序、就近选路。
 */
class RouteTest {

    @BeforeEach
    void resetRegistries() {
        // 清空 static 注册表，避免测试间互相影响
        WaypointManager.loadFromScenario(null);
    }

    @Test
    void loadsRoutesAndResolvesWaypointsInDeclaredOrder() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a",
                "red_1", "red_2", "blue_1", "blue_2"));

        assertTrue(RouteManager.isLoaded());
        Route route = RouteManager.getRoute("route_a");
        assertNotNull(route);

        assertEquals(List.of("red_1", "red_2", "blue_1", "blue_2"), route.getWaypointIds());
        assertEquals(List.of("red_1", "red_2", "blue_1", "blue_2"), ids(route.getResolvedWaypoints()));
        assertSame(WaypointManager.getWaypointById("red_1"), route.getResolvedWaypoints().get(0));
    }

    @Test
    void factionPlanKeepsOwnOrderAndReversesOthers() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a",
                "red_1", "red_2", "blue_1", "blue_2"));
        Route route = RouteManager.getRoute("route_a");

        assertEquals(List.of("red_1", "red_2", "blue_2", "blue_1"),
                ids(route.getPlanForFaction("red")));
        assertEquals(List.of("blue_1", "blue_2", "red_2", "red_1"),
                ids(route.getPlanForFaction("blue")));
        assertEquals(List.of("blue_2", "blue_1", "red_2", "red_1"),
                ids(route.getPlanForFaction("green")));
    }

    @Test
    void firstWaypointMustBelongToFaction() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a",
                "red_1", "red_2", "blue_1", "blue_2"));
        Route route = RouteManager.getRoute("route_a");

        assertEquals("red_1", route.getFirstWaypointForFaction("red").getId());
        assertEquals("blue_1", route.getFirstWaypointForFaction("blue").getId());
        assertNull(route.getFirstWaypointForFaction("green"));
    }

    @Test
    void unknownWaypointIdsStayDeclaredButAreNotResolved() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a", "red_1", "ghost", "blue_1"));
        Route route = RouteManager.getRoute("route_a");

        assertEquals(List.of("red_1", "ghost", "blue_1"), route.getWaypointIds());
        assertEquals(List.of("red_1", "blue_1"), ids(route.getResolvedWaypoints()));
    }

    @Test
    void routesWithoutIdAreSkipped() {
        Scenario scenario = baseScenario();
        scenario.setRoutes(List.of(
                route(null, List.of("red_1")),
                route("", List.of("blue_1")),
                route("valid", List.of("red_1", "blue_1"))));
        RouteManager.loadFromScenario(scenario);

        assertEquals(1, RouteManager.getAllRoutes().size());
        assertNotNull(RouteManager.getRoute("valid"));
        assertNull(RouteManager.getRoute(""));
    }

    @Test
    void reloadingWithoutScenarioClearsRoutes() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a", "red_1", "blue_1"));
        assertTrue(RouteManager.isLoaded());

        WaypointManager.loadFromScenario(null);
        assertFalse(RouteManager.isLoaded());
        assertTrue(RouteManager.getAllRoutes().isEmpty());
        assertNull(RouteManager.getRoute("route_a"));
        assertTrue(WaypointManager.getAllWaypoints().isEmpty());
    }

    @Test
    void selectNearestRouteUsesFirstOwnWaypoint() {
        Scenario scenario = baseScenario();
        scenario.setRoutes(List.of(
                route("route_west", List.of("red_1", "blue_1")),
                route("route_east", List.of("red_2", "blue_2"))));
        WaypointManager.loadFromScenario(scenario);

        // red_1 中心 (-40,0)，red_2 中心 (-15,0)
        assertEquals("route_west", RouteManager.selectNearestRoute("red", -100, 0));
        // blue_1 中心 (40,0)，blue_2 中心 (15,0)
        assertEquals("route_east", RouteManager.selectNearestRoute("blue", 0, 0));
        assertEquals("route_west", RouteManager.selectNearestRoute("blue", 100, 0));
        // 没有本阵营 waypoint 的路线无法选中
        assertNull(RouteManager.selectNearestRoute("green", 0, 0));
    }

    @Test
    void nullAndUnknownRouteLookupsAreSafe() {
        WaypointManager.loadFromScenario(scenarioWithRoute("route_a", "red_1", "blue_1"));
        assertNull(RouteManager.getRoute(null));
        assertNull(RouteManager.getRoute("missing"));
        assertFalse(RouteManager.hasRoute(null));
        assertFalse(RouteManager.hasRoute("missing"));
        assertTrue(RouteManager.hasRoute("route_a"));
    }

    private static List<String> ids(List<WaypointRegion> regions) {
        List<String> ids = new ArrayList<>();
        for (WaypointRegion region : regions) {
            ids.add(region.getId());
        }
        return ids;
    }

    private static Scenario baseScenario() {
        Scenario scenario = new Scenario();
        scenario.setId("test");
        scenario.setWaypoints(List.of(
                waypoint("red_1", "red", -50, -10, -30, 10),
                waypoint("red_2", "red", -20, -10, -10, 10),
                waypoint("blue_1", "blue", 30, -10, 50, 10),
                waypoint("blue_2", "blue", 10, -10, 20, 10)));
        return scenario;
    }

    private static Scenario scenarioWithRoute(String routeId, String... waypointIds) {
        Scenario scenario = baseScenario();
        scenario.setRoutes(List.of(route(routeId, List.of(waypointIds))));
        return scenario;
    }

    private static Scenario.WaypointRegionDef waypoint(String id, String factionId,
                                                       double x1, double z1, double x2, double z2) {
        Scenario.WaypointRegionDef def = new Scenario.WaypointRegionDef();
        def.setId(id);
        def.setFactionId(factionId);
        def.setX1(x1);
        def.setZ1(z1);
        def.setX2(x2);
        def.setZ2(z2);
        return def;
    }

    private static Scenario.RouteDef route(String id, List<String> waypointIds) {
        Scenario.RouteDef def = new Scenario.RouteDef();
        def.setId(id);
        def.setWaypoints(waypointIds);
        return def;
    }
}
