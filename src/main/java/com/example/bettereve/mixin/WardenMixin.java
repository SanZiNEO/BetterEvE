package com.example.bettereve.mixin;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;

@Mixin(Warden.class)
public abstract class WardenMixin extends Mob {

    protected WardenMixin(EntityType<? extends Mob> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    /**
     * @author SanZiNEO
     * @reason 修改 Warden 的目标判定，允许不同阵营的 Warden 互相攻击
     */
    @Overwrite
    public boolean canTargetEntity(net.minecraft.world.entity.Entity entity) {
        // 不能攻击自己
        if (entity == null || entity == this) {
            return false;
        }

        // 不能攻击玩家（保持原设定）
        if (entity instanceof Player) {
            return false;
        }

        // 对于其他 Warden，根据阵营判定是否允许攻击
        if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) {
            return FactionHelper.isEntitiesHostile(this, (net.minecraft.world.entity.LivingEntity) entity);
        }

        // 对于其他类型的生物，保持原有的判定逻辑
        if (entity instanceof net.minecraft.world.entity.LivingEntity livingentity) {
            if (this.level() == entity.level() && 
                net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && 
                !this.isAlliedTo(entity) && 
                livingentity.getType() != EntityType.ARMOR_STAND && 
                !livingentity.isInvulnerable() && 
                !livingentity.isDeadOrDying() && 
                this.level().getWorldBorder().isWithinBounds(livingentity.getBoundingBox())) {
                return true;
            }
        }

        return false;
    }
}