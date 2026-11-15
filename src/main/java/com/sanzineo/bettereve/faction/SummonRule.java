package com.sanzineo.bettereve.faction;

import com.sanzineo.bettereve.scenario.Scenario;
import com.sanzineo.bettereve.scenario.ScenarioManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * 召唤物机制的配置读取入口。
 *
 * 配置来源：当前剧本的 options.summon 段；
 * 剧本未写该段、或未加载剧本时，一律使用 {@link Scenario.ScenarioOptions} 中的代码内默认值。
 */
public final class SummonRule {

    private SummonRule() {
    }

    /** 取得当前生效的召唤配置；未加载剧本时返回 null（调用方走默认值） */
    private static Scenario.SummonConfig config() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getOptions() == null) {
            return null;
        }
        return scenario.getOptions().getSummon();
    }

    /** 召唤物机制总开关 */
    public static boolean enabled() {
        Scenario.SummonConfig cfg = config();
        return cfg == null || cfg.isEnabled();
    }

    /** 是否让召唤物继承宿主阵营 */
    public static boolean inheritFaction() {
        Scenario.SummonConfig cfg = config();
        return cfg == null || cfg.isInheritFaction();
    }

    /** 召唤物是否继承宿主等级 */
    public static boolean inheritLevel() {
        Scenario.SummonConfig cfg = config();
        return cfg == null || cfg.isInheritLevel();
    }

    /** 召唤物是否计分 */
    public static boolean countsForScore() {
        Scenario.SummonConfig cfg = config();
        return cfg != null && cfg.isCountsForScore();
    }

    /**
     * 判断某实体是否应显示血条（召唤物专用判定）。
     *
     * 优先级：healthBarOverrides 按实体类型覆盖 > hideHealthBar 全局值。
     *
     * @return true = 显示血条，false = 隐藏血条
     */
    public static boolean shouldShowHealthBar(Entity entity) {
        Scenario.SummonConfig cfg = config();
        if (cfg == null) {
            return !Scenario.ScenarioOptions.DEFAULT_SUMMON_HIDE_HEALTH_BAR;
        }

        // 1. 按实体类型覆盖（最高优先级）
        Map<String, Boolean> overrides = cfg.getHealthBarOverrides();
        if (overrides != null && !overrides.isEmpty() && entity != null) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (key != null) {
                Boolean override = overrides.get(key.toString());
                if (override == null) {
                    override = overrides.get(key.getPath());
                }
                if (override != null) {
                    return override;
                }
            }
        }

        // 2. 全局值：hideHealthBar=true 表示召唤物默认不显示血条
        return !cfg.isHideHealthBar();
    }

    /**
     * 按实体类型查询血条覆盖设置。
     *
     * 用途：默认规则是"是生物就有血条"，但个别召唤物（外观特效类、多部件生物的每一节）
     * 挂血条会很难看，剧本可精确点名关掉：
     * <pre>
     * "options": { "summon": { "healthBarOverrides": {
     *     "jujutsucraft:projectile_slash": false,
     *     "alexsmobs:void_worm_part": false
     * }}}
     * </pre>
     *
     * @return true/false = 该类型的覆盖值；null = 无覆盖，走默认
     */
    public static Boolean healthBarOverrideFor(Entity entity) {
        Scenario.SummonConfig cfg = config();
        if (cfg == null || entity == null) {
            return null;
        }
        Map<String, Boolean> overrides = cfg.getHealthBarOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return null;
        }
        Boolean override = overrides.get(key.toString());
        if (override == null) {
            override = overrides.get(key.getPath()); // 允许只写实体名不写命名空间
        }
        return override;
    }

    /**
     * 按实体类型查询剧本指定的强制阵营。
     *
     * 用途：某些实体在模组内部就被清空归属（如咒术回战的茈，cnt2==2.0 分支把
     * OWNER_UUID 写成空串），继承链拿不到证据，会被当成中立生物见谁打谁。
     * 剧本可点名把这类实体直接钉死到某个阵营：
     * <pre>
     * "options": { "summon": { "factionOverrides": {
     *     "jujutsucraft:purple": "gojo"
     * }}}
     * </pre>
     * 强制优先级高于一切继承逻辑；未配置的类型不受影响。
     *
     * @return 阵营 id；未配置返回 null
     */
    public static String factionOverrideFor(Entity entity) {
        Scenario.SummonConfig cfg = config();
        if (cfg == null || entity == null) {
            return null;
        }
        Map<String, String> overrides = cfg.getFactionOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return null;
        }
        String override = overrides.get(key.toString());
        if (override == null) {
            override = overrides.get(key.getPath()); // 允许只写实体名不写命名空间
        }
        return override == null || override.isEmpty() ? null : override;
    }

    /**
     * 实体是否应计入计分板。
     *
     * 多部件生物（虚空蠕虫等长条 boss）按"整体结算"处理：
     * 身体节单独死掉不计分，只有主体真正死亡才计分 —— 避免把一条虫的每一节
     * 都算成一次击杀。
     *
     * 其余情况：召唤物按剧本 countsForScore（默认不计），非召唤物一律计分。
     */
    public static boolean countsForScoreboard(Entity entity) {
        if (SummonedMarker.isMultipartPiece(entity)) {
            return false; // 身体节死亡，不结算
        }
        if (!SummonedMarker.isSummoned(entity)) {
            return true; // 普通生物，保持原有行为
        }
        return countsForScore();
    }
}
