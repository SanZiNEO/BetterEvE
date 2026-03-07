package com.example.bettereve.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
    
    /**
     * @author SanZi
     * @reason 修改末影龙的伤害判定机制，让它能够受到所有类型的伤害，包括非玩家造成的伤害和魔法伤害
     */
    @Overwrite
    public boolean hurt(EnderDragonPart p_31121_, DamageSource p_31122_, float p_31123_) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        
        // 保留原有的死亡阶段免疫逻辑
        if (dragon.getPhaseManager().getCurrentPhase().getPhase().getId() == 7) { // 7 是 DYING 阶段的 ID
            return false;
        }
        
        // 应用阶段伤害调整
        p_31123_ = dragon.getPhaseManager().getCurrentPhase().onHurt(p_31122_, p_31123_);
        
        // 应用部位伤害减免（头部除外）
        if (p_31121_ != ((EnderDragon) (Object) this).head) {
            p_31123_ = p_31123_ / 4.0F + Math.min(p_31123_, 1.0F);
        }
        
        if (p_31123_ < 0.01F) {
            return false;
        } else {
            // 移除原有的伤害来源限制，让所有伤害类型都能对末影龙造成伤害
            float f = dragon.getHealth();
            // 使用反射调用父类的 reallyHurt 方法
            try {
                java.lang.reflect.Method reallyHurtMethod = EnderDragon.class.getDeclaredMethod("reallyHurt", DamageSource.class, float.class);
                reallyHurtMethod.setAccessible(true);
                reallyHurtMethod.invoke(dragon, p_31122_, p_31123_);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            
            if (dragon.isDeadOrDying() && !dragon.getPhaseManager().getCurrentPhase().isSitting()) {
                dragon.setHealth(1.0F);
                dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING);
            }
            
            if (dragon.getPhaseManager().getCurrentPhase().isSitting()) {
                // 尝试访问并修改 sittingDamageReceived 字段
                try {
                    java.lang.reflect.Field sittingDamageReceivedField = EnderDragon.class.getDeclaredField("sittingDamageReceived");
                    sittingDamageReceivedField.setAccessible(true);
                    float sittingDamageReceived = sittingDamageReceivedField.getFloat(dragon);
                    sittingDamageReceived += f - dragon.getHealth();
                    sittingDamageReceivedField.setFloat(dragon, sittingDamageReceived);
                    
                    if (sittingDamageReceived > 0.25F * dragon.getMaxHealth()) {
                        sittingDamageReceivedField.setFloat(dragon, 0.0F);
                        dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.TAKEOFF);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            return true;
        }
    }
}