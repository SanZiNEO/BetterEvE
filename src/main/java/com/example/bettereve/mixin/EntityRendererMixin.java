package com.example.bettereve.mixin;

import com.example.bettereve.render.MinimalHealthBarRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityRendererMixin - 极简血条显示Mixin
 * 通过注入EntityRenderer.renderNameTag方法，在渲染实体名称标签时显示极简血条
 * 同时取消原版名称标签的显示
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    
    /**
     * 注入到renderNameTag方法的头部，用于显示极简血条
     * @param entity 要渲染的实体
     * @param displayName 实体的显示名称
     * @param poseStack 姿势堆栈
     * @param bufferSource 缓冲区源
     * @param packedLight 光照等级
     * @param ci 回调信息
     */
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void bettereve_renderMinimalHealthBar(Entity entity, Component displayName, PoseStack poseStack, 
                                                  MultiBufferSource bufferSource, int packedLight, 
                                                  CallbackInfo ci) {
        // 1. 检查是否应该显示极简血条
        if (!MinimalHealthBarRenderer.shouldShowHealthBar(entity)) {
            return;
        }
        
        // 2. 取消原版名称标签的渲染
        ci.cancel();
        
        // 3. 渲染极简血条（包含阵营颜色、等级和血量信息）
        // 注意：在1.20.1中，renderNameTag方法没有partialTick参数
        // 我们使用0.0f作为默认值，或者从Minecraft实例获取当前partialTick
        float partialTick = 0.0f; // 简化处理
        MinimalHealthBarRenderer.renderHealthBar(entity, poseStack, bufferSource, partialTick);
    }
}