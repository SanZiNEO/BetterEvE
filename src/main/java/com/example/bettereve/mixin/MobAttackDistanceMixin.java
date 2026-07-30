package com.example.bettereve.mixin;

import com.example.bettereve.entity.CrystalEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to fix the issue where melee mobs cannot attack CrystalEntity when its width > 4
 * or when the center is blocked by blocks.
 * 
 * The problem: Vanilla Minecraft's attack mechanism locks onto the target's center position.
 * When CrystalEntity has a large collision box (width > 4), attackers may not be able to reach the center.
 * 
 * Solution: When a mob is attacking a CrystalEntity, calculate distance to the nearest point
 * on the collision box instead of the center.
 */
@Mixin(Mob.class)
public abstract class MobAttackDistanceMixin {
    
    /**
     * Modify the perceived target distance calculation for melee attacks.
     * When the target is a CrystalEntity, calculate distance to the nearest point on its collision box.
     * This allows melee mobs to attack CrystalEntity even when it has a large width or when the center is blocked.
     */
    @Inject(method = "getPerceivedTargetDistanceSquareForMeleeAttack", at = @At("HEAD"), cancellable = true)
    private void onGetPerceivedTargetDistanceSquareForMeleeAttack(LivingEntity target, CallbackInfoReturnable<Double> cir) {
        // 非 CrystalEntity 不拦截，走原版逻辑
        if (!(target instanceof CrystalEntity)) {
            return;
        }
        
        Mob mob = (Mob)(Object)this;
        
        // Get the target's bounding box
        AABB boundingBox = target.getBoundingBox();
        
        // Get the mob's position
        Vec3 mobPos = mob.position();
        
        // Calculate the nearest point on the bounding box to the mob
        double nearestX = Mth.clamp(mobPos.x, boundingBox.minX, boundingBox.maxX);
        double nearestY = Mth.clamp(mobPos.y, boundingBox.minY, boundingBox.maxY);
        double nearestZ = Mth.clamp(mobPos.z, boundingBox.minZ, boundingBox.maxZ);
        
        // Calculate squared distance to the nearest point
        double dx = mobPos.x - nearestX;
        double dy = mobPos.y - nearestY;
        double dz = mobPos.z - nearestZ;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        
        // Return the calculated distance
        cir.setReturnValue(distanceSqr);
        cir.cancel();
    }
}