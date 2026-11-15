package com.sanzineo.bettereve.scenario;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;

/**
 * 剧本裁决规则的读取入口。
 *
 * 设计：代码只提供"规则种类 + 执行器"（执行在 {@code events/ScenarioRuleHandler}），
 * 具体针对哪个实体、什么状态、杀几次，全部写在剧本的 options.rules 段里 ——
 * 剧本特例不进代码，改规则只改 JSON。
 */
public final class ScenarioRules {

    private ScenarioRules() {
    }

    /** 取当前剧本的规则段；无剧本/未配置返回 null */
    private static Scenario.Rules rules() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null) {
            return null;
        }
        Scenario.ScenarioOptions options = scenario.getOptions();
        return options == null ? null : options.getRules();
    }

    /**
     * 命中裁决匹配：找出"攻击者 → 受害者"命中的第一条规则。
     *
     * 匹配顺序：攻击者类型 → 受害者类型 → 攻击者标记（attackerFlag 为空表示不校验）。
     */
    public static Scenario.LethalHit lethalHitFor(Entity attacker, Entity victim) {
        Scenario.Rules rules = rules();
        if (rules == null || rules.getLethalHits() == null || attacker == null || victim == null) {
            return null;
        }
        for (Scenario.LethalHit rule : rules.getLethalHits()) {
            if (!matchesType(attacker, rule.getAttacker()) || !matchesType(victim, rule.getVictim())) {
                continue;
            }
            String flag = rule.getAttackerFlag();
            if (flag != null && !flag.isEmpty() && !attacker.getPersistentData().getBoolean(flag)) {
                continue;
            }
            return rule;
        }
        return null;
    }

    /** 状态裁决：该实体命中的第一条规则；没有返回 null */
    public static Scenario.StateKill stateKillFor(Entity entity) {
        Scenario.Rules rules = rules();
        if (rules == null || rules.getStateKills() == null || entity == null) {
            return null;
        }
        for (Scenario.StateKill rule : rules.getStateKills()) {
            if (matchesType(entity, rule.getType())) {
                return rule;
            }
        }
        return null;
    }

    /** 该实体类型是否被任何状态裁决点名（用于决定要不要登记追踪） */
    public static boolean isStateKillType(Entity entity) {
        return stateKillFor(entity) != null;
    }

    /** 属性覆盖：该实体命中的第一条规则；没有返回 null */
    public static Scenario.AttributeOverride attributeOverrideFor(Entity entity) {
        Scenario.Rules rules = rules();
        if (rules == null || rules.getAttributeOverrides() == null || entity == null) {
            return null;
        }
        for (Scenario.AttributeOverride rule : rules.getAttributeOverrides()) {
            if (matchesType(entity, rule.getType())) {
                return rule;
            }
        }
        return null;
    }

    /** 全部状态裁决规则；无剧本/未配置返回空表 */
    public static List<Scenario.StateKill> stateKills() {
        Scenario.Rules rules = rules();
        if (rules == null || rules.getStateKills() == null) {
            return Collections.emptyList();
        }
        return rules.getStateKills();
    }

    /** 轮询间隔：取所有状态裁决规则里最小的一个（没有规则返回默认值） */
    public static int stateKillInterval() {
        int interval = Scenario.StateKill.DEFAULT_INTERVAL_TICKS;
        for (Scenario.StateKill rule : stateKills()) {
            interval = Math.min(interval, rule.getIntervalTicks());
        }
        return interval;
    }

    /** 实体类型匹配：支持 modid:name 与只写实体名两种写法（规则必须写类型，没写视为不匹配） */
    private static boolean matchesType(Entity entity, String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        return com.sanzineo.bettereve.phase.TriggerMatcher.matchesType(entity, type);
    }
}
