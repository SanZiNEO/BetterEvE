// 旧的 Config 类已被 BetterEvEConfig 替代
// 保留此类是为了向后兼容，但所有新代码应使用 BetterEvEConfig
package com.example.bettereve;

import com.example.bettereve.config.BetterEvEConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    // 向后兼容的字段
    public static boolean factionDefaultHostile;
    public static List<String> factionList;
    public static double renderOutlineWidth;
    public static boolean renderShowHealthPercent;
    public static double targetingDefaultFollowRange;
    public static int baseDefaultHealth;
    public static int baseClearRadius;
    public static double levelHealthBonus;
    public static double levelAttackBonus;
    public static double levelSpeedBonus;
    public static int levelMax;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 检查是否是我们的配置并且配置已加载
        if (event.getConfig().getSpec() != BetterEvEConfig.COMMON_SPEC) {
            return;
        }
        
        try {
            // 从 BetterEvEConfig 读取配置
            factionDefaultHostile = BetterEvEConfig.INSTANCE.faction_defaultHostile.get();
            // 解析阵营列表
            factionList = new ArrayList<>();
            String factionsStr = BetterEvEConfig.INSTANCE.faction_factions.get();
            if (!factionsStr.isEmpty()) {
                String[] factions = factionsStr.split(";");
                for (String faction : factions) {
                    if (!faction.trim().isEmpty()) {
                        factionList.add(faction.trim());
                    }
                }
            }
            
            renderOutlineWidth = BetterEvEConfig.INSTANCE.render_outlineWidth.get();
            renderShowHealthPercent = BetterEvEConfig.INSTANCE.render_showHealthPercent.get();
            targetingDefaultFollowRange = BetterEvEConfig.INSTANCE.targeting_defaultFollowRange.get();
            baseDefaultHealth = BetterEvEConfig.INSTANCE.base_defaultHealth.get();
            baseClearRadius = BetterEvEConfig.INSTANCE.base_clearRadius.get();
            levelHealthBonus = BetterEvEConfig.INSTANCE.level_healthBonus.get();
            levelAttackBonus = BetterEvEConfig.INSTANCE.level_attackBonus.get();
            levelSpeedBonus = BetterEvEConfig.INSTANCE.level_speedBonus.get();
            levelMax = BetterEvEConfig.INSTANCE.level_maxLevel.get();
            
            // 通知 TargetingHandler 重新加载配置
            com.example.bettereve.targeting.TargetingHandler.loadFromConfig();
        } catch (Exception e) {
            // 记录配置加载失败的日志
            System.err.println("配置加载失败: " + e.getMessage());
        }
    }
}
