package com.example.bettereve.mixin;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TargetGoal 兜底 Mixin — 所有目标选择器在 canContinueToUse() 返回 true 时，
 * 额外检查目标是否为友军。防止任何遗漏路径导致同阵营互殴。
 */
@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void onCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == true) {
            Mob mob = (Mob) (Object) this;
            LivingEntity target = mob.getTarget();
            if (target != null && !FactionHelper.isEntitiesHostile(mob, target)) {
                cir.setReturnValue(false);
            }
        }
    }
}
