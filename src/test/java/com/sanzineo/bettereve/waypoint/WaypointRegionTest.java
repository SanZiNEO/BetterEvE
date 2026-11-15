package com.sanzineo.bettereve.waypoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Waypoint 区域几何测试：边界归一化、包含判定、中心与距离。
 */
class WaypointRegionTest {

    @Test
    void normalizesReversedBoundsAndDefaultsLane() {
        WaypointRegion region = new WaypointRegion("wp", "red", null, 2, 10, 20, -10, 0);

        assertEquals("default", region.getLane());
        assertEquals(2, region.getOrder());
        assertEquals(-10.0, region.getX1(), 1e-9);
        assertEquals(0.0, region.getZ1(), 1e-9);
        assertEquals(10.0, region.getX2(), 1e-9);
        assertEquals(20.0, region.getZ2(), 1e-9);

        assertEquals(0.0, region.getCenterX(), 1e-9);
        assertEquals(10.0, region.getCenterZ(), 1e-9);

        assertTrue(region.isInside(-10, 0));
        assertTrue(region.isInside(10, 20));
        assertFalse(region.isInside(10.000001, 20));
        assertFalse(region.isInside(0, -0.000001));
    }

    @Test
    void keepsExplicitLaneAndComputesSquaredDistance() {
        WaypointRegion region = new WaypointRegion("wp", "blue", "mid", 5, 0, 0, 10, 10);

        assertEquals("mid", region.getLane());
        assertEquals("blue", region.getFactionId());
        assertEquals(0.0, region.distanceToSqr(5, 5), 1e-9);
        assertEquals(25.0, region.distanceToSqr(8, 9), 1e-9);
        assertEquals(50.0, region.distanceToSqr(0, 0), 1e-9);
        assertEquals(50.0, region.distanceToSqr(10, 10), 1e-9);
    }

    @Test
    void blankLaneFallsBackToDefault() {
        assertEquals("default", new WaypointRegion("wp", "red", "", 0, 0, 0, 1, 1).getLane());
        assertEquals("mid", new WaypointRegion("wp", "red", "mid", 0, 0, 0, 1, 1).getLane());
    }
}
