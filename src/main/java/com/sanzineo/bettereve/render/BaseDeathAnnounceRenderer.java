package com.sanzineo.bettereve.render;

import com.sanzineo.bettereve.Bettereve;
import com.sanzineo.bettereve.faction.Faction;
import com.sanzineo.bettereve.faction.FactionRegistry;
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
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基地死亡中央文字播报渲染器
 * 在屏幕正中央显示基地死亡提示文字，带倒计时功能
 * 
 * 配置说明：
 * 1. ANNOUNCE_TOTAL_DURATION_MS - 总显示时间（毫秒），默认3000毫秒（3秒）
 * 2. TEXT_SCALE - 文字缩放系数，默认1.0f，可在此统一调整两行文字大小
 * 3. 倒计时每秒更新一次，从3秒开始显示
 * 
 * 文字格式：
 * 第一行：{阵营}基地被摧毁！
 * 第二行：{数字倒计时}秒后进入下一轮
 * 
 * 重要：新的播报会直接覆盖旧的播报，确保新播报立即显示
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BaseDeathAnnounceRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/BaseDeathAnnounceRenderer");
    
    // ========== 可配置参数 ==========
    
    /**
     * 播报总显示时间（毫秒）
     * 修改此值可调整倒计时总时长，例如：5000 = 5秒，10000 = 10秒
     */
    private static final int ANNOUNCE_TOTAL_DURATION_MS = 10000; // 3秒总时长
    
    /**
     * 文字缩放系数
     * 修改此值可统一调整两行文字的大小，例如：1.2f = 增大20%，0.8f = 缩小20%
     */
    private static final float TEXT_SCALE = 3.0f;
    
    /**
     * 行间距（像素）
     * 修改此值可调整两行文字之间的垂直间距
     */
    private static final int LINE_SPACING = 4;
    
    /**
     * 文字阴影效果
     * true = 启用文字阴影（默认），false = 禁用文字阴影
     */
    private static final boolean TEXT_SHADOW = true;
    
    // ========== 内部数据结构 ==========
    
    /**
     * 单个死亡播报数据
     */
    private static class DeathAnnounce {
        private final String factionId;        // 阵营ID
        private final String factionName;      // 阵营显示名称
        private final int color;               // 阵营颜色（十六进制）
        private final long startTime;          // 开始时间（毫秒）
        private final int totalDuration;       // 总持续时间（毫秒）
        private int remainSeconds;             // 剩余秒数（用于倒计时显示）
        
        public DeathAnnounce(String factionId, String factionName, int color, long startTime) {
            this.factionId = factionId;
            this.factionName = factionName;
            this.color = color;
            this.startTime = startTime;
            this.totalDuration = ANNOUNCE_TOTAL_DURATION_MS;
            this.remainSeconds = ANNOUNCE_TOTAL_DURATION_MS / 1000; // 初始为总秒数
        }
        
        /**
         * 更新剩余秒数
         * @param currentTime 当前时间（毫秒）
         * @return true=播报仍然有效，false=播报已结束
         */
        public boolean update(long currentTime) {
            long elapsed = currentTime - startTime;
            if (elapsed >= totalDuration) {
                return false; // 播报已结束
            }
            
            // 计算剩余秒数（向上取整，确保3秒倒计时正确显示）
            int newRemainSeconds = (int) Math.ceil((totalDuration - elapsed) / 1000.0);
            if (newRemainSeconds != remainSeconds) {
                remainSeconds = newRemainSeconds;
                LOGGER.trace("Updated remain seconds for faction {}: {}s", factionId, remainSeconds);
            }
            return true;
        }
        
        /**
         * 获取第一行文字
         */
        public String getFirstLine() {
            return factionName + "基地被摧毁！";
        }
        
        /**
         * 获取第二行文字（带倒计时）
         */
        public String getSecondLine() {
            return remainSeconds + "秒后进入下一轮";
        }
        
        public int getColor() {
            return color;
        }
    }
    
    // 使用CopyOnWriteArrayList保证线程安全（渲染线程和网络线程可能同时访问）
    private static final CopyOnWriteArrayList<DeathAnnounce> activeAnnounces = new CopyOnWriteArrayList<>();
    
    /**
     * 添加新的死亡播报
     * 重要：新的播报会直接清除所有旧的播报，确保新播报立即显示
     * @param factionId 阵营ID
     */
    public static void addAnnounce(String factionId) {
        Faction faction = FactionRegistry.getFaction(factionId);
        String factionName = faction != null ? faction.getName() : factionId;
        
        // 获取阵营颜色，如果解析失败则使用默认白色
        int color = 0xFFFFFF; // 默认白色
        if (faction != null) {
            try {
                String hexColor = faction.getColor();
                if (hexColor != null && hexColor.startsWith("#")) {
                    color = Color.decode(hexColor).getRGB();
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse color for faction {}: {}", factionId, faction != null ? faction.getColor() : "null");
            }
        }
        
        long currentTime = System.currentTimeMillis();
        DeathAnnounce announce = new DeathAnnounce(factionId, factionName, color, currentTime);
        
        // 重要：清除所有旧的播报，确保新播报立即显示
        // 这是解决"下一个播报不显示"问题的关键修改
        activeAnnounces.clear();
        activeAnnounces.add(announce);
        LOGGER.debug("Added death announce for faction: {} (color: #{}) - cleared old announces", factionName, Integer.toHexString(color));
    }
    
    /**
     * 清除所有播报
     */
    public static void clearAll() {
        activeAnnounces.clear();
        LOGGER.debug("Cleared all death announces");
    }
    
    /**
     * 清除指定阵营的播报
     * @param factionId 阵营ID
     */
    public static void clearFaction(String factionId) {
        activeAnnounces.removeIf(announce -> announce.factionId.equals(factionId));
        LOGGER.debug("Cleared death announces for faction: {}", factionId);
    }
    
    /**
     * HUD渲染覆盖层
     */
    public static final IGuiOverlay BASE_DEATH_ANNOUNCE_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查是否在游戏中且有世界
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // 如果没有活动播报，直接返回
        if (activeAnnounces.isEmpty()) {
            return;
        }
        
        Font font = mc.font;
        PoseStack poseStack = guiGraphics.pose();
        long currentTime = System.currentTimeMillis();
        
        // 更新所有播报并移除已结束的
        Iterator<DeathAnnounce> iterator = activeAnnounces.iterator();
        while (iterator.hasNext()) {
            DeathAnnounce announce = iterator.next();
            if (!announce.update(currentTime)) {
                iterator.remove();
                LOGGER.trace("Removed expired announce for faction: {}", announce.factionId);
            }
        }
        
        // 如果没有活动播报了，直接返回
        if (activeAnnounces.isEmpty()) {
            return;
        }
        
        // 只显示最新的一个播报（避免屏幕拥挤）
        DeathAnnounce latestAnnounce = activeAnnounces.get(activeAnnounces.size() - 1);
        
        // 获取两行文字
        String firstLine = latestAnnounce.getFirstLine();
        String secondLine = latestAnnounce.getSecondLine();
        int color = latestAnnounce.getColor();
        
        // 保存当前变换状态
        poseStack.pushPose();
        
        // 应用缩放变换（屏幕中央缩放）
        // 重要：将坐标原点平移到屏幕中心，然后应用缩放
        // 保持原点在屏幕中心，这样位置计算更直观
        poseStack.translate(width / 2.0f, height / 2.0f, 0);
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        // 注意：不再平移回去！保持原点在屏幕中心
        
        // 计算原始文字尺寸（不应用缩放）
        int firstLineWidth = font.width(firstLine);
        int secondLineWidth = font.width(secondLine);
        int lineHeight = font.lineHeight;
        
        // 计算渲染位置（在以屏幕中心为原点的缩放后坐标系统中）
        // 原点在屏幕中心：(0,0)就是屏幕中心点
        // 文字以原点为中心对称显示
        
        // 第一行位置（在中心线上方）
        // x坐标：文字向左偏移一半宽度，使其居中
        // y坐标：向上偏移（行高 + 行间距/2）
        int firstLineX = -firstLineWidth / 2;
        int firstLineY = -lineHeight - LINE_SPACING / 2;
        
        // 第二行位置（在中心线下方）
        // x坐标：文字向左偏移一半宽度，使其居中
        // y坐标：向下偏移（行间距/2）
        int secondLineX = -secondLineWidth / 2;
        int secondLineY = LINE_SPACING / 2;
        
        // 渲染第一行文字
        // 现在的位置是在以屏幕中心为原点的坐标系统中
        guiGraphics.drawString(font, Component.literal(firstLine), firstLineX, firstLineY, color, TEXT_SHADOW);
        
        // 渲染第二行文字（倒计时）
        guiGraphics.drawString(font, Component.literal(secondLine), secondLineX, secondLineY, color, TEXT_SHADOW);
        
        // 恢复变换状态
        poseStack.popPose();
        LOGGER.trace("Rendered death announce for faction: {} (remain: {}s)", latestAnnounce.factionName, latestAnnounce.remainSeconds);
    };
    
    /**
     * 注册HUD渲染覆盖层
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // 在十字准心下方注册，但比基地死亡计分板更低（确保中央显示不被遮挡）
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_crystal_death_announce", BASE_DEATH_ANNOUNCE_OVERLAY);
        LOGGER.info("Registered crystal death announce HUD overlay (total duration: {}ms, text scale: {})", 
                   ANNOUNCE_TOTAL_DURATION_MS, TEXT_SCALE);
    }
    
    /**
     * 获取活动播报数量（用于调试）
     */
    public static int getActiveAnnounceCount() {
        return activeAnnounces.size();
    }
    
    /**
     * 获取配置信息（用于调试）
     */
    public static String getConfigInfo() {
        return String.format("CrystalDeathAnnounceRenderer Config: totalDuration=%dms, textScale=%.2f, lineSpacing=%d, shadow=%s",
                           ANNOUNCE_TOTAL_DURATION_MS, TEXT_SCALE, LINE_SPACING, TEXT_SHADOW);
    }
}