package com.example.bettereve.ai;

import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 阵营目标选择器：同阵营永不互打，异阵营按剧本关系判定（FactionRelationManager）。
// 继承原版 NearestAttackableTargetGoal，复用其随机间隔扫描（平均每 10 游戏 tick 一次）、
// 目标失效自动清除、生命周期与 TARGET flag 抢占机制。
// 双层巡敌（距离参数来自剧本 options，随地图设计配置）：
// - 常态巡敌：targetingRange 内选最近敌对目标，普通生物优先于基地（CrystalEntity）；
// - 近距离重扫：战斗中每 targetingFocusInterval tick 重扫 targetingFocusRange，
//   范围内出现"非基地"敌对目标就切换目标（小兵优先于基地，就近原则）；只有基地则不切换。
public class FactionHostileTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/TargetGoal");

    // 剧本未加载/未配置时的兜底默认值（与 ScenarioOptions 初始值一致）
    private static final double DEFAULT_TARGETING_RANGE = 64.0;
    private static final double DEFAULT_FOCUS_RANGE = 10.0;
    private static final int DEFAULT_FOCUS_INTERVAL = 20;

    private int focusScanTimer; // 近距离重扫计时器（tick）

    public FactionHostileTargetGoal(Mob mob) {
        // (mob, 目标类型, randomInterval=10, mustSee=远程生物要求视线, mustReach=false, selector=null)
        // 远程生物（骷髅/掠夺者等）必须看见目标，避免穿墙选敌导致射箭 AI 永远拉不满弓；
        // 近战生物不强制视线，避免战斗中短暂遮挡就丢目标。
        super(mob, LivingEntity.class, 10, mob instanceof RangedAttackMob, false, null);
    }

    /** 远程生物必须能看到目标才能将其当作攻击对象（保持原版射箭节奏）。 */
    private boolean requiresLineOfSight() {
        return this.mob instanceof RangedAttackMob;
    }

    // 目标中途失效时立即丢弃：目标阵营变化（如玩家切换阵营）或目标变为中立后不再追打。
    // 基类 canContinueToUse 不重查阵营，此覆写保证"同阵营永不互打"契约在战斗中也成立；
    // 同时承担近距离重扫：聚焦范围内出现更优（非基地）目标时切换，实现就近巡敌。
    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (FactionHelper.isEntitiesHostile(this.mob, target)) {
            // 近距离重扫：每 N tick 检查聚焦范围内是否有非基地敌对目标 → 切换（就近 + 小兵优先）
            if (++focusScanTimer >= focusInterval()) {
                focusScanTimer = 0;
                LivingEntity focus = findFocusTarget();
                if (focus != null && focus != target) {
                    LOGGER.debug("[{}] 近距离重扫：切换目标 #{} {} faction={} 位置=({}, {}, {})（原目标 #{} {} 距离={}）",
                            this.mob.getDisplayName().getString(), focus.getId(),
                            focus.getDisplayName().getString(), FactionHelper.getEntityFactionId(focus),
                            String.format("%.1f", focus.getX()), String.format("%.1f", focus.getY()),
                            String.format("%.1f", focus.getZ()),
                            target.getId(), target.getDisplayName().getString(),
                            String.format("%.1f", Math.sqrt(this.mob.distanceToSqr(target))));
                    this.target = focus;
                    this.mob.setTarget(focus);
                }
            }
            return true;
        }
        // 阵营重查失败：目标还活着且在范围内，但已不再敌对（如玩家切换阵营）
        if (target != null) {
            LOGGER.debug("[{}] 阵营重查失败，放弃目标 #{} {} faction={} 位置=({}, {}, {})",
                    this.mob.getDisplayName().getString(), target.getId(),
                    target.getDisplayName().getString(), FactionHelper.getEntityFactionId(target),
                    String.format("%.1f", target.getX()), String.format("%.1f", target.getY()),
                    String.format("%.1f", target.getZ()));
        }
        return false;
    }

    protected void findTarget() {
        double range = this.getFollowDistance();
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : this.mob.level().getEntitiesOfClass(
                LivingEntity.class, this.getTargetSearchArea(range),
                // 原版语义：创造/旁观玩家不可成为目标（canBeSeenAsEnemy），隐身且不发光者也不可见
                e -> e != this.mob && e.isAlive() && e.canBeSeenAsEnemy() && e.canBeSeenByAnyone()
                        && (!requiresLineOfSight() || this.mob.getSensing().hasLineOfSight(e)))) {
            // 唯一判定标准：阵营敌对关系（同阵营/中立 → false 排除；未定义关系 → 默认敌对）
            if (!FactionHelper.isEntitiesHostile(this.mob, e)) continue;
            // 优先级：普通生物 > 基地（CrystalEntity）；同类内取最近
            boolean eBase = e instanceof CrystalEntity;
            boolean curBase = nearest instanceof CrystalEntity;
            if (nearest == null || (!eBase && curBase) || (eBase == curBase && this.mob.distanceToSqr(e) < best)) {
                best = this.mob.distanceToSqr(e);
                nearest = e;
            }
        }
        this.target = nearest;
        // 目标选择变更时记录（调试用）：本机位置/阵营 + 选中的目标 id/阵营/位置
        if (nearest != null && nearest != this.mob.getTarget()) {
            LOGGER.debug("[{}] 选中目标 #{} {} faction={} 位置=({}, {}, {}) 本机=({}, {}, {}) faction={} 距离={}",
                    this.mob.getDisplayName().getString(), nearest.getId(),
                    nearest.getDisplayName().getString(), FactionHelper.getEntityFactionId(nearest),
                    String.format("%.1f", nearest.getX()), String.format("%.1f", nearest.getY()),
                    String.format("%.1f", nearest.getZ()),
                    String.format("%.1f", this.mob.getX()), String.format("%.1f", this.mob.getY()),
                    String.format("%.1f", this.mob.getZ()),
                    FactionHelper.getEntityFactionId(this.mob), String.format("%.1f", Math.sqrt(best)));
        }
    }

    /** 聚焦范围内（targetingFocusRange）最近的非基地敌对目标；无则返回 null（不切换） */
    private LivingEntity findFocusTarget() {
        double range = focusRange();
        double rangeSqr = range * range;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : this.mob.level().getEntitiesOfClass(
                LivingEntity.class, this.getTargetSearchArea(range),
                e -> e != this.mob && e.isAlive() && e.canBeSeenAsEnemy() && e.canBeSeenByAnyone()
                        && !(e instanceof CrystalEntity)
                        && (!requiresLineOfSight() || this.mob.getSensing().hasLineOfSight(e))
                        && FactionHelper.isEntitiesHostile(this.mob, e))) {
            double d = this.mob.distanceToSqr(e);
            if (d <= rangeSqr && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    // ---- 剧本参数读取（未加载剧本/未配置时用兜底默认值）----

    private static Scenario.ScenarioOptions scenarioOptions() {
        Scenario s = ScenarioManager.getActiveScenario();
        return s != null ? s.getOptions() : null;
    }

    /** 常态巡敌距离（格）——同时用于 FOLLOW_RANGE 属性同步 */
    public static double targetingRange() {
        Scenario.ScenarioOptions o = scenarioOptions();
        return o != null && o.getTargetingRange() > 0 ? o.getTargetingRange() : DEFAULT_TARGETING_RANGE;
    }

    /** 近距离重扫距离（格） */
    private static double focusRange() {
        Scenario.ScenarioOptions o = scenarioOptions();
        return o != null && o.getTargetingFocusRange() > 0 ? o.getTargetingFocusRange() : DEFAULT_FOCUS_RANGE;
    }

    /** 近距离重扫间隔（tick） */
    private static int focusInterval() {
        Scenario.ScenarioOptions o = scenarioOptions();
        return o != null && o.getTargetingFocusInterval() > 0 ? o.getTargetingFocusInterval() : DEFAULT_FOCUS_INTERVAL;
    }
}
