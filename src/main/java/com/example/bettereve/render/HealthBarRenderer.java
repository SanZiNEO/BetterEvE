package com.example.bettereve.render;

import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class HealthBarRenderer {

    private static float getBarWidth() {
        return BetterEvEConfig.INSTANCE.render_healthBarWidth.get().floatValue();
    }

    private static float getBarHeight() {
        return BetterEvEConfig.INSTANCE.render_healthBarHeight.get().floatValue();
    }

    private static float getBarOffsetY() {
        return BetterEvEConfig.INSTANCE.render_healthBarOffsetY.get().floatValue();
    }

    private static final float TEXT_HEIGHT = 8;
    private static final float SCALE = 0.0267F;

    private static boolean shouldShowHealthBar(LivingEntity living, Entity cameraEntity) {
        if (living == cameraEntity) {
            return false;
        }

        if (!BetterEvEConfig.INSTANCE.render_showHealthBar.get()) {
            return false;
        }

        if (!living.isAlive()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        double distance = mc.player != null ? mc.player.distanceTo(living) : 0;
        if (distance > 64.0) {
            return false;
        }

        return true;
    }

    public static void renderHealthBar(Entity entity, PoseStack poseStack, MultiBufferSource buffers,
                                       Camera camera, EntityRenderer<? super Entity> entityRenderer,
                                       float partialTicks, double x, double y, double z) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        if (!shouldShowHealthBar(living, camera.getEntity())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        float healthPercent = health / maxHealth;
        String percentText = String.format("%.0f%%", healthPercent * 100);

        int barColor = 0xFFFFFF;
        String factionId = FactionHelper.getEntityFactionId(living);
        if (factionId != null) {
            Faction faction = FactionRegistry.getFaction(factionId);
            if (faction != null) {
                barColor = faction.getColorInt();
            }
        }

        poseStack.pushPose();

        var renderOffset = entityRenderer.getRenderOffset(entity, partialTicks);
        poseStack.translate(x + renderOffset.x(), y + renderOffset.y(), z + renderOffset.z());

        poseStack.translate(0, entity.getBbHeight() + getBarOffsetY(), 0);

        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(-SCALE, -SCALE, SCALE);

        float centerX = 0;
        float centerY = 0;
        renderHealthBarBackground(poseStack, buffers, centerX, centerY);
        renderHealthBarFill(poseStack, buffers, centerX, centerY, healthPercent, barColor);
        renderHealthPercentText(poseStack, buffers, centerX, centerY, percentText);

        poseStack.popPose();
    }

    private static void renderHealthBarBackground(PoseStack poseStack, MultiBufferSource buffers, float x, float y) {
        float left = x - getBarWidth() / 2;
        float right = x + getBarWidth() / 2;
        float top = y;
        float bottom = y + getBarHeight();

        VertexConsumer builder = buffers.getBuffer(RenderType.gui());
        int light = 0xF000F0;

        builder.vertex(poseStack.last().pose(), left, top, 0)
                .color(0, 0, 0, 128)
                .uv(0, 0)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), right, top, 0)
                .color(0, 0, 0, 128)
                .uv(1, 0)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), right, bottom, 0)
                .color(0, 0, 0, 128)
                .uv(1, 1)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), left, bottom, 0)
                .color(0, 0, 0, 128)
                .uv(0, 1)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();
    }

    private static void renderHealthBarFill(PoseStack poseStack, MultiBufferSource buffers, float x, float y, float percent, int color) {
        float left = x - getBarWidth() / 2;
        float right = left + getBarWidth() * percent;
        float top = y;
        float bottom = y + getBarHeight();

        VertexConsumer builder = buffers.getBuffer(RenderType.gui());
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int light = 0xF000F0;

        builder.vertex(poseStack.last().pose(), left, top, 0)
                .color(r, g, b, 255)
                .uv(0, 0)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), right, top, 0)
                .color(r, g, b, 255)
                .uv(1, 0)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), right, bottom, 0)
                .color(r, g, b, 255)
                .uv(1, 1)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();

        builder.vertex(poseStack.last().pose(), left, bottom, 0)
                .color(r, g, b, 255)
                .uv(0, 1)
                .uv2(light)
                .normal(poseStack.last().normal(), 0, 1, 0)
                .endVertex();
    }

    private static void renderHealthPercentText(PoseStack poseStack, MultiBufferSource buffers, float x, float y, String text) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        poseStack.pushPose();
        poseStack.translate(x, y - TEXT_HEIGHT - 2, 0);
        poseStack.scale(0.8F, 0.8F, 0.8F);

        float textWidth = font.width(text);
        float textX = -textWidth / 2;
        float textY = 0;

        int textColor = 0xFFFFFF;
        int backgroundColor = 0x80000000;
        int light = 0xF000F0;

        font.drawInBatch(text, textX, textY, textColor, false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, backgroundColor, light);
        poseStack.popPose();
    }
}