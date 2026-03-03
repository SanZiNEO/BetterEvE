package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/**
 * HUD 渲染事件处理器
 * 使用 Forge 的 IGuiOverlay 接口渲染血条
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HudRenderHandler {
    
    public static final IGuiOverlay HEALTH_BAR_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查是否在游戏中且有世界
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        
        // 遍历所有可见的实体并渲染血条
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                SimpleHealthBarRenderer.renderHealthBar(entity, poseStack, bufferSource, partialTick);
            }
        }
    };
    
    /**
     * 注册 HUD 渲染覆盖层
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "bettereve_health_bar", HEALTH_BAR_OVERLAY);
    }
}
