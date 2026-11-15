package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.entity.CrystalEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SensingMixin - 通用解决方案
 * 当远程生物（RangedAttackMob）攻击CrystalEntity时，跳过所有视线检查
 * 这样无论什么类型的远程生物（骷髅、掠夺者、模组生物等）都能正常攻击CrystalEntity
 */
@Mixin(Sensing.class)
public abstract class SensingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(SensingMixin.class);
    
    @Shadow(remap = true)
    private Mob mob;
    
    /**
     * 重定向hasLineOfSight方法
     * 当攻击者是远程生物（RangedAttackMob）且目标是CrystalEntity时，直接返回true
     * 这样远程生物就能完全跳过视线检查，正常攻击CrystalEntity
     */
    @Inject(
        method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void bettereve$skipLineOfSightForCrystalEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // 检查攻击者是否是远程生物，且目标是否是CrystalEntity
        if (this.mob instanceof RangedAttackMob && entity instanceof CrystalEntity) {
            LOGGER.debug("SensingMixin: 远程生物攻击CrystalEntity，跳过视线检查");
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}