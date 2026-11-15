package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.scenario.Scenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 指令召唤阵型计算。
 *
 * 输入"数量 + 阵型 + 参数"，输出相对召唤中心的 XZ 偏移列表。
 * 所有阵型在 count<=1 时退化为单点（原点），保证向后兼容旧的"原地召唤"行为。
 *
 * 结果统一按"距中心由近到远"排序，避免多行阵型出现外侧先占、内侧空置的观感问题。
 */
public final class SpawnFormation {

    /** 平面偏移（XZ） */
    public record Offset(double x, double z) {
    }

    private SpawnFormation() {
    }

    /**
     * 计算阵型偏移。
     *
     * @param count  生物数量
     * @param layout 剧本配置的阵型参数
     * @return 偏移列表，长度 = count
     */
    public static List<Offset> compute(int count, Scenario.SpawnLayout layout) {
        Scenario.Formation formation = layout.getFormation();
        double radius = layout.getRadius();
        double spacing = layout.getSpacing();
        double arc = layout.getArc();
        double width = layout.getWidth();
        double depth = layout.getDepth();
        return compute(count, formation, radius, spacing, arc, width, depth);
    }

    /**
     * 计算阵型偏移（显式参数版，便于指令临时覆盖）。
     */
    public static List<Offset> compute(int count, Scenario.Formation formation,
                                       double radius, double spacing,
                                       double arc, double width, double depth) {
        if (count <= 1) {
            return List.of(new Offset(0.0, 0.0));
        }
        if (formation == null) {
            formation = Scenario.ScenarioOptions.DEFAULT_SPAWN_FORMATION;
        }

        List<Offset> result = switch (formation) {
            case POINT -> repeatPoint(count);
            case LINE_X -> line(count, spacing, true);
            case LINE_Z -> line(count, spacing, false);
            case CIRCLE -> circle(count, radius, arc);
            case SQUARE -> rectangle(count, radius * 2.0, radius * 2.0);
            case DIAMOND -> diamond(count, radius);
            case RECTANGLE -> rectangle(count, width, depth);
            case TRIANGLE -> triangle(count, radius);
            case GRID -> grid(count, spacing);
        };

        // 统一按距中心由近到远排序（POINT / LINE / GRID 已自带合理顺序，排序不改变其语义）
        if (result.size() > 1) {
            result = new ArrayList<>(result);
            result.sort(Comparator.comparingDouble(o -> Math.hypot(o.x(), o.z())));
        }
        return result;
    }

    // ==================== 各阵型实现 ====================

