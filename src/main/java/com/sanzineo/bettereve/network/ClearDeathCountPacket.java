package com.sanzineo.bettereve.network;

import com.sanzineo.bettereve.crystal.CrystalDeathCounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 专用清除基地死亡计数数据包。
 * 替代原先在 CrystalDeathPacket 中复用的清除标志位方案。
 */
public class ClearDeathCountPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/ClearDeathCountPacket");
    
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
            buffer.writeBoolean(false); // 修复：原来两分支都写 true，且丢失 factionId
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (factionId == null || "all".equals(factionId)) {
                CrystalDeathCounter.clearAll();
                LOGGER.debug("Client: Cleared all base death counts via ClearDeathCountPacket");
            } else {
                CrystalDeathCounter.clearFaction(factionId);
                LOGGER.debug("Client: Cleared base death count for faction {} via ClearDeathCountPacket", factionId);
            }
        });
        context.setPacketHandled(true);
    }
}
