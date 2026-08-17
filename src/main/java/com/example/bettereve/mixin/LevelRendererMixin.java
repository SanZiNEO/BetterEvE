package com.example.bettereve.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
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
     * 实体渲染完成后，在世界空间绘制实体化血条（参考 Neat）。
     * 这样血条走实体/世界渲染管线，Oculus/Iris 光影下也能正常渲染。
     */
    @Inject(
        method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void onRenderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        SimpleHealthBarRenderer.renderHealthBarWorld(entity, poseStack, buffers, entityRenderDispatcher.camera, partialTick, camX, camY, camZ);
    }
}
