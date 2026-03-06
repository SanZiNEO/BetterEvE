package com.example.bettereve.network;

import com.example.bettereve.Bettereve;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 血条可见性同步数据包
 * 负责在服务器和客户端之间同步实体的血条显示状态
 */
public class HealthBarVisibilityPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/HealthBarVisibilityPacket");
    
    // 支持多种同步模式
    private static final int MODE_CLEAR_CACHE = 0;
    private static final int MODE_SINGLE_ENTITY = 1;
    private static final int MODE_BATCH_SYNC = 2;
    
    // 同步模式
    private int syncMode;
    
    // 单个实体同步数据
    private UUID entityUUID;
    private boolean showHealthBar;
    
    // 批量同步数据
    private Map<UUID, Boolean> entityVisibilityMap;
    
    /**
     * 构造方法：清除客户端缓存
     */
    public HealthBarVisibilityPacket() {
        this.syncMode = MODE_CLEAR_CACHE;
        this.entityVisibilityMap = new HashMap<>();
    }
    
    /**
     * 构造方法：同步单个实体的血条可见性
     */
    public HealthBarVisibilityPacket(UUID entityUUID, boolean showHealthBar) {
        this.syncMode = MODE_SINGLE_ENTITY;
        this.entityUUID = entityUUID;
        this.showHealthBar = showHealthBar;
        this.entityVisibilityMap = new HashMap<>();
    }
    
    /**
     * 构造方法：批量同步实体的血条可见性
     */
    public HealthBarVisibilityPacket(Map<UUID, Boolean> entityVisibilityMap) {
        this.syncMode = MODE_BATCH_SYNC;
        this.entityVisibilityMap = new HashMap<>(entityVisibilityMap);
    }
    
    /**
     * 编码数据包
     */
    public static void encode(HealthBarVisibilityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.syncMode);
        
        switch (packet.syncMode) {
            case MODE_SINGLE_ENTITY:
                buffer.writeUUID(packet.entityUUID);
                buffer.writeBoolean(packet.showHealthBar);
                break;
                
            case MODE_BATCH_SYNC:
                buffer.writeVarInt(packet.entityVisibilityMap.size());
                for (Map.Entry<UUID, Boolean> entry : packet.entityVisibilityMap.entrySet()) {
                    buffer.writeUUID(entry.getKey());
                    buffer.writeBoolean(entry.getValue());
                }
                break;
                
            case MODE_CLEAR_CACHE:
            default:
                // 清除缓存模式不需要额外数据
                break;
        }
    }
    
    /**
     * 解码数据包
     */
    public static HealthBarVisibilityPacket decode(FriendlyByteBuf buffer) {
        int syncMode = buffer.readVarInt();
        
        switch (syncMode) {
            case MODE_SINGLE_ENTITY:
                UUID entityUUID = buffer.readUUID();
                boolean showHealthBar = buffer.readBoolean();
                return new HealthBarVisibilityPacket(entityUUID, showHealthBar);
                
            case MODE_BATCH_SYNC:
                int size = buffer.readVarInt();
                Map<UUID, Boolean> entityVisibilityMap = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    UUID uuid = buffer.readUUID();
                    boolean visible = buffer.readBoolean();
                    entityVisibilityMap.put(uuid, visible);
                }
                return new HealthBarVisibilityPacket(entityVisibilityMap);
                
            case MODE_CLEAR_CACHE:
            default:
                return new HealthBarVisibilityPacket();
        }
    }
    
    /**
     * 处理数据包（客户端）
     */
    public static void handle(HealthBarVisibilityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                switch (packet.syncMode) {
                    case MODE_SINGLE_ENTITY:
                        ClientHealthBarVisibilityCache.setEntityHealthBarVisibility(
                                packet.entityUUID, packet.showHealthBar);
                        break;
                        
                    case MODE_BATCH_SYNC:
                        for (Map.Entry<UUID, Boolean> entry : packet.entityVisibilityMap.entrySet()) {
                            ClientHealthBarVisibilityCache.setEntityHealthBarVisibility(
                                    entry.getKey(), entry.getValue());
                        }
                        break;
                        
                    case MODE_CLEAR_CACHE:
                        ClientHealthBarVisibilityCache.clearCache();
                        break;
                }
            } catch (Exception e) {
                LOGGER.error("Error handling HealthBarVisibilityPacket: {}", e.getMessage());
                e.printStackTrace();
            }
        });
        context.setPacketHandled(true);
    }
    
    /**
     * 获取同步模式
     */
    public int getSyncMode() {
        return syncMode;
    }
    
    /**
     * 获取实体UUID（单个实体同步模式）
     */
    public UUID getEntityUUID() {
        return entityUUID;
    }
    
    /**
     * 获取是否显示血条（单个实体同步模式）
     */
    public boolean shouldShowHealthBar() {
        return showHealthBar;
    }
    
    /**
     * 获取批量同步数据（批量同步模式）
     */
    public Map<UUID, Boolean> getEntityVisibilityMap() {
        return entityVisibilityMap;
    }
    
    /**
     * 客户端血条可见性缓存
     */
    public static class ClientHealthBarVisibilityCache {
        private static final Map<UUID, Boolean> visibilityCache = new HashMap<>();
        
        /**
         * 设置实体的血条可见性
         */
        public static void setEntityHealthBarVisibility(UUID entityUUID, boolean showHealthBar) {
            visibilityCache.put(entityUUID, showHealthBar);
        }
        
        /**
         * 获取实体的血条可见性（默认返回true，显示血条）
         */
        public static boolean getEntityHealthBarVisibility(UUID entityUUID) {
            return visibilityCache.getOrDefault(entityUUID, true);
        }
        
        /**
         * 清除客户端缓存
         */
        public static void clearCache() {
            visibilityCache.clear();
        }
        
        /**
         * 检查是否有缓存的可见性数据
         */
        public static boolean hasVisibilityData(UUID entityUUID) {
            return visibilityCache.containsKey(entityUUID);
        }
    }
}