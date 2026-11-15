package com.sanzineo.bettereve.network;

import com.sanzineo.bettereve.crystal.CrystalDeathCounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EntityKillPacket {
    private final String factionId;

    public EntityKillPacket(String factionId) {
        this.factionId = factionId;
    }

    public EntityKillPacket(FriendlyByteBuf buffer) {
        this.factionId = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(factionId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CrystalDeathCounter.addEntityKill(factionId));
        context.setPacketHandled(true);
    }
}
