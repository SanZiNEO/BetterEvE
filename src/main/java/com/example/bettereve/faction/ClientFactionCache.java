package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端阵营缓存系统
 * 用于在客户端存储实体ID到阵营ID的映射，避免频繁的网络请求
 * 保持轻量化和高性能
 */
@OnlyIn(Dist.CLIENT)
public class ClientFactionCache {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/ClientFactionCache");
    
    // 实体ID到阵营ID的映射（使用ConcurrentHashMap保证线程安全）
    private static final Map<Integer, String> entityFactionCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRY_MS = 30_000; // 30秒
    
    // 实体ID到缓存时间戳的映射
    private static final Map<Integer, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
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
        
        int entityId = entity.getId();
        
        // 检查缓存是否存在且未过期
        if (entityFactionCache.containsKey(entityId)) {
            Long timestamp = cacheTimestamps.get(entityId);
            if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                cacheHits++;
                return entityFactionCache.get(entityId);
            } else {
                // 缓存已过期，清除
                entityFactionCache.remove(entityId);
                cacheTimestamps.remove(entityId);
            }
        }
        
        cacheMisses++;
        return null;
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
        
        int entityId = entity.getId();
        long timestamp = System.currentTimeMillis();
        
        entityFactionCache.put(entityId, factionId);
        cacheTimestamps.put(entityId, timestamp);
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
        
        long timestamp = System.currentTimeMillis();
        
        entityFactionCache.put(entityId, factionId);
        cacheTimestamps.put(entityId, timestamp);
    }
    
    /**
     * 清除实体的缓存
     * @param entity 实体
     */
    public static void clearCache(Entity entity) {
        if (entity == null) {
            return;
        }
        
        int entityId = entity.getId();
        entityFactionCache.remove(entityId);
        cacheTimestamps.remove(entityId);
    }
    
    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        int size = entityFactionCache.size();
        entityFactionCache.clear();
        cacheTimestamps.clear();
        
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
     * 清理过期的缓存条目
     * 应该在游戏循环中定期调用
     */
    public static void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        for (Map.Entry<Integer, Long> entry : cacheTimestamps.entrySet()) {
            if (now - entry.getValue() > CACHE_EXPIRY_MS) {
                int entityId = entry.getKey();
                entityFactionCache.remove(entityId);
                cacheTimestamps.remove(entityId);
                removed++;
            }
        }
        
        if (removed > 0) {
            // 清理过期缓存条目，不记录调试日志
        }
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