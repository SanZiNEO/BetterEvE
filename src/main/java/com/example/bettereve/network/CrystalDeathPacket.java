package com.example.bettereve.network;

import com.example.bettereve.crystal.CrystalDeathCounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * 基地死亡数据包 — 只带阵营ID。
 * ADD_DEATH：增加得分
 * CLEAR_ALL / CLEAR_FACTION：清除计数
 */
public class CrystalDeathPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/CrystalDeathPacket");

    public enum SyncMode {
        ADD_DEATH,
        CLEAR_ALL,
        CLEAR_FACTION
    }

    private final SyncMode mode;
    private final String factionId;

    /** ADD_DEATH */
    public CrystalDeathPacket(String factionId) {
        this.mode = SyncMode.ADD_DEATH;
        this.factionId = factionId;
    }

    /** CLEAR_ALL */
    public CrystalDeathPacket() {
        this.mode = SyncMode.CLEAR_ALL;
        this.factionId = null;
    }

    /** CLEAR_FACTION */
    public CrystalDeathPacket(String factionId, boolean unused) {
        this.mode = SyncMode.CLEAR_FACTION;
        this.factionId = factionId;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        if (mode == SyncMode.ADD_DEATH || mode == SyncMode.CLEAR_FACTION) {
            buffer.writeUtf(factionId != null ? factionId : "");
        }
    }

    public static CrystalDeathPacket decode(FriendlyByteBuf buffer) {
        SyncMode mode = buffer.readEnum(SyncMode.class);
        switch (mode) {
            case ADD_DEATH:
                return new CrystalDeathPacket(buffer.readUtf(32767));
            case CLEAR_ALL:
                return new CrystalDeathPacket();
            case CLEAR_FACTION:
                return new CrystalDeathPacket(buffer.readUtf(32767), true);
            default:
                LOGGER.error("CrystalDeathPacket: unknown sync mode: {}", mode);
                return new CrystalDeathPacket();
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient();
            }
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleOnClient() {
        switch (mode) {
            case ADD_DEATH:
                if (factionId != null) {
                    CrystalDeathCounter.addDeath(factionId);
                }
                break;
            case CLEAR_ALL:
                CrystalDeathCounter.clearAll();
                break;
            case CLEAR_FACTION:
                if (factionId != null) {
                    CrystalDeathCounter.clearFaction(factionId);
                }
                break;
        }
    }
}
