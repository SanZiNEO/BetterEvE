package com.example.bettereve.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 基地死亡播报状态管理器
 * 用于管理基地死亡播报期间的锁定状态，防止连锁计数问题
 * 
 * 核心功能：
 * 1. 播报期间锁定基地死亡逻辑
 * 2. 防止清场期间其他基地被杀死时增加计数
 * 3. 超时自动清理机制
 */
public class BaseDeathLockManager {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/BaseDeathLockManager");
    
    // 播报状态
    private static boolean isBroadcasting = false;
    private static long broadcastEndTime = 0;
    private static String broadcastingFactionId = null;
    
    // 播报超时时间（毫秒） - 设置为比客户端播报时间稍长，确保客户端播报结束
    private static final long BROADCAST_TIMEOUT_MS = 11000; // 11秒，比客户端的10秒长1秒
    
    // 播报锁定时间（毫秒）- 实际锁定时间
    private static final long BROADCAST_LOCK_DURATION_MS = 3500; // 3.5秒，确保清场期间锁定
    
    /**
     * 开始播报，锁定基地死亡逻辑
     * @param factionId 触发播报的阵营ID
     */
    public static synchronized void startBroadcast(String factionId) {
        isBroadcasting = true;
        broadcastingFactionId = factionId;
        broadcastEndTime = System.currentTimeMillis() + BROADCAST_LOCK_DURATION_MS;
        
        LOGGER.debug("Started broadcast lock for faction {} (ends at {}ms)", factionId, broadcastEndTime);
    }
    
    /**
     * 结束播报，解锁基地死亡逻辑
     */
    public static synchronized void endBroadcast() {
        LOGGER.debug("Ended broadcast lock for faction {}", broadcastingFactionId);
        
        isBroadcasting = false;
        broadcastingFactionId = null;
        broadcastEndTime = 0;
    }
    
    /**
     * 检查是否正在播报期间
     * 如果播报已超时，自动清理
     * @return true=正在播报期间，基地死亡逻辑应被锁定
     */
    public static synchronized boolean isBroadcasting() {
        // 如果未在播报状态，直接返回false
        if (!isBroadcasting) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        // 检查是否超时
        if (currentTime > broadcastEndTime) {
            LOGGER.debug("Broadcast lock for faction {} timed out, calling endBroadcast()", broadcastingFactionId);
            endBroadcast();
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取当前播报的阵营ID
     * @return 当前播报的阵营ID，如果没有播报则返回null
     */
    public static synchronized String getBroadcastingFactionId() {
        return broadcastingFactionId;
    }
    
    /**
     * 获取剩余播报时间（毫秒）
     * @return 剩余播报时间，如果不在播报中则返回0
     */
    public static synchronized long getRemainingBroadcastTime() {
        if (!isBroadcasting) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime >= broadcastEndTime) {
            return 0;
        }
        
        return broadcastEndTime - currentTime;
    }
    
    /**
     * 强制结束所有播报
     * 用于紧急情况或游戏重置
     */
    public static synchronized void forceEndAllBroadcasts() {
        if (isBroadcasting) {
            LOGGER.debug("Force ending all broadcast locks");
        }
        
        isBroadcasting = false;
        broadcastingFactionId = null;
        broadcastEndTime = 0;
    }
    
    /**
     * 检查指定的基地是否应该被处理
     * 如果正在播报期间，且基地不是触发播报的基地，则跳过处理
     * @param factionId 要检查的基地阵营ID
     * @return true=应该处理该基地死亡，false=应该跳过
     */
    public static synchronized boolean shouldProcessBaseDeath(String factionId) {
        if (!isBroadcasting()) {
            // 不在播报期间，所有基地死亡都应该处理
            return true;
        }
        
        // 在播报期间，只处理触发播报的基地（理论上不应该发生，因为已经锁定）
        // 或者如果播报已经超时，也应该处理
        return false;
    }
    
    /**
     * 获取状态信息（用于调试）
     * @return 状态信息字符串
     */
    public static synchronized String getStatus() {
        if (!isBroadcasting) {
            return "BaseDeathLockManager: No active broadcast";
        }
        
        long remaining = getRemainingBroadcastTime();
        return String.format("BaseDeathLockManager: Broadcasting faction=%s, remaining=%dms", 
            broadcastingFactionId, remaining);
    }
    
    /**
     * 获取统计信息
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        return String.format("BaseDeathLockManager: isBroadcasting=%s, faction=%s", 
            isBroadcasting, broadcastingFactionId);
    }
}