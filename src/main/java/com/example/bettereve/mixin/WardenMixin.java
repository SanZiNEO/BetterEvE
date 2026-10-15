package com.example.bettereve.mixin;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Warden.class)
public abstract class WardenMixin extends Mob {

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
     * 所以放开这一个方法就能让脑那套自己找到敌对阵营的监守者。
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
}