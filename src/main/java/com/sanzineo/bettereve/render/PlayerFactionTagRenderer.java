package com.sanzineo.bettereve.render;

import com.sanzineo.bettereve.faction.Faction;
import com.sanzineo.bettereve.faction.FactionRegistry;
import com.sanzineo.bettereve.faction.PlayerFactionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家头顶阵营颜色标记渲染器。
 * 在玩家名称标签旁显示阵营颜色圆点和名称。
 */
@Mod.EventBusSubscriber(modid = "bettereve", value = Dist.CLIENT)
public class PlayerFactionTagRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        String factionId = PlayerFactionData.getPlayerFaction(player);
        Faction faction = FactionRegistry.getFaction(factionId);
        if (faction == null) {
            return; // 无阵营不显示标签
        }
        
        // 在名字前添加阵营颜色标记
        String tag = "[" + faction.getName() + "] ";
        Component coloredTag = Component.literal(tag).withStyle(
                style -> style.withColor(faction.getColorInt())
        );
        event.setContent(coloredTag.copy().append(event.getContent()));
    }
}
