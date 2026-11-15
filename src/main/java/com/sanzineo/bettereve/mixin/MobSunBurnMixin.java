package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.scenario.SunBurnRule;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 日光燃烧开关：掐在源头。
 *
 * 原版只有三类生物在 {@code aiStep()} 里调 {@code Mob.isSunBurnTick()}
 * （{@code AbstractSkeleton:95} / {@code Zombie:214} / {@code Phantom:130}），
 * 判定为真就点燃 8 秒。这里在 HEAD 直接返回 false ——
 * 连带跳过原版那套「算光照 + 取 BlockPos + canSeeSky + 摇随机数」的计算，
 * 所以是**净省**，不是多花。
 *
 * 只压「日光点燃」这一条路：岩浆 / 火焰 / 闪电 / {@code /ignite} 照旧
 * （那些不走 isSunBurnTick，从别处调 setSecondsOnFire）。
 *
 * 剧本没写 {@code options.sunBurn} 段时，行为与原版逐项一致。
 *
 * 注意：目标类就是 Mob 本身，所以这里**不能** extends Mob（会构成环，
 * 启动时 InvalidMixinException）；`this` 用 {@code (Mob)(Object)this} 取
 * —— 与 {@link MobAttackDistanceMixin} 同一套写法。
 */
@Mixin(Mob.class)
public abstract class MobSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void bettereve$sunBurnSwitch(CallbackInfoReturnable<Boolean> cir) {
        if (SunBurnRule.suppresses((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
