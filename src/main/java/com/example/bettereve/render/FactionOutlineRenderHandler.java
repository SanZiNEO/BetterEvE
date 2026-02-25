package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OutlineBufferSource;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FactionOutlineRenderHandler {

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<LivingEntity, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity = event.getEntity();
        Player player = mc.player;

        // ========== 强制开启实体轮廓（无视游戏内开关） ==========
        // 在 Minecraft 1.20.1 版本中，我们通过直接控制 OutlineBufferSource 来实现强制渲染
        // 而不是直接修改游戏选项，这样可以避免干扰原版行为

        // 1. 仅处理有阵营的实体（非中立）
        String factionId = FactionHelper.getEntityFactionId(entity);
        if ("neutral".equals(factionId)) {
            return;
        }

        // 2. 检查配置开关
        if (!BetterEvEConfig.INSTANCE.global_outlineEnabled.get()) {
            return;
        }

        // 3. 获取阵营信息
        Faction faction = FactionHelper.getEntityFaction(entity);
        if (faction == null) {
            return;
        }

        // 4. 原版核心：获取穿墙描边的缓冲源
        OutlineBufferSource outlineBuffer = (OutlineBufferSource) event.getMultiBufferSource();
        
        // 5. 设置描边颜色
        outlineBuffer.setColor(faction.getRed(), faction.getGreen(), faction.getBlue(), 255);
        
        // 6. 重新渲染实体轮廓（穿墙高亮）
        event.getRenderer().render(
            entity, entity.getYRot(), event.getPartialTick(),
            event.getPoseStack(), outlineBuffer, event.getPackedLight()
        );
        
        // 7. 重置颜色为默认
        outlineBuffer.setColor(255, 255, 255, 255);
    }
}