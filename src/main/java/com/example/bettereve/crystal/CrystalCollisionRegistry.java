package com.example.bettereve.crystal;

import java.util.ArrayList;
import java.util.List;

/**
 * 基地碰撞箱注册表 — 记录所有已生成基地的 XZ 占地方块范围，
 * 供 FactionNodeEvaluator 查询，让 A* 路径规划绕过基地。
 */
public class CrystalCollisionRegistry {
    private static final List<CrystalBox> boxes = new ArrayList<>();

    public static void register(String crystalId, String factionId, double x, double z, int width, int height) {
        boxes.add(new CrystalBox(crystalId, factionId, x, z, width, height));
    }

    /** 清除所有注册（剧本重载时调用） */
    public static void clear() {
        boxes.clear();
    }

    /** 检查某方块坐标是否被任何基地碰撞箱占用 */
    public static boolean isBlockedAt(int blockX, int blockZ) {
        for (CrystalBox box : boxes) {
            if (box.contains(blockX, blockZ)) return true;
        }
        return false;
    }

    /** 基地 XZ 占地区域信息（兵线路线推荐用）：基地 id/阵营 + 中心坐标 + 水平边长 a（sizeKey 的 a×b 中的 a） */
    public record CrystalFootprint(String crystalId, String factionId, double centerX, double centerZ, int edgeSize) {}

    /** 获取所有基地的占地区域只读视图，供兵线路线推荐绕开基地使用 */
    public static List<CrystalFootprint> getFootprints() {
        List<CrystalFootprint> result = new ArrayList<>(boxes.size());
        for (CrystalBox box : boxes) {
            result.add(new CrystalFootprint(box.crystalId, box.factionId, box.centerX, box.centerZ, box.edgeSize));
        }
        return result;
    }

    /** 检查某方块坐标是否被指定阵营的基地碰撞箱占用 */
    public static boolean isBlockedAt(int blockX, int blockZ, String factionId) {
        for (CrystalBox box : boxes) {
            if (box.factionId.equals(factionId) && box.contains(blockX, blockZ)) return true;
        }
        return false;
    }

    /** 检查某方块坐标是否被指定基地（crystalId）的碰撞箱占用 */
    public static boolean isBlockedByCrystal(int blockX, int blockZ, String crystalId) {
        if (crystalId == null || crystalId.isEmpty()) return false;
        for (CrystalBox box : boxes) {
            if (box.crystalId.equals(crystalId) && box.contains(blockX, blockZ)) return true;
        }
        return false;
    }

    private static class CrystalBox {
        final String crystalId;
        final String factionId;
        final double centerX;
        final double centerZ;
        final int edgeSize; // sizeKey 的水平边长 a
        final int minX, maxX, minZ, maxZ;

        CrystalBox(String crystalId, String factionId, double x, double z, int width, int height) {
            this.crystalId = crystalId;
            this.factionId = factionId;
            this.centerX = x;
            this.centerZ = z;
            this.edgeSize = width;
            // 碰撞箱以水晶位置为中心向四周扩展；XZ 两个方向都用水平边长 a（sizeKey 的 a×b 中的 a），
            // 基地实体碰撞盒是 a×a 正方形，高度 b 不影响占地区域
            int halfW = (int) Math.ceil(width / 2.0);
            this.minX = (int) Math.floor(x) - halfW;
            this.maxX = (int) Math.floor(x) + halfW;
            this.minZ = (int) Math.floor(z) - halfW;
            this.maxZ = (int) Math.floor(z) + halfW;
        }

        boolean contains(int bx, int bz) {
            return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
        }
    }
}
