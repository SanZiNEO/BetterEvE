package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Mixin(Warden.class)
public abstract class WardenMixin extends Mob {

    /**
     * 监守者怒气上限。原版 {@code AngerManagement.MAX_ANGER} 是 protected，外部拿不到，这里照抄数值。
     * 原版 {@code AngerLevel}：CALM 0 / AGITATED 40 / ANGRY 80，且 {@code isAngry()} 只认 ANGRY，
     * 所以读数 ≥ 80 就够，这里直接顶到上限，让怒气衰减（每 20 tick 减 1）永远追不上。
     */
    private static final int BETTEREVE_WARDEN_ANGER_CAP = 150;

    /** 补扫寻敌范围（格），与 spawn 命令里那段 50 格寻敌保持一致。 */
    private static final double BETTEREVE_WARDEN_SCAN_RANGE = 50.0D;

    protected WardenMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * 在 Warden.canTargetEntity() 的原版检查之后加两道阵营过滤：
     * <ol>
     *   <li><b>收</b>：原版放行的目标，阵营不敌对 → 改判 false（原版检查全部保留）；</li>
     *   <li><b>放</b>：原版因「监守者不打监守者」（{@code Warden.canTargetEntity} 里排除
     *       {@code EntityType.WARDEN}）而拒绝的目标，双方阵营敌对时改判 true。</li>
     * </ol>
     *
     * <p>第 2 条是阵营对战里监守者互殴的唯一入口：监守者的目标全部来自脑记忆
     * （{@code getTarget()} 读 {@code ATTACK_TARGET}），模组的
     * {@code FactionHostileTargetGoal} 给它设的 {@code Mob.target} 字段没人读；
     * 而 {@code WardenEntitySensor}（采 {@code NEAREST_ATTACKABLE}）、
     * {@code Sniffing}（嗅到目标后涨怒气）、
     * {@code WardenAi} 的 FIGHT 活动（{@code StopAttackingIfTargetInvalid}）、
     * {@code AngerManagement} 都以 {@code canTargetEntity} 为过滤条件，
     * 所以放开这一个方法，脑那套的每一道过滤都会把敌对阵营算作合法目标
     * （至于「怎么把目标找出来」，见本类的 {@code onGetEntityAngryAt}）。
     *
     * <p>第 2 条只替换「排除 WARDEN」这一项，原版同一条判定式里的其它条件在这里逐条复核
     * （同维度 / 非友军 / 非无敌 / 非濒死 / 在世界边界内）。唯一没复核的是
     * 「非创造、非旁观玩家」那条 —— 对监守者目标恒为真。
     */
    @Inject(method = "canTargetEntity", at = @At("RETURN"), cancellable = true)
    private void onCanTargetEntity(@Nullable Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof LivingEntity target)) {
            return;
        }
        boolean hostile = FactionHelper.isEntitiesHostile(this, target);

        if (cir.getReturnValue() == true) {
            if (!hostile) {
                cir.setReturnValue(false);
            }
            return;
        }

        if (hostile
                && target.getType() == EntityType.WARDEN
                && this.level() == target.level()
                && !this.isAlliedTo(target)
                && !target.isInvulnerable()
                && !target.isDeadOrDying()
                && this.level().getWorldBorder().isWithinBounds(target.getBoundingBox())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 让「当前目标（{@code ATTACK_TARGET}）是敌对阵营实体」的监守者怒气读数恒为上限，
     * 从而 {@code getAngerLevel().isAngry()} 恒为 true。
     *
     * <p><b>为什么是 {@code getActiveAnger} 而不是 {@code getAngerLevel}</b>：
     * {@code Warden.getAngerLevel() = AngerLevel.byAnger(getActiveAnger())}，而
     * {@code getActiveAnger() = angerManagement.getActiveAnger(getTarget())} —— 读的正是
     * 「对当前目标的怒气」（目标为空时才退化成 highestAnger）。改这一处同时覆盖两条下游：
     * <ul>
     *   <li>FIGHT 活动（{@code WardenAi.initFightActivity}）的闸门
     *       {@code StopAttackingIfTargetInvalid} 的谓词就是
     *       {@code !getAngerLevel().isAngry() || !canTargetEntity(target)}，命中即 erase
     *       {@code ATTACK_TARGET}；</li>
     *   <li>{@code Warden.customServerAiStep} 里每 20 tick 的 {@code syncClientAngerLevel()}
     *       （心跳 / 触须动画的怒气同步）。</li>
     * </ul>
     *
     * <p><b>为什么必须顶住</b>：原版怒气每 20 tick（1 秒）衰减 1
     * （{@code AngerManagement.tick}，由 {@code Warden.customServerAiStep} 每 20 tick 调用一次），
     * 而监守者没有「扫描敌对生物」的原版行为 —— 只在 spawn 时灌一次怒气的话，
     * 写在阈值上的 80 一秒后就掉到 79，目标被 {@code StopAttackingIfTargetInvalid} 摘掉，
     * 之后也没人再点火。
     *
     * <p>目标不是敌对阵营实体（含原版玩家目标）→ 原样返回读数，行为完全走原版。
     */
    @Inject(method = "getActiveAnger", at = @At("RETURN"), cancellable = true)
    private void onGetActiveAnger(CallbackInfoReturnable<Integer> cir) {
        if (this.bettereve$isHostileFactionTarget(this.bettereve$currentAttackTarget())) {
            cir.setReturnValue(BETTEREVE_WARDEN_ANGER_CAP);
        }
    }

    /**
     * 让监守者「自己」找得到敌对阵营目标。
     *
     * <p>{@code Warden.getEntityAngryAt()} 是原版唯一的目标来源：IDLE / INVESTIGATE / SNIFF
     * 三条活动上的 {@code SetRoarTarget} 都拿它当目标提供者，命中后走
     * {@code ROAR_TARGET → Roar 活动 → Roar.stop() → setAttackTarget()} 这条原版桥，
     * 下游的寻路 / 朝向 / SonicBoom / MeleeAttack 全是原版代码，不用模组插手。
     *
     * <p>原版实现是 {@code getAngerLevel().isAngry() ? angerManagement.getActiveEntity() : empty}，
     * 只认「已经被惹怒过的实体」，不会主动找敌人。所以原版返回空时，这里补一个
     * 「当前目标或附近最近的敌对阵营生物」（阵营判定复用 {@link FactionHelper}）。
     */
    @Inject(method = "getEntityAngryAt", at = @At("RETURN"), cancellable = true)
    private void onGetEntityAngryAt(CallbackInfoReturnable<Optional<LivingEntity>> cir) {
        Optional<LivingEntity> vanilla = cir.getReturnValue();
        if (vanilla != null && vanilla.isPresent()) {
            return;
        }
        LivingEntity hostile = this.bettereve$findHostileFactionTarget();
        if (hostile != null) {
            cir.setReturnValue(Optional.of(hostile));
        }
    }

    /**
     * 取「当前目标」或「附近最近的敌对阵营生物」，都没有则返回 null。
     */
    @Nullable
    private LivingEntity bettereve$findHostileFactionTarget() {
        LivingEntity currentTarget = this.bettereve$currentAttackTarget();
        if (this.bettereve$isHostileFactionTarget(currentTarget)) {
            return currentTarget;
        }

        // 先用脑里现成的记忆：NEAREST_ATTACKABLE 由原版 WardenEntitySensor 每 20 tick 重算一次，
        // 它的过滤谓词就是 Warden.canTargetEntity（本 mixin 已把敌对阵营放行），
        // 所以这个记忆里放的就是 24 格内最近的敌对阵营生物。读记忆零开销，不用自己扫世界。
        LivingEntity fromSensor = this.getBrain()
                .getMemory(MemoryModuleType.NEAREST_ATTACKABLE)
                .filter(this::bettereve$isHostileFactionTarget)
                .orElse(null);
        if (fromSensor != null) {
            return fromSensor;
        }

        // 传感器只覆盖 24 格，再补一次 50 格（与 spawn 命令的寻敌范围一致）的搜索。
        // 每 10 tick 只做一次：无目标的监守者可能有一大群，每 tick 扫 50 格 AABB 太贵。
        if (this.tickCount % 10 != 0) {
            return null;
        }
        return this.bettereve$nearestHostileFactionEntity(BETTEREVE_WARDEN_SCAN_RANGE);
    }

    /**
     * 在 {@code range} 格内找最近的敌对阵营活体（判定条件与 spawn 命令里那段寻敌一致）。
     */
    @Nullable
    private LivingEntity bettereve$nearestHostileFactionEntity(double range) {
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(range),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && !candidate.isInvulnerable()
                        && !candidate.isDeadOrDying()
                        && this.bettereve$isHostileFactionTarget(candidate));

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = this.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * 等价于 {@code Warden.getTarget()}：读脑里的 {@code ATTACK_TARGET} 记忆。
     * 这里直接读记忆而不用 {@code this.getTarget()}，是为了不依赖 mixin 对
     * 「mixin 类自身继承来的方法调用」的引用重映射（万一没重映射，就会退回读
     * {@code Mob.target} 字段 —— 监守者从不写那个字段，永远为 null）。
     */
    @Nullable
    private LivingEntity bettereve$currentAttackTarget() {
        return this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /**
     * 目标是「敌对阵营的活体」时为 true；null / 已死 / 已移除 / 非敌对阵营一律 false（→ 走原版）。
     */
    private boolean bettereve$isHostileFactionTarget(@Nullable LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && !candidate.isRemoved()
                && FactionHelper.isEntitiesHostile(this, candidate);
    }
}
