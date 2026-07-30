package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.crystal.CrystalDeathCounter;
import com.example.bettereve.scenario.ScenarioManager;
import com.example.bettereve.scenario.Scenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 计分板 HUD — 顶部居中图形化面板。
 * 显示各阵营的名称、死亡分数（主条区域）和击败数（下方击杀条区域）。
 * 风格参考暗色科技感设计，带四角色角标和阵营色装饰。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ScoreboardOverlay {

    // ==================== 布局常量 ====================
    private static final int PANEL_TOP = 4;
    private static final int PANEL_PADDING_H = 8;
    private static final int MAIN_HEIGHT = 44;
    private static final int KILL_HEIGHT = 22;
    private static final int TOTAL_HEIGHT = MAIN_HEIGHT + KILL_HEIGHT; // 66
    private static final int CORNER_EXTENT = 5;

    // ==================== 颜色常量 ====================
    private static final int BG_MAIN = 0xCC0C0C1A;
    private static final int BG_KILL = 0xCC080810;
    private static final int CORNER_GOLD = 0xFFFACC15;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int SEPARATOR_ALPHA = 0x0FFFFFFF; // rgba(255,255,255,0.06)
    private static final int ACCENT_LINE_ALPHA = 0x1AFFFFFF; // rgba(255,255,255,0.10)
    private static final int CENTER_DOT_COLOR = 0x60FACC15; // gold with 0.38 alpha

    // ==================== 颜色辅助方法 ====================

    /**
     * 将 RGB 颜色转为带指定透明度的 ARGB 值。
     */
    private static int colorWithAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * 阵营颜色的半透明背景色（~8% 不透明度）。
     */
    private static int bgColor(int rgb) {
        return colorWithAlpha(rgb, 0x14);
    }
    // ==================== 主覆盖层 ====================

    public static final IGuiOverlay CRYSTAL_DEATH_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        // ---------- 提前退出守卫 ----------
        if (!BetterEvEConfig.INSTANCE.global_hudEnabled.get()) return;
        if (ScenarioManager.getActiveScenario() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var scenario = ScenarioManager.getActiveScenario();
        var factionDefs = scenario.getFactions();
        if (factionDefs == null || factionDefs.isEmpty()) return;
        var visibleDefs = factionDefs.stream()
                .filter(Scenario.FactionDef::isShowOnScoreboard).toList();
        if (visibleDefs.isEmpty()) return;

        var deathCounts = CrystalDeathCounter.getAllFactionDeathCounts();
        var killCounts = CrystalDeathCounter.getAllEntityKillCounts();
        int numFactions = visibleDefs.size();

        // ---------- 布局计算 ----------
        int panelWidth = Math.max(80, Math.min(width / 8, width - 20));
        int contentWidth = panelWidth - PANEL_PADDING_H * 2;
        int sectionWidth = contentWidth / numFactions;
        int panelX = (width - panelWidth) / 2;
        int panelY = PANEL_TOP;
        int panelBottom = panelY + TOTAL_HEIGHT;

        // ---- 1. 主条背景 ----
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + MAIN_HEIGHT, BG_MAIN);

        // ---- 2. 击杀条背景 ----
        guiGraphics.fill(panelX, panelY + MAIN_HEIGHT, panelX + panelWidth, panelBottom, BG_KILL);

        // ---- 3. 区域分隔线和阵营背景色调 ----
        for (int i = 0; i < numFactions; i++) {
            int sl = panelX + PANEL_PADDING_H + sectionWidth * i;
            int sr = sl + sectionWidth;

            // 阵营背景淡色（主条区域）
            guiGraphics.fill(sl, panelY, sr, panelY + MAIN_HEIGHT, bgColor(visibleDefs.get(i).getColorInt()));

            // 分隔线（除最后一段右侧）
            if (i < numFactions - 1) {
                guiGraphics.vLine(sr, panelY, panelBottom, SEPARATOR_ALPHA);
            }
        }

        // ---- 4. 四角色角标 ----
        int pl = panelX, pr = panelX + panelWidth;
        int pt = panelY, pb = panelBottom;

        // 左上
        guiGraphics.hLine(pl + 4, pl + 4 + CORNER_EXTENT, pt + 4, CORNER_GOLD);
        guiGraphics.vLine(pl + 4, pt + 4, pt + 4 + CORNER_EXTENT, CORNER_GOLD);
        // 右上
        guiGraphics.hLine(pr - 4 - CORNER_EXTENT, pr - 4, pt + 4, CORNER_GOLD);
        guiGraphics.vLine(pr - 4, pt + 4, pt + 4 + CORNER_EXTENT, CORNER_GOLD);
        // 左下
        guiGraphics.hLine(pl + 4, pl + 4 + CORNER_EXTENT, pb - 4, CORNER_GOLD);
        guiGraphics.vLine(pl + 4, pb - 4 - CORNER_EXTENT, pb - 4, CORNER_GOLD);
        // 右下
        guiGraphics.hLine(pr - 4 - CORNER_EXTENT, pr - 4, pb - 4, CORNER_GOLD);
        guiGraphics.vLine(pr - 4, pb - 4 - CORNER_EXTENT, pb - 4, CORNER_GOLD);

        // ---- 5. 逐阵营内容 ----
        for (int i = 0; i < numFactions; i++) {
            var f = visibleDefs.get(i);
            int sl = panelX + PANEL_PADDING_H + sectionWidth * i;
            int cx = sl + sectionWidth / 2; // 该区域水平中心
            int cy;

            // 死亡分数（从数据源获取）
            int deathScore = deathCounts.getOrDefault(f.getId(), 0);
            // 击杀数
            int kills = killCounts.getOrDefault(f.getId(), 0);
            // 阵营颜色
            int factionColor = colorWithAlpha(f.getColorInt(), 0xCC); // 80% 不透明度

            if (sectionWidth < 60) {
                // --- 极窄布局：名称 + 分数同行 ---
                String name = f.getName();
                if (name.length() > 4) name = name.substring(0, 4);
                guiGraphics.drawString(mc.font, name, sl + 2, panelY + 4, factionColor, false);
                String scoreStr = String.valueOf(deathScore);
                int sw = mc.font.width(scoreStr);
                guiGraphics.drawString(mc.font, scoreStr, sl + sectionWidth - sw - 2, panelY + 4, factionColor, false);
                // 击败数
                String killStr = "击败 " + kills;
                int kw = mc.font.width(killStr);
                guiGraphics.drawString(mc.font, killStr, cx - kw / 2, panelY + MAIN_HEIGHT + 7, TEXT_WHITE, false);
            } else {
                // --- 标准布局 ---
                // 阵营名称 + "得分"（顶部）
                String name = f.getName();
                guiGraphics.drawString(mc.font, name + " 得分", sl + 4, panelY + 3, factionColor, false);

                // 死亡分数（底部居中，大号）
                String scoreStr = String.valueOf(deathScore);
                var pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(cx, panelY + 30, 0);
                float scale = 1.4f;
                pose.scale(scale, scale, scale);
                int sw = mc.font.width(scoreStr);
                guiGraphics.drawString(mc.font, scoreStr, -sw / 2, -4, factionColor, false);
                pose.popPose();

                // 击败数（击杀条区居中）
                String killStr = "击败数 " + kills;
                int killW = mc.font.width(killStr);
                guiGraphics.drawString(mc.font, killStr, cx - killW / 2, panelY + MAIN_HEIGHT + 7, TEXT_WHITE, false);
            }
        }

        // ---- 6. 主条与击杀条之间的装饰线 ----
        int accentY = panelY + MAIN_HEIGHT;
        // 主横线
        guiGraphics.hLine(panelX + 10, panelX + panelWidth - 10, accentY, ACCENT_LINE_ALPHA);
        // 中央金色小点缀
        int centerX = panelX + panelWidth / 2;
        guiGraphics.hLine(centerX - 12, centerX + 12, accentY, CENTER_DOT_COLOR);
    };

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_scoreboard", CRYSTAL_DEATH_OVERLAY);
    }
}
