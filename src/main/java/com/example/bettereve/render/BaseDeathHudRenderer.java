package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.base.BaseDeathCounter;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.util.Map;

/**
 * 自定义计分板 HUD — 替代原版 MC Scoreboard。
 * 显示每个阵营的基地摧毁数和实体击杀数。
 * 大小固定宽度，内容自适应行高。
 * 数据源：BaseDeathCounter（客户端缓存）
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BaseDeathHudRenderer {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/ScoreboardHUD");
    
    // HUD 布局配置
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_MAX_HEIGHT = 300;
    private static final int TITLE_COLOR = 0xFFFFAA;
    private static final int HEADER_COLOR = 0xAAAAAA;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int LINE_HEIGHT = 20;
    private static final int PADDING = 4;
    private static final int LEFT_MARGIN = 8;
    private static final int RIGHT_MARGIN = 8;
    
    public static final IGuiOverlay BASE_DEATH_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        Font font = mc.font;
        
        // 获取阵营数据
        var deathCounts = BaseDeathCounter.getAllFactionDeathCounts();
        var factions = FactionRegistry.getAllFactions();
        if (factions.isEmpty()) return;
        
        // 计算面板高度
        int rowCount = (int) factions.stream().filter(Faction::isEnabled).count();
        int panelHeight = Math.min(40 + rowCount * LINE_HEIGHT, PANEL_MAX_HEIGHT);
        
        // 面板位置：屏幕顶部居中
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = PADDING;
        
        // 绘制背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BACKGROUND_COLOR);
        
        // 绘制标题
        int titleY = panelY + 4;
        guiGraphics.drawString(font, Component.literal("=== 阵营计分板 ==="), 
            panelX + LEFT_MARGIN, titleY, TITLE_COLOR, false);
        
        // 绘制表头
        int headerY = titleY + LINE_HEIGHT - 4;
        guiGraphics.drawString(font, Component.literal("阵营"), 
            panelX + LEFT_MARGIN, headerY, HEADER_COLOR, false);
        guiGraphics.drawString(font, Component.literal("基地摧毁"), 
            panelX + 120, headerY, HEADER_COLOR, false);
        guiGraphics.drawString(font, Component.literal("实体击杀"), 
            panelX + 220, headerY, HEADER_COLOR, false);
        
        // 绘制每个阵营的数据行
        int rowY = headerY + LINE_HEIGHT - 4;
        for (Faction faction : factions) {
            if (!faction.isEnabled()) continue;
            
            String factionId = faction.getId();
            int baseDeaths = deathCounts.getOrDefault(factionId, 0);
            int entityDeaths = 0; // 暂未同步实体死亡数
            
            int factionColor = faction.getColorInt();
            
            // 阵营名称（带颜色）
            guiGraphics.drawString(font, Component.literal(faction.getName()), 
                panelX + LEFT_MARGIN + 4, rowY, factionColor, false);
            
            // 基地摧毁数（白色）
            guiGraphics.drawString(font, Component.literal(String.valueOf(baseDeaths)), 
                panelX + 120, rowY, TEXT_COLOR, false);
            
            // 实体击杀数（白色，暂为 0）
            guiGraphics.drawString(font, Component.literal(String.valueOf(entityDeaths)), 
                panelX + 220, rowY, TEXT_COLOR, false);
            
            rowY += LINE_HEIGHT;
        }
    };
    
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_scoreboard", BASE_DEATH_OVERLAY);
        LOGGER.info("Registered custom scoreboard HUD overlay");
    }
}