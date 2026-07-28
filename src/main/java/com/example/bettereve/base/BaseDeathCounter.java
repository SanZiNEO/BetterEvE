package com.example.bettereve.base;

import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基地死亡计数器（客户端专用）
 * 用于在客户端缓存和显示基地死亡计数
 * 使用ConcurrentHashMap实现线程安全
 */
public class BaseDeathCounter {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/BaseDeathCounter");
    
    // 使用ConcurrentHashMap确保线程安全
    private static final ConcurrentHashMap<String, Integer> deathCounts = new ConcurrentHashMap<>();
    
    /**
     * 为指定阵营增加死亡计数
     * @param factionId 阵营ID
     */
    public static void addDeath(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "neutral";
        }
        
        // 使用merge方法实现原子操作
        deathCounts.merge(factionId, 1, Integer::sum);
        LOGGER.debug("Added death for faction {}: total = {}", factionId, deathCounts.get(factionId));
    }
    
    /**
     * 获取指定阵营的死亡计数
     * @param factionId 阵营ID
     * @return 死亡计数，如果不存在则返回0
     */
    public static int getDeathCount(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "neutral";
        }
        
        return deathCounts.getOrDefault(factionId, 0);
    }
    
    /**
     * 获取所有阵营的死亡计数
     * @return 阵营ID到死亡计数的映射
     */
    public static Map<String, Integer> getAllDeathCounts() {
        // 返回副本以避免并发修改问题
        return new HashMap<>(deathCounts);
    }
    
    /**
     * 获取所有活跃阵营的死亡计数（包括默认阵营）
     * 确保所有已注册的阵营都有计数，即使为0
     * @return 所有活跃阵营的死亡计数映射
     */
    public static Map<String, Integer> getAllFactionDeathCounts() {
        Map<String, Integer> allCounts = new HashMap<>();
        
        // 首先添加所有已注册的阵营
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (faction.isEnabled()) {
                allCounts.put(faction.getId(), getDeathCount(faction.getId()));
            }
        }
        
        // 然后添加其他有计数的阵营（可能未在FactionRegistry中注册）
        for (Map.Entry<String, Integer> entry : deathCounts.entrySet()) {
            if (!allCounts.containsKey(entry.getKey())) {
                allCounts.put(entry.getKey(), entry.getValue());
            }
        }
        
        return allCounts;
    }
    
    /**
     * 清除所有死亡计数
     */
    public static void clearAll() {
        deathCounts.clear();
        LOGGER.debug("Cleared all base death counts");
    }
    
    /**
     * 清除指定阵营的死亡计数
     * @param factionId 阵营ID
     */
    public static void clearFaction(String factionId) {
        if (factionId != null) {
            deathCounts.remove(factionId);
            LOGGER.debug("Cleared death count for faction {}", factionId);
        }
    }
    
    /**
     * 重置所有计数为0
     */
    public static void resetAll() {
        // 重置所有现有条目的计数为0
        for (String factionId : deathCounts.keySet()) {
            deathCounts.put(factionId, 0);
        }
        LOGGER.debug("Reset all base death counts to 0");
    }
    
    /**
     * 获取总死亡计数
     * @return 所有阵营的总死亡数
     */
    public static int getTotalDeathCount() {
        return deathCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
    
    /**
     * 获取统计数据字符串
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        int total = getTotalDeathCount();
        int factions = deathCounts.size();
        return String.format("Base Deaths: total=%d, factions=%d", total, factions);
    }
}