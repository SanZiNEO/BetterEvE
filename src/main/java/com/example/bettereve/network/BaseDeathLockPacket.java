package com.example.bettereve.network;

import com.example.bettereve.base.BaseDeathLockManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * 基地死亡播报锁定确认包
 * 客户端 → 服务器，用于通知服务器客户端已开始播报
 * 服务器收到此包后锁定基地死亡逻辑，防止连锁计数
 */
public class BaseDeathLockPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/BaseDeathLockPacket");
    
    private final String factionId;
    private final long clientTimestamp;
    private final boolean isLockRequest;
    
    /**
     * 客户端确认播报开始
     * @param factionId 触发播报的阵营ID
     * @param clientTimestamp 客户端时间戳（毫秒）
     */
    public BaseDeathLockPacket(String factionId, long clientTimestamp) {
        this.factionId = factionId;
        this.clientTimestamp = clientTimestamp;
        this.isLockRequest = true;
    }
    
    /**
     * 客户端请求解锁（用于特殊情况）
     * @param factionId 阵营ID
     * @param isUnlockRequest 解锁请求标志
     */
    public BaseDeathLockPacket(String factionId, boolean isUnlockRequest) {
        this.factionId = factionId;
        this.clientTimestamp = System.currentTimeMillis();
        this.isLockRequest = !isUnlockRequest;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(factionId != null ? factionId : "neutral");
        buffer.writeLong(clientTimestamp);
        buffer.writeBoolean(isLockRequest);
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static BaseDeathLockPacket decode(FriendlyByteBuf buffer) {
        String factionId = buffer.readUtf(32767);
        long clientTimestamp = buffer.readLong();
        boolean isLockRequest = buffer.readBoolean();
        
        // 根据isLockRequest创建不同类型的包
        if (isLockRequest) {
            return new BaseDeathLockPacket(factionId, clientTimestamp);
        } else {
            return new BaseDeathLockPacket(factionId, false);
        }
    }
    
    /**
     * 处理数据包（在服务器端执行）
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 只在服务器端处理
            if (context.getDirection().getReceptionSide().isServer()) {
                handleOnServer();
            }
        });
        context.setPacketHandled(true);
    }
    
    /**
     * 在服务器端处理数据包
     */
    private void handleOnServer() {
        if (isLockRequest) {
            // 客户端确认播报开始，服务器开始锁定
            BaseDeathLockManager.startBroadcast(factionId);
            
            long serverTime = System.currentTimeMillis();
            long latency = serverTime - clientTimestamp;
            LOGGER.debug("Received lock request from client - faction: {}, clientTime: {}, serverTime: {}, latency: {}ms",
                factionId, clientTimestamp, serverTime, latency);
        } else {
            // 客户端请求解锁（特殊情况处理）
            BaseDeathLockManager.forceEndAllBroadcasts();
            
            LOGGER.debug("Received unlock request from client - faction: {}", factionId);
        }
    }
    
    /**
     * 获取阵营ID
     */
    public String getFactionId() {
        return factionId;
    }
    
    /**
     * 获取客户端时间戳
     */
    public long getClientTimestamp() {
        return clientTimestamp;
    }
    
    /**
     * 是否是锁定请求
     */
    public boolean isLockRequest() {
        return isLockRequest;
    }
    
    @Override
    public String toString() {
        return String.format("BaseDeathLockPacket{factionId=%s, clientTimestamp=%d, isLockRequest=%s}",
            factionId, clientTimestamp, isLockRequest);
    }
}