    /** 全部重叠在中心 */
    private static List<Offset> repeatPoint(int count) {
        List<Offset> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(new Offset(0.0, 0.0));
        }
        return out;
    }

    /**
     * 直线。
     * 以召唤点为中心向两侧展开，整体沿 X 轴或 Z 轴居中。
     */
    private static List<Offset> line(int count, double spacing, boolean alongX) {
        List<Offset> out = new ArrayList<>(count);
        double start = -(count - 1) * spacing / 2.0;
        for (int i = 0; i < count; i++) {
            double d = start + i * spacing;
            out.add(alongX ? new Offset(d, 0.0) : new Offset(0.0, d));
        }
        return out;
    }

    /**
     * 圆形（或扇形）。
     * arc >= 360 时为首尾不相连的整圆均分；arc < 360 时沿指定张角从 -arc/2 扫到 +arc/2。
     * arc <= 0 时退化为沿 X 轴的直线（保留参数自由度）。
     */
    private static List<Offset> circle(int count, double radius, double arc) {
        if (arc <= 0.0) {
            return line(count, Scenario.ScenarioOptions.DEFAULT_SPAWN_SPACING, true);
        }
        List<Offset> out = new ArrayList<>(count);
        boolean fullCircle = arc >= 360.0;
        for (int i = 0; i < count; i++) {
            double angleDeg = fullCircle
                    ? 360.0 * i / count                    // 整圆：均分不自连
                    : -arc / 2.0 + arc * i / (count - 1);  // 扇形：含两端点
            double rad = Math.toRadians(angleDeg);
            out.add(new Offset(radius * Math.cos(rad), radius * Math.sin(rad)));
        }
        return out;
    }

    /**
     * 矩形边框（width × depth，以召唤点为中心）。
     * 按周长参数化取点，四个角随 t 自然落在拐角上。
     */
    private static List<Offset> rectangle(int count, double width, double depth) {
        double halfW = Math.max(width, 0.1) / 2.0;
        double halfD = Math.max(depth, 0.1) / 2.0;
        double perimeter = 4.0 * (halfW + halfD);

        List<Offset> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 从 -W 角开始，顺时针绕一圈；分母用 count 保证均分不自连
            double t = perimeter * i / count;
            double x;
            double z;
            if (t < 2.0 * halfW) {                 // 上边：-W → +W
                x = -halfW + t;
                z = -halfD;
            } else if (t < 2.0 * halfW + 2.0 * halfD) { // 右边：-D → +D
                x = halfW;
                z = -halfD + (t - 2.0 * halfW);
            } else if (t < 4.0 * halfW + 2.0 * halfD) { // 下边：+W → -W
                x = halfW - (t - 2.0 * halfW - 2.0 * halfD);
                z = halfD;
            } else {                                // 左边：+D → -D
                x = -halfW;
                z = halfD - (t - 4.0 * halfW - 2.0 * halfD);
            }
            out.add(new Offset(x, z));
        }
        return out;
    }

    /**
     * 菱形边框（45° 旋转的正方形，以召唤点为中心）。
     * 四个顶点为 (0,±r) 与 (±r,0)。
     */
    private static List<Offset> diamond(int count, double radius) {
        double r = Math.max(radius, 0.1);
        List<Offset> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = 4.0 * r * i / count;
            double x;
            double z;
            if (t < r) {              // 上边：顶点 → 右顶点
                x = t;
                z = r - t;
            } else if (t < 2.0 * r) { // 右边：右顶点 → 下顶点
                x = 2.0 * r - t;
                z = t - r;
            } else if (t < 3.0 * r) { // 下边：下顶点 → 左顶点
                x = t - 2.0 * r;
                z = r - (t - 2.0 * r);
            } else {                  // 左边：左顶点 → 上顶点
                x = -(4.0 * r - t);
                z = -(t - 3.0 * r);
            }
            out.add(new Offset(x, z));
        }
        return out;
    }

    /**
     * 三角形三顶点起排，其余沿三条边分布。
     * 顶点取外接圆半径 radius 的正三角：上顶点 (0, r)、右下 (0.866r, -0.5r)、左下 (-0.866r, -0.5r)。
     */
    private static List<Offset> triangle(int count, double radius) {
        double r = Math.max(radius, 0.1);
        double[][] vertices = {
                {0.0, r},
                {r * Math.sqrt(3) / 2.0, -r / 2.0},
                {-r * Math.sqrt(3) / 2.0, -r / 2.0}
        };
        double side = r * Math.sqrt(3.0);   // 正三角形边长
        double perimeter = side * 3.0;

        List<Offset> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = perimeter * i / count;
            // 每条边长度相同，直接按 t 定位到边与段内比例
            int edge = Math.min(2, (int) (t / side));
            double local = t - edge * side;
            double ratio = local / side;
            double[] from = vertices[edge];
            double[] to = vertices[(edge + 1) % 3];
            out.add(new Offset(
                    from[0] + (to[0] - from[0]) * ratio,
                    from[1] + (to[1] - from[1]) * ratio));
        }
        return out;
    }

    /**
     * 方形网格填充。
     * 以召唤点为中心对称铺开，点数不足时优先取靠近中心的格子（后续统一排序保证）。
     */
    private static List<Offset> grid(int count, double spacing) {
        double step = Math.max(spacing, 0.1);
        int side = (int) Math.ceil(Math.sqrt(count));
        double start = -(side - 1) * step / 2.0;

        List<Offset> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int row = i / side;
            int col = i % side;
            out.add(new Offset(start + col * step, start + row * step));
        }
        return out;
    }
}
