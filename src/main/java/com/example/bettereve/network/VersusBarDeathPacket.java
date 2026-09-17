package com.example.bettereve.network;

import com.example.bettereve.render.VersusHealthBarOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 格斗血条死亡通知数据包。
 * <p>
 * 与计分板链路（EntityKillPacket）完全独立：计分板只在"计分生物"死亡时收到通知，
 * 而格斗血条需要覆盖所有带阵营的生物（含召唤物），因此单独走一条通道。
 * <p>
 * 只传实体网络 ID —— 该 ID 由服务端分配（{@code ENTITY_COUNTER}），
 * 客户端在 {@code ClientPacketListener.handleAddEntity} 中用服务端 ID 覆盖本地 ID，
 * 因此两端 {@code entity.getId()} 恒等，可直接用于匹配。
 */
public class VersusBarDeathPacket {
    private final int victimEntityId;

    public VersusBarDeathPacket(int victimEntityId) {
        this.victimEntityId = victimEntityId;
    }

    public VersusBarDeathPacket(FriendlyByteBuf buffer) {
        this.victimEntityId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(victimEntityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> VersusHealthBarOverlay.onEntityDied(victimEntityId)));
        context.setPacketHandled(true);
    }
}
