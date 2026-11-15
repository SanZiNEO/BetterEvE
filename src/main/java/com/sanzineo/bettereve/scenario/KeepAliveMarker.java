package com.sanzineo.bettereve.scenario;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * 单生物防清除标记。
 *
 * 与 {@link KeepAliveZoneManager} 的区域制互补：
 * - 区域制管"某片场地内的所有生物"（keepAliveZone + chunk ticket）；
 * - 本标记管"指定的单个生物"——没配场地的剧本里，用它让特定生物不被原版 despawn 清除。
 *
 * NBT 键写在 {@code getPersistentData()} 里，随存档持久化，跨区块卸载/重载有效：
 * - {@code BetterEvE:KeepAlive} 布尔标记
 *
 * 生效链路：
 * - {@code /bettereve spawn ... <count> keepAlive} 生成时调用 {@link #mark(Entity)} 并 setPersistenceRequired()
 * - despawn 判定见 TargetingHandler.onMobDespawn（带标记 → DENY，与区域判定并列）
 */
public final class KeepAliveMarker {
    private static final String KEEP_ALIVE_KEY = "BetterEvE:KeepAlive";

    private KeepAliveMarker() {
    }

    /** 打上防清除标记 */
    public static void mark(Entity entity) {
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean(KEEP_ALIVE_KEY, true);
    }

    /** 清除防清除标记 */
    public static void clear(Entity entity) {
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.remove(KEEP_ALIVE_KEY);
    }

    /** 该实体是否带防清除标记 */
    public static boolean isMarked(Entity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.contains(KEEP_ALIVE_KEY) && nbt.getBoolean(KEEP_ALIVE_KEY);
    }
}
