package com.sanzineo.bettereve.scenario;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 日光燃烧开关的配置读取入口（写法对齐 {@code faction.SummonRule}）。
 *
 * 配置来源：当前剧本的 {@code options.sunBurn} 段；
 * **没写该段、或根本没加载剧本 → 一律防护开**（不被日光点燃）；
 * 只有剧本明确写 {@code enabled: true} 才走原版（会烧）。
 *
 * 被 {@code mixin/MobSunBurnMixin} 调用：它注入 {@code Mob.isSunBurnTick()} 的 HEAD。
 */
public final class SunBurnRule {

    private SunBurnRule() {
    }

    /** 当前生效的配置；未加载剧本 / 没写这一段时返回 null */
    private static Scenario.SunBurnConfig config() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getOptions() == null) {
            return null;
        }
        return scenario.getOptions().getSunBurn();
    }

    /**
     * 这只生物是否"不许被日光点燃"。
     *
     * @return true = 压掉原版的日光点燃（{@code isSunBurnTick()} 直接返回 false）；false = 走原版
     */
    public static boolean suppresses(Entity entity) {
        Scenario.SunBurnConfig cfg = config();
        if (cfg == null) {
            return true;                   // 没加载剧本 / 剧本没写这一段 → 按默认：防护开（不烧）
        }
        if (cfg.isEnabled()) {
            return false;                  // 剧本明确要烧 → 走原版
        }
        return !isExcepted(cfg, entity);    // 防护开：除例外名单外，全部压掉
    }

    /**
     * 例外名单：写实体 id 精确匹配；末尾带 {@code *} 表示按前缀匹配（如 {@code somemod:*}）。
     *
     * 名单为空时**不做**注册表查询 —— 这是每 tick 每只生物都会走的路，能省一次就省一次。
     */
    private static boolean isExcepted(Scenario.SunBurnConfig cfg, Entity entity) {
        List<String> except = cfg.getExcept();
        if (except == null || except.isEmpty() || entity == null) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return false;
        }
        String id = key.toString();
        for (String pattern : except) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*")) {
                if (id.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
