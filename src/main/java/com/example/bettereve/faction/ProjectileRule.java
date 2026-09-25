package com.example.bettereve.faction;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.world.entity.Entity;

/**
 * 投掷物机制的配置读取入口。
 *
 * 配置来源：当前剧本的 options.projectile 段；
 * 剧本未写该段、或未加载剧本时，一律使用 {@link Scenario.ScenarioOptions} 中的代码内默认值。
 */
public final class ProjectileRule {

    private ProjectileRule() {
    }

    /** 取得当前生效的投掷物配置；未加载剧本时返回 null（调用方走默认值） */
    private static Scenario.ProjectileConfig config() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getOptions() == null) {
            return null;
        }
        return scenario.getOptions().getProjectile();
    }

    /** 投掷物机制总开关 */
    public static boolean enabled() {
        Scenario.ProjectileConfig cfg = config();
        return cfg == null || cfg.isEnabled();
    }

    /** 是否让投掷物继承宿主阵营 */
    public static boolean inheritFaction() {
        Scenario.ProjectileConfig cfg = config();
        return cfg == null || cfg.isInheritFaction();
    }

    /** 是否让投掷物继承宿主等级 */
    public static boolean inheritLevel() {
        Scenario.ProjectileConfig cfg = config();
        return cfg == null || cfg.isInheritLevel();
    }

    /**
     * "投射物语义但非 Projectile 子类"的兜底识别。
     *
     * Projectile 子类（含 AbstractArrow）已经由 instanceof 覆盖，这里只处理
     * 模组自己实现、不继承 Projectile 的远程实体。
     *
     * 关键词来自整合包实测（咒术回战 bullet_knife / slash、各类 bullet 系），
     * 只作为后备判据：走到这里说明该实体已经能解析出 owner，
     * 归属关系是确定的，类型判断只为排除"被误当投射物的 Mob"。
     */
    public static boolean isProjectileLike(Entity entity) {
        String name = entity.getType().toString().toLowerCase();
        return name.contains("arrow") || name.contains("fireball") || name.contains("snowball")
                || name.contains("egg") || name.contains("pearl") || name.contains("trident")
                || name.contains("bottle") || name.contains("potion") || name.contains("bullet")
                || name.contains("_shot") || name.contains("shulker_bullet") || name.contains("llama_spit")
                || name.contains("knife") || name.contains("dagger") || name.contains("blade")
                || name.contains("spear") || name.contains("rock") || name.contains("shard")
                || name.contains("needle") || name.contains("spit") || name.contains("nail")
                || name.contains("kunai") || name.contains("shuriken") || name.contains("missile");
    }
}
