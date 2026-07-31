package com.example.bettereve.waypoint;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 路径点区域 — 定义兵线路径点（XZ 无限高矩形柱体）。
 */
public class WaypointRegion {
    private final String id;
    private final String factionId;
    private final int order;
    private final double x1, z1, x2, z2;

    public WaypointRegion(String id, String factionId, int order, double x1, double z1, double x2, double z2) {
        this.id = id;
        this.factionId = factionId;
        this.order = order;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
    }

    public String getId() { return id; }
    public String getFactionId() { return factionId; }
    public int getOrder() { return order; }
    public double getX1() { return x1; }
    public double getZ1() { return z1; }
    public double getX2() { return x2; }
    public double getZ2() { return z2; }

    /**
     * 判断实体是否在该区域内（Y 轴无限高）。
     */
    public boolean isInside(double x, double z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    /**
     * 获取区域的中心点。
     */
    public double getCenterX() { return (x1 + x2) / 2.0; }
    public double getCenterZ() { return (z1 + z2) / 2.0; }

    /**
     * 获取该区域中心点到指定 XZ 坐标的平方距离（阵营排序用，避免 sqrt）。
     */
    public double distanceToSqr(double x, double z) {
        double dx = x - getCenterX();
        double dz = z - getCenterZ();
        return dx * dx + dz * dz;
    }

    /**
     * 获取区域的范围描述。
     */
    public String getDescription() {
        return String.format("Waypoint (%d: %.0f,%.0f)-(%.0f,%.0f)", order, x1, z1, x2, z2);
    }
}
