package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// 自定义目标选择器：完全控制攻击目标选择，确保阵营敌对判定完全生效
public class FactionHostileTargetGoal extends TargetGoal {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionHostileTargetGoal.class);
    private final Mob mob; // 当前生物
    private LivingEntity target; // 目标生物
    private final TargetingConditions targetingConditions; // 目标条件

    // 构造方法
    public FactionHostileTargetGoal(Mob mob, boolean mustSee, boolean mustReach) {
        super(mob, false, mustReach); // 关键：改为false，取消视线检查（穿墙寻敌）
        this.mob = mob;
        // 创建目标条件：取消可见性检查，只检查范围
        this.targetingConditions = TargetingConditions.forCombat()
                .range(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))
                .ignoreLineOfSight(); // 忽略视线阻挡
    }

    // 判断是否应该开始执行该目标
    @Override
    public boolean canUse() {
        // 在周围找到符合条件的目标，完全排除玩家
        List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                entity -> !(entity instanceof Player) && isValidTarget(entity)
        );

        if (nearbyEntities.isEmpty()) {
            // 如果没有找到敌对目标，确保清除当前目标
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
            return false;
        }

        // 找到最近的目标
        target = findNearestTarget(nearbyEntities);
        return target != null;
    }

    // 判断是否应该继续执行该目标
    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) {
            return false;
        }

        // 检查目标是否仍然在范围内且有效
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        boolean isValid = mob.distanceToSqr(target) <= followRange * followRange &&
                isValidTarget(target);
                
        // 如果目标不再有效，清除目标
        if (!isValid) {
            mob.setTarget(null);
        }
        
        return isValid;
    }

    // 优化强制刷新机制，避免频繁中断攻击行为
    public void forceRefreshTarget() {
        if (mob.level().isClientSide) {
            return;
        }
        
        // 只有在当前没有有效目标时才会刷新目标选择，避免频繁刷新导致的攻击行为中断
        if (target == null || !target.isAlive() || !isValidTarget(target)) {
            List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                    LivingEntity.class,
                    mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                    entity -> !(entity instanceof Player) && isValidTarget(entity)
            );

            if (!nearbyEntities.isEmpty()) {
                LivingEntity nearestTarget = findNearestTarget(nearbyEntities);
                if (nearestTarget != null && nearestTarget != target) {
                    target = nearestTarget;
                    mob.setTarget(target);
                }
            } else {
                // 如果没有找到敌对目标，确保清除当前目标
                mob.setTarget(null);
            }
        }
    }

    // 开始执行目标
    @Override
    public void start() {
        mob.setTarget(target);
        super.start();
    }

    // 停止执行目标
    @Override
    public void stop() {
        mob.setTarget(null);
        super.stop();
    }

    // 找到最近的目标
    private LivingEntity findNearestTarget(List<? extends LivingEntity> entities) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            double distance = mob.distanceToSqr(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }

        return nearest;
    }

    // 检查目标是否有效 - 增强版友军判定
    private boolean isValidTarget(LivingEntity target) {
        // 不能攻击自己
        if (target == mob) {
            return false;
        }
        
        // 排除玩家，玩家永远不会成为攻击目标
        if (target instanceof Player) {
            return false;
        }

        // 完全基于阵营判定：只攻击不同阵营的生物
        boolean isHostile = FactionHelper.isEntitiesHostile(this.mob, target);
        
        // 如果是友军，我们需要额外处理：取消敌对目标
        if (!isHostile) {
            // 如果当前目标是友军，直接清除目标
            if (mob.getTarget() == target) {
                mob.setTarget(null);
            }
            LOGGER.debug("Ignoring friendly target: " + target.getName().getString() + 
                    " (Faction: " + FactionHelper.getEntityFactionId(target) + ") " +
                    "Current mob faction: " + FactionHelper.getEntityFactionId(mob));
            return false;
        }
        
        LOGGER.debug("Found hostile target: " + target.getName().getString() + 
                " (Faction: " + FactionHelper.getEntityFactionId(target) + ") " +
                "Current mob faction: " + FactionHelper.getEntityFactionId(mob));
        return true;
    }
}
