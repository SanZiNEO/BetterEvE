package com.example.bettereve.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * LevelSyncPacket - 等级同步数据包
 * 用于在服务器和客户端之间同步实体等级信息
 * 保持轻量化和高效
 */
public class LevelSyncPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/LevelSyncPacket");
    
    // 同步模式
    public enum SyncMode {
        SINGLE_ENTITY,    // 单个实体同步
        BATCH_ENTITIES,   // 批量实体同步
        CLEAR_CACHE       // 清除客户端缓存
    }
    
    private final SyncMode mode;
    private final Map<UUID, Integer> entityLevels; // 实体UUID到等级的映射
    private final UUID entityUUID; // 单个实体模式使用
    private final int level; // 单个实体模式使用
    
    /**
     * 单个实体同步构造函数
     */
    public LevelSyncPacket(UUID entityUUID, int level) {
        this.mode = SyncMode.SINGLE_ENTITY;
        this.entityUUID = entityUUID;
        this.level = level;
        this.entityLevels = new HashMap<>();
        this.entityLevels.put(entityUUID, level);
    }
    
    /**
     * 批量实体同步构造函数
     */
    public LevelSyncPacket(Map<UUID, Integer> entityLevels) {
        this.mode = SyncMode.BATCH_ENTITIES;
        this.entityLevels = new HashMap<>(entityLevels);
        this.entityUUID = null;
        this.level = 0;
    }
    
    /**
     * 清除缓存构造函数
     */
    public LevelSyncPacket() {
        this.mode = SyncMode.CLEAR_CACHE;
        this.entityLevels = new HashMap<>();
        this.entityUUID = null;
        this.level = 0;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        
        switch (mode) {
            case SINGLE_ENTITY:
                buffer.writeUUID(entityUUID);
                buffer.writeVarInt(level);
                break;
                
            case BATCH_ENTITIES:
                buffer.writeVarInt(entityLevels.size());
                for (Map.Entry<UUID, Integer> entry : entityLevels.entrySet()) {
                    buffer.writeUUID(entry.getKey());
                    buffer.writeVarInt(entry.getValue());
                }
                break;
                
            case CLEAR_CACHE:
                // 不需要额外数据
                break;
        }
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static LevelSyncPacket decode(FriendlyByteBuf buffer) {
        SyncMode mode = buffer.readEnum(SyncMode.class);
        
        switch (mode) {
            case SINGLE_ENTITY:
                UUID entityUUID = buffer.readUUID();
                int level = buffer.readVarInt();
                return new LevelSyncPacket(entityUUID, level);
                
            case BATCH_ENTITIES:
                int size = buffer.readVarInt();
                Map<UUID, Integer> levels = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    UUID id = buffer.readUUID();
                    int entityLevel = buffer.readVarInt();
                    levels.put(id, entityLevel);
                }
                return new LevelSyncPacket(levels);
                
            case CLEAR_CACHE:
                return new LevelSyncPacket();
                
            default:
                LOGGER.error("LevelSyncPacket: unknown sync mode: {}", mode);
                return new LevelSyncPacket();
        }
    }
    
    /**
     * 处理数据包（在客户端执行）
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 只在客户端处理
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient();
            }
        });
        context.setPacketHandled(true);
    }
    
    /**
     * 在客户端处理数据包
     */
    @OnlyIn(Dist.CLIENT)
    private void handleOnClient() {
        switch (mode) {
            case SINGLE_ENTITY:
                ClientLevelCache.cacheEntityLevel(entityUUID, level);
                break;
                
            case BATCH_ENTITIES:
                for (Map.Entry<UUID, Integer> entry : entityLevels.entrySet()) {
                    ClientLevelCache.cacheEntityLevel(entry.getKey(), entry.getValue());
                }
                break;
                
            case CLEAR_CACHE:
                ClientLevelCache.clearAllCache();
                break;
        }
    }
    
    /**
     * 获取同步模式
     */
    public SyncMode getMode() {
        return mode;
    }
    
    /**
     * 获取实体等级映射
     */
    public Map<UUID, Integer> getEntityLevels() {
        return entityLevels;
    }
    
    /**
     * 获取实体UUID（单实体模式）
     */
    public UUID getEntityUUID() {
        return entityUUID;
    }
    
    /**
     * 获取等级（单实体模式）
     */
    public int getLevel() {
        return level;
    }
    
    @Override
    public String toString() {
        return String.format("LevelSyncPacket{mode=%s, size=%d}", mode, entityLevels.size());
    }
    
    /**
     * ClientLevelCache - 客户端等级缓存
     * 用于在客户端缓存实体等级信息
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientLevelCache {
        private static final Map<UUID, Integer> levelCache = new HashMap<>();
        
        /**
         * 缓存实体等级
         */
        public static void cacheEntityLevel(UUID entityUUID, int level) {
            levelCache.put(entityUUID, level);
        }
        
        /**
         * 获取实体等级
         */
        public static int getEntityLevel(UUID entityUUID) {
            return levelCache.getOrDefault(entityUUID, 0);
        }
        
        /**
         * 清除所有缓存
         */
        public static void clearAllCache() {
            levelCache.clear();
        }
        
        /**
         * 检查是否有实体的等级缓存
         */
        public static boolean hasLevelCache(UUID entityUUID) {
            return levelCache.containsKey(entityUUID);
        }
    }
}