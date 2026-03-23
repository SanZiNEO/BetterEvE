package com.example.bettereve.mixin;

import com.example.bettereve.DebugConfig;
import com.example.bettereve.entity.BaseEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RangedAttackGoal.class, remap = true)
public abstract class RangedAttackGoalMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(RangedAttackGoalMixin.class);

    @Shadow(remap = true)
    private LivingEntity target;

    /**
     * 当远程生物的目标是BaseEntity时，跳过视线检查
     * 这样即使BaseEntity的碰撞箱内部被方块填充，远程生物也能正常攻击
     */
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
            remap = true
        ),
        remap = true
    )
    private boolean redirectHasLineOfSight(Sensing sensing, Entity targetEntity) {
        // 如果目标是BaseEntity，直接返回true，跳过所有视线检查
        if (targetEntity instanceof BaseEntity) {
            if (DebugConfig.isDebugEnabled()) {
                LOGGER.debug("RangedAttackGoalMixin: 检测到BaseEntity目标，跳过视线检查");
            }
            return true;
        }
        
        // 其他实体执行原版逻辑
        return sensing.hasLineOfSight(targetEntity);
    }
}