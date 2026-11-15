package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阵型坐标计算测试：只验证纯数学结果（数量 / 坐标 / 排序），不启动游戏。
 */
class SpawnFormationTest {

    private static final double EPS = 1e-9;

    private static List<SpawnFormation.Offset> compute(int count, Scenario.Formation formation,
                                                       double radius, double spacing, double arc,
                                                       double width, double depth) {
        return SpawnFormation.compute(count, formation, radius, spacing, arc, width, depth);
    }

    /** 按 X 再按 Z 排序后逐点比对，避免依赖实现里排序的稳定性 */
    private static void assertOffsets(List<SpawnFormation.Offset> actual, double[][] expected) {
        List<double[]> actualPoints = new ArrayList<>();
        for (SpawnFormation.Offset offset : actual) {
            actualPoints.add(new double[]{offset.x(), offset.z()});
        }
        List<double[]> expectedPoints = new ArrayList<>();
        for (double[] point : expected) {
            expectedPoints.add(new double[]{point[0], point[1]});
        }
        Comparator<double[]> order = Comparator
                .comparingDouble((double[] point) -> point[0])
                .thenComparingDouble(point -> point[1]);
        actualPoints.sort(order);
        expectedPoints.sort(order);

        assertEquals(expectedPoints.size(), actualPoints.size(), "偏移数量");
        for (int i = 0; i < expectedPoints.size(); i++) {
            assertEquals(expectedPoints.get(i)[0], actualPoints.get(i)[0], EPS, "第 " + i + " 个偏移的 X");
            assertEquals(expectedPoints.get(i)[1], actualPoints.get(i)[1], EPS, "第 " + i + " 个偏移的 Z");
        }
    }

    private static void assertSortedByDistance(List<SpawnFormation.Offset> offsets) {
        double previous = Double.NEGATIVE_INFINITY;
        for (SpawnFormation.Offset offset : offsets) {
            double distance = Math.hypot(offset.x(), offset.z());
            assertTrue(distance >= previous - EPS, "偏移未按距中心由近到远排序");
            previous = distance;
        }
    }

    @Test
    void collapsesToCenterForCountAtMostOne() {
        for (int count : new int[]{0, 1}) {
            List<SpawnFormation.Offset> offsets = compute(count, Scenario.Formation.GRID, 2, 2, 360, 4, 4);
            assertEquals(1, offsets.size(), "count=" + count);
            assertEquals(0.0, offsets.get(0).x(), EPS);
            assertEquals(0.0, offsets.get(0).z(), EPS);
        }
    }

    @Test
    void nullFormationFallsBackToCircle() {
        List<SpawnFormation.Offset> fallback = compute(4, null, 10, 2, 360, 4, 4);
        List<SpawnFormation.Offset> circle = compute(4, Scenario.Formation.CIRCLE, 10, 2, 360, 4, 4);
        assertEquals(circle.size(), fallback.size());
        for (int i = 0; i < circle.size(); i++) {
            assertEquals(circle.get(i).x(), fallback.get(i).x(), EPS);
            assertEquals(circle.get(i).z(), fallback.get(i).z(), EPS);
        }
    }

    @Test
    void pointRepeatsCenter() {
        List<SpawnFormation.Offset> offsets = compute(5, Scenario.Formation.POINT, 2, 2, 360, 4, 4);
        assertEquals(5, offsets.size());
        assertOffsets(offsets, new double[][]{
                {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}
        });
    }

    @Test
    void lineXCentersAroundOrigin() {
        List<SpawnFormation.Offset> offsets = compute(3, Scenario.Formation.LINE_X, 2, 2, 360, 4, 4);
        assertOffsets(offsets, new double[][]{{-2, 0}, {0, 0}, {2, 0}});
        assertSortedByDistance(offsets);
    }

