package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityRenderHandler {
    private static final Logger logger = LogManager.getLogger(EntityRenderHandler.class);

    @SubscribeEvent
    public void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            String factionId = FactionHelper.getEntityFactionId(livingEntity);
            if (factionId != null) {
                Faction faction = FactionRegistry.getFaction(factionId);
                if (faction != null) {
                    renderCustomNameTag(livingEntity, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
                }
            }
        }
    }

    private void renderCustomNameTag(LivingEntity entity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive() || entity.isInvisible() || !entity.shouldShowName()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (font == null) {
            return;
        }

        double distance = minecraft.player.distanceTo(entity);
        if (distance > 64.0) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.5, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        String factionId = FactionHelper.getEntityFactionId(entity);
        Faction faction = FactionRegistry.getFaction(Objects.requireNonNull(factionId));
        String name = faction.getName() + " - " + entity.getDisplayName().getString();

        float textWidth = font.width(name);
        float x = -textWidth / 2.0F;
        float y = 0.0F;
        float backgroundOpacity = 0.25F;

        // 渲染背景
        int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;
        font.drawInBatch(name, x, y, faction.getColorInt(), false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, backgroundColor, packedLight);

        poseStack.popPose();
    }
}