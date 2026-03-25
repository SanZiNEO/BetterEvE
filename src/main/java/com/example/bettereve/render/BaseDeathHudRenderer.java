package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.DebugConfig;
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
 * 基地死亡HUD渲染器
 * 在屏幕顶部显示基地被毁数计分板
 * 横向布局：标题居中，下面一行显示各阵营计数
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BaseDeathHudRenderer {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/BaseDeathHudRenderer");
    
    // HUD配置
    private static final String TITLE = "基地被毁数";
    private static final int TITLE_COLOR = 0xFFFFFF; // 白色
    private static final int TEXT_COLOR = 0xFFFFFF;  // 白色
    private static final int BACKGROUND_COLOR = 0x80000000; // 半透明黑色背景
    private static final int PADDING = 3;
    private static final int SPACING = 10; // 阵营之间的间距
    private static final int VERTICAL_SPACING = 2; // 标题和内容之间的垂直间距
    
    public static final IGuiOverlay BASE_DEATH_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查是否在游戏中且有世界
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        Font font = mc.font;
        PoseStack poseStack = guiGraphics.pose();
        
        // 获取所有阵营的死亡计数
        Map<String, Integer> deathCounts = BaseDeathCounter.getAllFactionDeathCounts();
        
        // 如果没有死亡计数，不显示HUD
        if (deathCounts.isEmpty()) {
            return;
        }
        
        // 计算总内容宽度（阵营名称: 数字）
        int totalWidth = 0;
        for (Map.Entry<String, Integer> entry : deathCounts.entrySet()) {
            Faction faction = FactionRegistry.getFaction(entry.getKey());
            String displayName = faction != null ? faction.getName() : entry.getKey();
            int count = entry.getValue();
            // 分开计算阵营名称部分（带冒号）和数字部分的宽度
            String namePart = displayName + ": ";
            String countPart = String.valueOf(count);
            totalWidth += font.width(namePart) + font.width(countPart) + SPACING;
        }
        
        // 减去最后一个间距
        if (!deathCounts.isEmpty()) {
            totalWidth -= SPACING;
        }
        
        // 计算标题宽度
        int titleWidth = font.width(TITLE);
        
        // 使用总宽度或标题宽度中较大的一个作为背景宽度
        int backgroundWidth = Math.max(totalWidth, titleWidth) + PADDING * 2;
        
        // 计算背景高度（标题 + 内容 + 间距 + 边距）
        int backgroundHeight = font.lineHeight * 2 + VERTICAL_SPACING + PADDING * 2;
        
        // 计算背景位置（顶部居中）
        int backgroundX = (width - backgroundWidth) / 2;
        int backgroundY = PADDING;
        
        // 绘制半透明黑色背景
        guiGraphics.fill(backgroundX, backgroundY, 
                        backgroundX + backgroundWidth, 
                        backgroundY + backgroundHeight, 
                        BACKGROUND_COLOR);
        
        // 绘制标题（居中）
        int titleX = (width - titleWidth) / 2;
        int titleY = backgroundY + PADDING;
        guiGraphics.drawString(font, Component.literal(TITLE), titleX, titleY, TITLE_COLOR, false);
        
        // 绘制阵营死亡计数（横向排列）
        int contentY = titleY + font.lineHeight + VERTICAL_SPACING;
        int contentX = (width - totalWidth) / 2;
        
        for (Map.Entry<String, Integer> entry : deathCounts.entrySet()) {
            Faction faction = FactionRegistry.getFaction(entry.getKey());
            String displayName = faction != null ? faction.getName() : entry.getKey();
            int count = entry.getValue();
            
            // 分开渲染阵营名称部分（带冒号）和数字部分
            String namePart = displayName + ": ";
            String countPart = String.valueOf(count);
            
            // 获取阵营颜色（如果可用），否则使用默认颜色
            int factionColor = TEXT_COLOR;
            if (faction != null) {
                try {
                    // 使用Faction类的getColorInt()方法获取颜色整数
                    factionColor = faction.getColorInt();
                } catch (Exception e) {
                    // 如果颜色解析失败，使用默认颜色
                    if (DebugConfig.isDebugEnabled()) {
                        LOGGER.debug("Failed to parse color for faction {}: {}", entry.getKey(), faction.getColor());
                    }
                }
            }
            
            // 数字颜色固定为白色
            int countColor = 0xFFFFFF;
            
            // 渲染阵营名称部分（带冒号）
            guiGraphics.drawString(font, Component.literal(namePart), contentX, contentY, factionColor, false);
            contentX += font.width(namePart);
            
            // 渲染数字部分
            guiGraphics.drawString(font, Component.literal(countPart), contentX, contentY, countColor, false);
            contentX += font.width(countPart) + SPACING;
        }
        
        if (DebugConfig.isTraceEnabled()) {
            LOGGER.trace("Rendered base death HUD with {} factions", deathCounts.size());
        }
    };
    
    /**
     * 注册HUD渲染覆盖层
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // 在十字准心下方注册，确保始终可见
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_base_death", BASE_DEATH_OVERLAY);
        LOGGER.info("Registered base death HUD overlay");
    }
}