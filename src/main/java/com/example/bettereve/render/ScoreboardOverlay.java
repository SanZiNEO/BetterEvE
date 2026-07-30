package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.crystal.CrystalDeathCounter;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/**
 * 计分板 HUD — 右上角双行显示。
 * 第一行：队名 + 得分（基地摧毁数）
 * 第二行：击杀（击杀非本队实体数）
 * 无背景、无剧本时不显示。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ScoreboardOverlay {
    private static final int MARGIN_RIGHT = 4;
    private static final int MARGIN_TOP = 4;
    private static final int LINE_GAP = 10;

    public static final IGuiOverlay CRYSTAL_DEATH_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        if (ScenarioManager.getActiveScenario() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var deathCounts = CrystalDeathCounter.getAllFactionDeathCounts();
        var killCounts = CrystalDeathCounter.getAllEntityKillCounts();
        var factions = FactionRegistry.getAllFactions().stream()
                .filter(Faction::isEnabled).toList();
        if (factions.isEmpty()) return;

        // 第一行：队名 + 得分
        // 格式：红队  3 : 1  蓝队
        StringBuilder line1 = new StringBuilder();
        boolean first = true;
        for (Faction f : factions) {
            String name = f.getName();
            if (name.length() > 4) name = name.substring(0, 4);
            int score = deathCounts.getOrDefault(f.getId(), 0);
            if (!first) line1.append("  ");
            first = false;
            line1.append(name).append(" ").append(score);
        }

        // 第二行：击杀
        // 格式：击杀 15    击杀 10
        StringBuilder line2 = new StringBuilder();
        first = true;
        for (Faction f : factions) {
            int kills = killCounts.getOrDefault(f.getId(), 0);
            if (!first) line2.append("    ");
            first = false;
            line2.append("\u51fb\u6740 ").append(kills); // "击杀 "
        }

        String l1 = line1.toString();
        String l2 = line2.toString();
        if (l1.isEmpty()) return;

        int x = width - mc.font.width(l1) - MARGIN_RIGHT;
        int y = MARGIN_TOP;

        guiGraphics.drawString(mc.font, Component.literal(l1), x, y, 0xFFFFFF, false);
        guiGraphics.drawString(mc.font, Component.literal(l2), x, y + LINE_GAP, 0xAAAAAA, false);
    };

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_scoreboard", CRYSTAL_DEATH_OVERLAY);
    }
}
