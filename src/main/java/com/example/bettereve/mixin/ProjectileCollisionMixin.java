package com.example.bettereve.mixin;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileCollisionMixin {

    // 重写 canHitEntity() 方法，使同阵营的实体无法被击中
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    protected void canHitEntity(Entity p_37250_, CallbackInfoReturnable<Boolean> cir) {
        // 获取当前投掷物实体
        Projectile projectile = (Projectile) (Object) this;
        
        // 检查投掷物是否有阵营属性
        String projectileFactionId = FactionHelper.getEntityFactionId(projectile);
        
        // 只有非中立阵营的投掷物才需要进行同阵营检查
        if (!projectileFactionId.equals("neutral")) {
            // 获取目标实体的阵营属性
            String targetFactionId = FactionHelper.getEntityFactionId(p_37250_);
            
            // 如果目标实体与投掷物阵营相同，则无法被击中
            if (projectileFactionId.equals(targetFactionId)) {
                cir.setReturnValue(false);
            }
        }
    }
}