// 旧的 Config 类已被 BetterEvEConfig 替代
// 保留此类是为了向后兼容，但所有新代码应使用 BetterEvEConfig
package com.example.bettereve;

import com.example.bettereve.config.BetterEvEConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    // 向后兼容的字段
    // 注意：阵营系统配置已从全局配置中移除，现在每个地图单独存储
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
            // 配置加载失败，不输出任何日志
        }
    }
}