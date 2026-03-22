package com.example.bettereve.network;

import com.example.bettereve.base.BaseDeathCounter;
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
        CLEAR_COUNTS    // 清除所有计数
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
     * 清除计数构造函数
     */
    public BaseDeathPacket() {
        this.mode = SyncMode.CLEAR_COUNTS;
        this.factionId = null;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        
        if (mode == SyncMode.ADD_DEATH) {
            buffer.writeUtf(factionId != null ? factionId : "neutral");
        }
        // CLEAR_COUNTS模式不需要额外数据
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
                
            case CLEAR_COUNTS:
                return new BaseDeathPacket();
                
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
                    LOGGER.debug("Client: Added base death for faction {}", factionId);
                }
                break;
                
            case CLEAR_COUNTS:
                BaseDeathCounter.clearAll();
                LOGGER.debug("Client: Cleared all base death counts");
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
        if (mode == SyncMode.ADD_DEATH) {
            return String.format("BaseDeathPacket{mode=%s, factionId=%s}", mode, factionId);
        } else {
            return String.format("BaseDeathPacket{mode=%s}", mode);
        }
    }
}