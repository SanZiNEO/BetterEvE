package com.example.bettereve.events;

import com.example.bettereve.Bettereve;
import com.example.bettereve.phase.TriggerMatcher;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 剧本裁决规则的执行器 —— 代码只认规则，不认具体实体。
 *
 * 两类规则（定义见 {@link Scenario.Rules}，读取见 {@link ScenarioRules}）：
 *   1. 命中裁决 lethalHits：指定攻击者命中指定受害者时，把这一击的伤害改写成致死量。
 *      在 LivingHurtEvent 上以 LOWEST 优先级执行 —— 此时所有前置判定（含同阵营取消）
 *      都已跑完，我们只改最后那个数字，不介入谁打谁的判定。
 *   2. 状态裁决 stateKills：指定实体一旦满足 NBT 状态探针，就连续击杀若干次；
 *      只要它还活着，之后的每一轮轮询都会再杀一次，直到它真的死透。
 *
 * 剧本没有配置任何规则时，两条路径都在第一行返回，零开销。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScenarioRuleHandler {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/RuleEngine");

    /** 状态裁决登记的被点名实体（服务器主线程独占，无需加锁） */
    private static final List<LivingEntity> TRACKED = new ArrayList<>();

    /** 状态裁决轮询计时 */
    private static int tickCounter = 0;

    /**
     * 命中裁决：把匹配规则的一击改写成致死量。
     *
     * 只改数值、不取消事件：击杀归属、死亡通知、计分链路全部沿用原有逻辑
     * （例如茈带 gojo 阵营，击杀自然记给 gojo）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }
        Scenario.LethalHit rule = ScenarioRules.lethalHitFor(attacker, victim);
        if (rule == null) {
            return;
        }
        LOGGER.info("[命中裁决] {} → {}：{} → {} (剧本 rules.lethalHits)",
                attacker.getType(), victim.getType(), event.getAmount(), rule.getDamage());
        event.setAmount((float) rule.getDamage());
    }

    /** 状态裁决：登记被点名的实体类型（含存档加载进来的）；属性覆盖只对新生成的实体应用 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!event.loadedFromDisk()) {
            // 存档加载的实体跳过：属性已经带上过倍率，再乘一次会翻倍叠乘
            applyAttributeOverride(living);
        }
        if (!ScenarioRules.isStateKillType(entity)) {
            return;
        }
        if (!TRACKED.contains(living)) {
            TRACKED.add(living);
        }
    }

    /** 属性覆盖：按剧本写的倍率改写属性，只改写了的那几项 */
    private static void applyAttributeOverride(LivingEntity living) {
        Scenario.AttributeOverride rule = ScenarioRules.attributeOverrideFor(living);
        if (rule == null || rule.isEmpty()) {
            return;
        }
        StringBuilder applied = new StringBuilder();
        if (applyAttribute(living, Attributes.MAX_HEALTH, rule.getHealthMultiplier(), "最大生命", applied)) {
            // 刚生成时是满血，跟着新的上限补满
            living.setHealth(living.getMaxHealth());
        }
        applyAttribute(living, Attributes.ARMOR, rule.getArmorMultiplier(), "护甲", applied);
        applyAttribute(living, Attributes.ATTACK_DAMAGE, rule.getAttackMultiplier(), "攻击", applied);
        if (applied.length() > 0) {
            LOGGER.info("[属性覆盖] {}：{} (剧本 rules.attributeOverrides)", living.getType(), applied);
        }
    }

    /** 改一项属性；倍率为 1 或实体没有该属性时不动。返回是否真的改了 */
    private static boolean applyAttribute(LivingEntity living, Attribute attribute, double multiplier,
                                          String label, StringBuilder applied) {
        if (multiplier == 1.0) {
            return false;
        }
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        double before = instance.getBaseValue();
        instance.setBaseValue(before * multiplier);
        if (applied.length() > 0) {
            applied.append("、");
        }
        applied.append(label).append(' ').append(before).append(" → ").append(instance.getBaseValue());
        return true;
    }

    /** 状态裁决：按最小间隔轮询状态，命中即连杀 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<Scenario.StateKill> rules = ScenarioRules.stateKills();
        if (rules.isEmpty()) {
            // 剧本没配（或已卸载）：清空登记，后续零开销
            TRACKED.clear();
            return;
        }
        if (TRACKED.isEmpty()) {
            return;
        }
        if (++tickCounter % ScenarioRules.stateKillInterval() != 0) {
            return;
        }

        TRACKED.removeIf(living -> living.isRemoved() || !living.isAlive());
        for (LivingEntity living : TRACKED) {
            Scenario.StateKill rule = ScenarioRules.stateKillFor(living);
            if (rule == null || !matchesState(living, rule)) {
                continue;
            }
            int kills = rule.getKills();
            for (int i = 0; i < kills && living.isAlive() && !living.isRemoved(); i++) {
                // genericKill：原版"击杀"伤害类型，绕过护甲/抗性/无敌帧，保证死透
                living.hurt(living.damageSources().genericKill(), Float.MAX_VALUE);
            }
            LOGGER.info("[状态裁决] {} 命中状态 {}，连杀 {} 次后存活={}",
                    living.getType(), rule.getNbt(), kills, living.isAlive());
        }
    }

    /** 状态探针：把实体序列化成 NBT，逐项比对规则里写的键值（规则必须写探针，空探针视为不匹配） */
    private static boolean matchesState(LivingEntity entity, Scenario.StateKill rule) {
        Map<String, Object> probe = rule.getNbt();
        if (probe == null || probe.isEmpty()) {
            return false;
        }
        return TriggerMatcher.matchesNbt(entity, probe);
    }
}
