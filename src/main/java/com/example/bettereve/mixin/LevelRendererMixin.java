package com.example.bettereve.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.bettereve.render.SimpleHealthBarRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    /**
     * 每帧开始时清理渲染记录
     */
    @Inject(
        method = "renderLevel",
        at = @At("HEAD")
    )
    private void onRenderLevelStart(PoseStack poseStack, float partialTick, long finishTimeNano, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        SimpleHealthBarRenderer.onFrameStart();
    }

    /**
     * 注入到 LevelRenderer.renderEntity 方法中，在 EntityRenderDispatcher.render 之后渲染血条
     * 参考 Neat 模组的实现方式
     */
    @Inject(
        method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void renderHealthBar(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        double d0 = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double d1 = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double d2 = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        SimpleHealthBarRenderer.renderHealthBar(entity, poseStack, buffers, partialTick, 
                                               d0 - camX, d1 - camY, d2 - camZ);
    }
}