    @Test
    void lineZCentersAroundOrigin() {
        List<SpawnFormation.Offset> offsets = compute(4, Scenario.Formation.LINE_Z, 2, 1, 360, 4, 4);
        assertOffsets(offsets, new double[][]{{0, -1.5}, {0, -0.5}, {0, 0.5}, {0, 1.5}});
        assertSortedByDistance(offsets);
    }

    @Test
    void circleDividesFullCircle() {
        List<SpawnFormation.Offset> offsets = compute(4, Scenario.Formation.CIRCLE, 10, 2, 360, 4, 4);
        assertOffsets(offsets, new double[][]{
                {10, 0}, {0, 10}, {-10, 0}, {0, -10}
        });
        assertSortedByDistance(offsets);
    }

    @Test
    void circleFanSweepsConfiguredArc() {
        List<SpawnFormation.Offset> offsets = compute(3, Scenario.Formation.CIRCLE, 10, 2, 180, 4, 4);
        assertOffsets(offsets, new double[][]{
                {0, -10}, {10, 0}, {0, 10}
        });
        assertSortedByDistance(offsets);
    }

    @Test
    void nonPositiveArcDegradesToLine() {
        List<SpawnFormation.Offset> offsets = compute(3, Scenario.Formation.CIRCLE, 10, 2, 0, 4, 4);
        assertOffsets(offsets, new double[][]{{-2, 0}, {0, 0}, {2, 0}});
    }

    @Test
    void gridFillsSquareNearestFirst() {
        List<SpawnFormation.Offset> offsets = compute(5, Scenario.Formation.GRID, 2, 2, 360, 4, 4);
        assertOffsets(offsets, new double[][]{
                {-2, -2}, {0, -2}, {2, -2}, {-2, 0}, {0, 0}
        });
        assertSortedByDistance(offsets);

        List<SpawnFormation.Offset> nine = compute(9, Scenario.Formation.GRID, 2, 2, 360, 4, 4);
        assertEquals(9, nine.size());
        assertOffsets(nine, new double[][]{
                {-2, -2}, {0, -2}, {2, -2},
                {-2, 0}, {0, 0}, {2, 0},
                {-2, 2}, {0, 2}, {2, 2}
        });
        assertSortedByDistance(nine);
    }

    @Test
    void rectangleWalksTheBorder() {
        List<SpawnFormation.Offset> offsets = compute(4, Scenario.Formation.RECTANGLE, 2, 2, 360, 4, 2);
        assertOffsets(offsets, new double[][]{
                {-2, -1}, {1, -1}, {2, 1}, {-1, 1}
        });
        assertSortedByDistance(offsets);
    }

    @Test
    void triangleStartsAtVertices() {
        List<SpawnFormation.Offset> offsets = compute(3, Scenario.Formation.TRIANGLE, 2, 2, 360, 4, 4);
        double sideX = 2.0 * Math.sqrt(3.0) / 2.0;
        assertOffsets(offsets, new double[][]{
                {0, 2}, {sideX, -1}, {-sideX, -1}
        });
        assertSortedByDistance(offsets);
    }

    @Test
    void layoutOverloadUsesConfiguredValuesAndDefaults() {
        Scenario.SpawnLayout layout = new Scenario.SpawnLayout();
        layout.setFormation(Scenario.Formation.GRID);
        layout.setSpacing(2.0);
        List<SpawnFormation.Offset> fromLayout = SpawnFormation.compute(5, layout);
        assertOffsets(fromLayout, new double[][]{
                {-2, -2}, {0, -2}, {2, -2}, {-2, 0}, {0, 0}
        });
        assertSortedByDistance(fromLayout);

        // 默认 layout：圆形、半径 2、整圆
        List<SpawnFormation.Offset> defaultCircle = SpawnFormation.compute(4, new Scenario.SpawnLayout());
        assertOffsets(defaultCircle, new double[][]{
                {2, 0}, {0, 2}, {-2, 0}, {0, -2}
        });
    }
}
