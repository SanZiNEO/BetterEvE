package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端阵营缓存系统
 * 用于在客户端存储实体ID到阵营ID的映射，供血条渲染着色使用。
 * <p>
 * 注意：缓存<b>永不过期</b>——实体ID在生命周期内唯一，缓存条目只增不减，
 * 残留条目（实体已离场）量级极小（一场直播几百条），无害。
 * 曾经有过 30 秒 TTL，但服务端同步改为事件驱动后（实体加入/玩家登录一次性发送），
 * TTL 过期后无人补充 → 血条阵营颜色回退默认色，故移除。
 */
@OnlyIn(Dist.CLIENT)
public class ClientFactionCache {

    // 实体ID到阵营ID的映射（使用ConcurrentHashMap保证线程安全）
    private static final Map<Integer, String> entityFactionCache = new ConcurrentHashMap<>();

    // 缓存命中统计
    private static int cacheHits = 0;
    private static int cacheMisses = 0;

    /**
     * 获取实体的阵营ID（优先从缓存读取）
     * @param entity 实体
     * @return 阵营ID，如果未缓存则返回null
     */
    public static String getCachedFactionId(Entity entity) {
        if (entity == null) {
            return null;
        }

        String factionId = entityFactionCache.get(entity.getId());
        if (factionId != null) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
        return factionId;
    }

    /**
     * 缓存实体的阵营ID
     * @param entity 实体
     * @param factionId 阵营ID
     */
    public static void cacheFactionId(Entity entity, String factionId) {
        if (entity == null || factionId == null) {
            return;
        }
        entityFactionCache.put(entity.getId(), factionId);
    }

    /**
     * 缓存实体的阵营ID（通过实体ID）
     * @param entityId 实体ID
     * @param factionId 阵营ID
     */
    public static void cacheFactionId(int entityId, String factionId) {
        if (factionId == null) {
            return;
        }
        entityFactionCache.put(entityId, factionId);
    }

    /**
     * 清除所有缓存（CLEAR_CACHE 协议分支，防御性保留）
     */
    public static void clearAllCache() {
        entityFactionCache.clear();
    }

    /**
     * 获取缓存大小
     * @return 缓存中的实体数量
     */
    public static int getCacheSize() {
        return entityFactionCache.size();
    }

    /**
     * 获取缓存命中率
     * @return 命中率（0.0到1.0）
     */
    public static double getCacheHitRate() {
        int total = cacheHits + cacheMisses;
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHits / total;
    }

    /**
     * 重置缓存统计
     */
    public static void resetStatistics() {
        cacheHits = 0;
        cacheMisses = 0;
    }

    /**
     * 获取缓存统计信息
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        return String.format("Cache: size=%d, hits=%d, misses=%d, hitRate=%.2f%%",
            getCacheSize(), cacheHits, cacheMisses, getCacheHitRate() * 100);
    }
}
