package com.example.bettereve.network;

import com.example.bettereve.faction.ClientFactionCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 阵营同步数据包
 * 用于在服务器和客户端之间同步实体阵营信息
 * 保持轻量化和高效
 */
public class FactionSyncPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionSyncPacket");
    
    // 同步模式
    public enum SyncMode {
        SINGLE_ENTITY,    // 单个实体同步
        BATCH_ENTITIES,   // 批量实体同步
        CLEAR_CACHE       // 清除客户端缓存
    }
    
    private final SyncMode mode;
    private final Map<Integer, String> entityFactions; // 实体ID到阵营ID的映射
    private final int entityId; // 单个实体模式使用
    private final String factionId; // 单个实体模式使用
    
    /**
     * 单个实体同步构造函数
     */
    public FactionSyncPacket(int entityId, String factionId) {
        this.mode = SyncMode.SINGLE_ENTITY;
        this.entityId = entityId;
        this.factionId = factionId;
        this.entityFactions = new HashMap<>();
        this.entityFactions.put(entityId, factionId);
    }
    
    /**
     * 批量实体同步构造函数
     */
    public FactionSyncPacket(Map<Integer, String> entityFactions) {
        this.mode = SyncMode.BATCH_ENTITIES;
        this.entityFactions = new HashMap<>(entityFactions);
        this.entityId = -1;
        this.factionId = null;
    }
    
    /**
     * 清除缓存构造函数
     */
    public FactionSyncPacket() {
        this.mode = SyncMode.CLEAR_CACHE;
        this.entityFactions = new HashMap<>();
        this.entityId = -1;
        this.factionId = null;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        
        switch (mode) {
            case SINGLE_ENTITY:
                buffer.writeInt(entityId);
                buffer.writeUtf(factionId != null ? factionId : "");
                break;
                
            case BATCH_ENTITIES:
                buffer.writeInt(entityFactions.size());
                for (Map.Entry<Integer, String> entry : entityFactions.entrySet()) {
                    buffer.writeInt(entry.getKey());
                    buffer.writeUtf(entry.getValue() != null ? entry.getValue() : "");
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
    public static FactionSyncPacket decode(FriendlyByteBuf buffer) {
        SyncMode mode = buffer.readEnum(SyncMode.class);
        
        switch (mode) {
            case SINGLE_ENTITY:
                int entityId = buffer.readInt();
                String factionId = buffer.readUtf(32767);
                return new FactionSyncPacket(entityId, factionId);
                
            case BATCH_ENTITIES:
                int size = buffer.readInt();
                Map<Integer, String> factions = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    int id = buffer.readInt();
                    String fid = buffer.readUtf(32767);
                    factions.put(id, fid);
                }
                return new FactionSyncPacket(factions);
                
            case CLEAR_CACHE:
                return new FactionSyncPacket();
                
            default:
                LOGGER.error("FactionSyncPacket: unknown sync mode: {}", mode);
                return new FactionSyncPacket();
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
                ClientFactionCache.cacheFactionId(entityId, factionId);
                break;
                
            case BATCH_ENTITIES:
                for (Map.Entry<Integer, String> entry : entityFactions.entrySet()) {
                    ClientFactionCache.cacheFactionId(entry.getKey(), entry.getValue());
                }
                break;
                
            case CLEAR_CACHE:
                ClientFactionCache.clearAllCache();
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
     * 获取实体阵营映射
     */
    public Map<Integer, String> getEntityFactions() {
        return entityFactions;
    }
    
    /**
     * 获取实体ID（单实体模式）
     */
    public int getEntityId() {
        return entityId;
    }
    
    /**
     * 获取阵营ID（单实体模式）
     */
    public String getFactionId() {
        return factionId;
    }
    
    @Override
    public String toString() {
        return String.format("FactionSyncPacket{mode=%s, size=%d}", mode, entityFactions.size());
    }
}