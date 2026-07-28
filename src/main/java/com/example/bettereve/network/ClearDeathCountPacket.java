package com.example.bettereve.network;

import com.example.bettereve.base.BaseDeathCounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * 专用清除基地死亡计数数据包。
 * 替代原先在 BaseDeathPacket 中复用的清除标志位方案。
 */
public class ClearDeathCountPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/ClearDeathCountPacket");
    
    private final String factionId; // null = 清除全部

    public ClearDeathCountPacket() {
        this.factionId = null; // 清除全部
    }

    public ClearDeathCountPacket(String factionId) {
        this.factionId = factionId;
    }

    public ClearDeathCountPacket(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            this.factionId = buffer.readUtf();
        } else {
            this.factionId = null;
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        if (factionId != null) {
            buffer.writeBoolean(true);
            buffer.writeUtf(factionId);
        } else {
            buffer.writeBoolean(true);
            buffer.writeUtf("all"); // 客户端读不到实际 factionId 没关系，仅用于传输
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (factionId == null || "all".equals(factionId)) {
                BaseDeathCounter.clearAll();
                LOGGER.debug("Client: Cleared all base death counts via ClearDeathCountPacket");
            } else {
                BaseDeathCounter.clearFaction(factionId);
                LOGGER.debug("Client: Cleared base death count for faction {} via ClearDeathCountPacket", factionId);
            }
        });
        context.setPacketHandled(true);
    }
}
