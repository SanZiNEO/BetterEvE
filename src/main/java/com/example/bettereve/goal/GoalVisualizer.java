package com.example.bettereve.goal;

import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "bettereve", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GoalVisualizer {
    private static boolean enabled = false;

    public static void setEnabled(boolean enable) { enabled = enable; }
    public static boolean isEnabled() { return enabled; }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_PARTICLES) return;
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 playerPos = mc.player.position();

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (GoalRegion region : GoalManager.getAllGoals()) {
            double cx = region.getCenterX();
            double cz = region.getCenterZ();
            if (playerPos.distanceToSqr(new Vec3(cx, playerPos.y, cz)) > 64 * 64) continue;

            Faction faction = FactionRegistry.getFaction(region.getFactionId());
            int color = faction != null ? faction.getColorInt() : 0xFFFFFF;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            renderCornerColumn(poseStack, consumer, region.getX1(), region.getZ1(), r, g, b);
            renderCornerColumn(poseStack, consumer, region.getX2(), region.getZ1(), r, g, b);
            renderCornerColumn(poseStack, consumer, region.getX1(), region.getZ2(), r, g, b);
            renderCornerColumn(poseStack, consumer, region.getX2(), region.getZ2(), r, g, b);
        }

        poseStack.popPose();
    }

    private static void renderCornerColumn(PoseStack pose, VertexConsumer buffer, double x, double z, float r, float g, float b) {
        AABB box = new AABB(x - 0.25, -64.0, z - 0.25, x + 0.25, 320.0, z + 0.25);
        LevelRenderer.renderLineBox(pose, buffer, box, r, g, b, 1.0f);
    }
}
