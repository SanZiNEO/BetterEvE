package com.example.bettereve.mixin;

import com.example.bettereve.render.SimpleHealthBarRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityRendererMixin - 简单血条显示Mixin
 * 通过注入EntityRenderer.render方法，在渲染实体名称标签时显示简单血条
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    
    /**
     * 注入到EntityRenderer.render方法中，在方法结束前渲染血条
     * @param entity 要渲染的实体
     * @param $$1 旋转角度
     * @param $$2 部分tick
     * @param poseStack 姿势堆栈
     * @param bufferSource 缓冲区源
     * @param $$5 光照等级
     * @param ci 回调信息
     */
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"),
            cancellable = false)
    private void bettereve_renderSimpleHealthBarAtEnd(Entity entity, float $$1, float $$2, PoseStack poseStack,
                                                 MultiBufferSource bufferSource, int $$5, CallbackInfo ci) {
        // 1. 检查是否应该显示简单血条
        if (!SimpleHealthBarRenderer.shouldShowHealthBar(entity)) {
            return;
        }
        
        // 2. 计算实体相对于相机的坐标（与LevelRendererMixin相同）
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        double d0 = Mth.lerp($$2, entity.xOld, entity.getX());
        double d1 = Mth.lerp($$2, entity.yOld, entity.getY());
        double d2 = Mth.lerp($$2, entity.zOld, entity.getZ());
        
        double dx = d0 - cameraPos.x;
        double dy = d1 - cameraPos.y;
        double dz = d2 - cameraPos.z;
        
        // 3. 渲染简单血条
        SimpleHealthBarRenderer.renderHealthBar(entity, poseStack, bufferSource, $$2, dx, dy, dz);
    }
}
