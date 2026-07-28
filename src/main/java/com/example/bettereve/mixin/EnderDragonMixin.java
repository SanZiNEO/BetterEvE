package com.example.bettereve.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EnderDragonMixin — 让所有实体都能对末影龙造成伤害。
 * 
 * 原版 EnderDragon.hurt() 仅当 damageSource.getEntity() instanceof Player 时
 * 才调用 reallyHurt() 应用伤害。此 Mixin 使用 @Inject HEAD + cancellable
 * 完全接管 hurt() 逻辑，移除 Player instanceof 检查。
 * 
 * 不能使用 @At(value = "INSTANCEOF")，Mixin 0.8.5 (Forge 1.20.1) 不支持。
 * 改为 @Inject HEAD + cancellable，这是 Mixin 0.8.5 兼容的方案。
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {

    @Shadow
    private float sittingDamageReceived;
    
    @Shadow
    public EnderDragonPart head;
    
    @Shadow
    protected boolean reallyHurt(DamageSource source, float amount) { return false; }
    @Inject(
        method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHurt(EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EnderDragon dragon = (EnderDragon)(Object)this;
        
        // 保留原版阶段检查：DYING 阶段免疫
        if (dragon.getPhaseManager().getCurrentPhase().getPhase().getId() == 7) {
            cir.setReturnValue(false);
            return;
        }
        
        // 应用阶段伤害调整
        float adjustedAmount = dragon.getPhaseManager().getCurrentPhase().onHurt(source, amount);
        
        // 应用部位伤害减免（头部除外）
        if (part != this.head) {
            adjustedAmount = adjustedAmount / 4.0F + Math.min(adjustedAmount, 1.0F);
        }
        
        if (adjustedAmount < 0.01F) {
            cir.setReturnValue(false);
            return;
        }
        
        // 关键修改：移除 instanceof Player 限制，所有伤害来源都能伤害龙
        float currentHealth = dragon.getHealth();
        this.reallyHurt(source, adjustedAmount);
        
        // 原版死亡阶段逻辑
        if (dragon.isDeadOrDying() && !dragon.getPhaseManager().getCurrentPhase().isSitting()) {
            dragon.setHealth(1.0F);
            dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING);
        }
        
        // 原版坐地阶段伤害累加逻辑
        if (dragon.getPhaseManager().getCurrentPhase().isSitting()) {
            float sittingDamage = this.sittingDamageReceived + currentHealth - dragon.getHealth();
            if (sittingDamage > 0.25F * dragon.getMaxHealth()) {
                dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.TAKEOFF);
            }
        }
        
        cir.setReturnValue(true);
    }
}