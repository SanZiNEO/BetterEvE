package com.example.bettereve.network;

import com.example.bettereve.DebugConfig;
import com.example.bettereve.base.BaseDeathCounter;
import com.example.bettereve.render.BaseDeathAnnounceRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * 基地死亡数据包
 * 专门用于传输基地死亡事件，只包含阵营ID
 * 保持轻量化和高效
 */
public class BaseDeathPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/BaseDeathPacket");
    
    // 同步模式
    public enum SyncMode {
        ADD_DEATH,      // 增加死亡计数
        CLEAR_ALL,      // 清除所有计数
        CLEAR_FACTION   // 清除指定阵营计数
    }
    
    private final SyncMode mode;
    private final String factionId; // 阵营ID
    
    /**
     * 增加死亡计数构造函数
     */
    public BaseDeathPacket(String factionId) {
        this.mode = SyncMode.ADD_DEATH;
        this.factionId = factionId;
    }
    
    /**
     * 清除所有计数构造函数
     */
    public BaseDeathPacket() {
        this.mode = SyncMode.CLEAR_ALL;
        this.factionId = null;
    }
    
    /**
     * 清除指定阵营计数构造函数
     * @param factionId 要清除的阵营ID
     * @param clearFaction 标记为清除指定阵营
     */
    public BaseDeathPacket(String factionId, boolean clearFaction) {
        this.mode = SyncMode.CLEAR_FACTION;
        this.factionId = factionId;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        
        switch (mode) {
            case ADD_DEATH:
            case CLEAR_FACTION:
                buffer.writeUtf(factionId != null ? factionId : "neutral");
                break;
            case CLEAR_ALL:
                // 不需要额外数据
                break;
        }
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static BaseDeathPacket decode(FriendlyByteBuf buffer) {
        SyncMode mode = buffer.readEnum(SyncMode.class);
        
        switch (mode) {
            case ADD_DEATH:
                String factionId = buffer.readUtf(32767);
                return new BaseDeathPacket(factionId);
                
            case CLEAR_ALL:
                return new BaseDeathPacket();
                
            case CLEAR_FACTION:
                String clearFactionId = buffer.readUtf(32767);
                return new BaseDeathPacket(clearFactionId, true);
                
            default:
                LOGGER.error("BaseDeathPacket: unknown sync mode: {}", mode);
                return new BaseDeathPacket();
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
            case ADD_DEATH:
                if (factionId != null) {
                    BaseDeathCounter.addDeath(factionId);
                    // 触发中央文字播报显示
                    BaseDeathAnnounceRenderer.addAnnounce(factionId);
                    
                    // 向服务器发送播报确认包，通知服务器客户端已开始播报
                    // 获取当前时间戳（客户端时间）
                    long clientTimestamp = System.currentTimeMillis();
                    
                    // 创建确认包
                    BaseDeathLockPacket lockPacket = new BaseDeathLockPacket(factionId, clientTimestamp);
                    
                    // 发送给服务器
                    NetworkManager.getChannel().sendToServer(lockPacket);
                    
                    if (DebugConfig.isDebugEnabled()) {
                        LOGGER.debug("Client: Added base death for faction {} and sent lock confirmation", factionId);
                    }
                }
                break;
                
            case CLEAR_ALL:
                BaseDeathCounter.clearAll();
                BaseDeathAnnounceRenderer.clearAll();
                if (DebugConfig.isDebugEnabled()) {
                    LOGGER.debug("Client: Cleared all base death counts and announces");
                }
                break;
                
            case CLEAR_FACTION:
                if (factionId != null) {
                    BaseDeathCounter.clearFaction(factionId);
                    BaseDeathAnnounceRenderer.clearFaction(factionId);
                    if (DebugConfig.isDebugEnabled()) {
                        LOGGER.debug("Client: Cleared base death count and announces for faction {}", factionId);
                    }
                }
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
     * 获取阵营ID
     */
    public String getFactionId() {
        return factionId;
    }
    
    @Override
    public String toString() {
        switch (mode) {
            case ADD_DEATH:
            case CLEAR_FACTION:
                return String.format("BaseDeathPacket{mode=%s, factionId=%s}", mode, factionId);
            case CLEAR_ALL:
                return String.format("BaseDeathPacket{mode=%s}", mode);
            default:
                return String.format("BaseDeathPacket{mode=UNKNOWN}");
        }
    }
}