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

    /** 检查某方块坐标是否被指定阵营的基地碰撞箱占用 */
    public static boolean isBlockedAt(int blockX, int blockZ, String factionId) {
        for (CrystalBox box : boxes) {
            if (box.factionId.equals(factionId) && box.contains(blockX, blockZ)) return true;
        }
        return false;
    }

    private static class CrystalBox {
        final String crystalId;
        final String factionId;
        final int minX, maxX, minZ, maxZ;

        CrystalBox(String crystalId, String factionId, double x, double z, int width, int height) {
            this.crystalId = crystalId;
            this.factionId = factionId;
            // sizeKey 的 width 跟 Minecraft 方块坐标对应，碰撞箱以水晶位置为中心向四周扩展
            int halfW = (int) Math.ceil(width / 2.0);
            int halfH = (int) Math.ceil(height / 2.0);
            this.minX = (int) Math.floor(x) - halfW;
            this.maxX = (int) Math.floor(x) + halfW;
            this.minZ = (int) Math.floor(z) - halfH;
            this.maxZ = (int) Math.floor(z) + halfH;
        }

        boolean contains(int bx, int bz) {
            return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
        }
    }
}
