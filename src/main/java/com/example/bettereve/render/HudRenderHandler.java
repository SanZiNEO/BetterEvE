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

import java.util.ArrayList;
import java.util.List;

/**
 * HUD 渲染事件处理器
 * 使用 Forge 的 IGuiOverlay 接口渲染血条
 * 实现预渲染+渲染队列机制
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HudRenderHandler {
    
    private static final List<HealthBarRenderData> renderQueue = new ArrayList<>();
    
    public static final IGuiOverlay HEALTH_BAR_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查是否在游戏中且有世界
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        
        // 预处理阶段：预计算所有可见实体的渲染数据
        preRenderEntities(mc, partialTick);
        
        // 正式渲染阶段：从队列取出数据进行渲染
        renderFromQueue(poseStack, bufferSource, partialTick);
    };
    
    /**
     * 预处理阶段：遍历所有可见实体，预计算渲染数据
     */
    private static void preRenderEntities(Minecraft mc, float partialTick) {
        renderQueue.clear();
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != mc.player && SimpleHealthBarRenderer.shouldShowHealthBar(entity)) {
                HealthBarRenderData renderData = SimpleHealthBarRenderer.preRenderHealthBar(entity, partialTick);
                if (renderData != null) {
                    renderQueue.add(renderData);
                }
            }
        }
    }
    
    /**
     * 正式渲染阶段：从队列中取出预计算的渲染数据进行渲染
     */
    private static void renderFromQueue(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        for (HealthBarRenderData renderData : renderQueue) {
            SimpleHealthBarRenderer.renderFromPreRendered(renderData, poseStack, bufferSource);
        }
    }
    
    /**
     * 注册 HUD 渲染覆盖层
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "bettereve_health_bar", HEALTH_BAR_OVERLAY);
    }
}
