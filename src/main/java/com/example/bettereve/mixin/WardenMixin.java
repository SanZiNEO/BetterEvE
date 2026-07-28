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
     * 在 Warden.canTargetEntity() 的原版检查之后，额外添加阵营过滤。
     * 原版所有检查（无敌、死亡、边界、Warden 不能打 Warden 等）全部保留。
     */
    @Inject(method = "canTargetEntity", at = @At("RETURN"), cancellable = true)
    private void onCanTargetEntity(@Nullable Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == true && entity instanceof LivingEntity target) {
            if (!FactionHelper.isEntitiesHostile(this, target)) {
                cir.setReturnValue(false);
            }
        }
    }
}