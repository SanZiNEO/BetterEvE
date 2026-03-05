package com.example.bettereve.network;

import com.example.bettereve.Bettereve;
import com.example.bettereve.render.SimpleHealthBarRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 血条缩放数据包
 * 用于在服务器和客户端之间同步血条缩放设置
 */
public class HealthBarScalePacket {
    
    private static final ResourceLocation PACKET_ID = new ResourceLocation(Bettereve.MODID, "health_bar_scale");
    
    private final float scale;
    private final boolean isReset;
    
    public HealthBarScalePacket(float scale) {
        this.scale = scale;
        this.isReset = false;
    }
    
    public HealthBarScalePacket() {
        this.scale = 1.0f;
        this.isReset = true;
    }
    
    public float getScale() {
        return scale;
    }
    
    public boolean isReset() {
        return isReset;
    }
    
    public static HealthBarScalePacket decode(FriendlyByteBuf buf) {
        boolean isReset = buf.readBoolean();
        if (isReset) {
            return new HealthBarScalePacket();
        } else {
            float scale = buf.readFloat();
            return new HealthBarScalePacket(scale);
        }
    }
    
    public static void encode(HealthBarScalePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.isReset());
        if (!packet.isReset()) {
            buf.writeFloat(packet.getScale());
        }
    }
    
    public static void handle(HealthBarScalePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                // 在客户端处理
                if (packet.isReset()) {
                    SimpleHealthBarRenderer.setGlobalScale(1.0f);
                } else {
                    SimpleHealthBarRenderer.setGlobalScale(packet.getScale());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    /**
     * 发送缩放设置到客户端
     */
    public static void sendToClient(ServerPlayer player, float scale) {
        HealthBarScalePacket packet = new HealthBarScalePacket(scale);
        NetworkManager.getChannel().sendTo(packet, player.connection.connection, 
            net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }
    
    /**
     * 发送重置命令到客户端
     */
    public static void sendResetToClient(ServerPlayer player) {
        HealthBarScalePacket packet = new HealthBarScalePacket();
        NetworkManager.getChannel().sendTo(packet, player.connection.connection, 
            net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }
    
    /**
     * 发送缩放设置到所有客户端
     */
    public static void sendToAllClients(float scale) {
        HealthBarScalePacket packet = new HealthBarScalePacket(scale);
        NetworkManager.getChannel().send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 发送重置命令到所有客户端
     */
    public static void sendResetToAllClients() {
        HealthBarScalePacket packet = new HealthBarScalePacket();
        NetworkManager.getChannel().send(PacketDistributor.ALL.noArg(), packet);
    }
}