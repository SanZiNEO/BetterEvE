package com.example.bettereve.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.bettereve.render.SimpleHealthBarRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

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
}
