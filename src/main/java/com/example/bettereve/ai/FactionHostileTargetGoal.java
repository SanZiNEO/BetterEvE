package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// 自定义目标选择器：完全控制攻击目标选择，确保阵营敌对判定完全生效
public class FactionHostileTargetGoal extends TargetGoal {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionHostileTargetGoal.class);
    private final Mob mob; // 当前生物
    private LivingEntity target; // 目标生物
    private final TargetingConditions targetingConditions; // 目标条件
    private long lastRefreshTime; // 上次刷新时间

    // 构造方法
    public FactionHostileTargetGoal(Mob mob, boolean mustSee, boolean mustReach) {
        super(mob, false, mustReach);
        this.mob = mob;
        // 创建目标条件：先不设置忽略视线，由 isValidTarget 根据目标类型决定
        this.targetingConditions = TargetingConditions.forCombat()
                .range(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
        this.lastRefreshTime = 0;
    }

    // 判断是否应该开始执行该目标
    @Override
    public boolean canUse() {
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double rangeSqr = followRange * followRange;

        // 如果有已有的目标，检查距离和有效性
        if (target != null && target.isAlive() && mob.distanceToSqr(target) <= rangeSqr && isValidTarget(target)) {
            return true;
        }
        // 旧目标失效或超出范围，清除
        if (target != null) {
            target = null;
            mob.setTarget(null);
        }

        // 在周围找到符合条件的目标
        List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(followRange),
                entity -> isValidTarget(entity)
        );

        if (nearbyEntities.isEmpty()) {
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

    // 强制刷新目标选择机制，确保完全控制目标选择过程
    public void forceRefreshTarget() {
        if (mob.level().isClientSide) {
            return;
        }
        
        long currentTime = mob.level().getGameTime();
        // 限制刷新频率，避免过于频繁
        if (currentTime - lastRefreshTime < 100) { // 至少100tick（5秒）才刷新一次
            return;
        }
        
        lastRefreshTime = currentTime;
        
        // 检查当前目标是否仍然符合条件
        if (target != null) {
            if (!target.isAlive() || !isValidTarget(target)) {
                // 如果当前目标无效，清除目标
                target = null;
                mob.setTarget(null);
            } else {
                // 当前目标仍然有效，检查是否有更合适的目标
                List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                        LivingEntity.class,
                        mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                        entity -> isValidTarget(entity)
                );
                
                if (!nearbyEntities.isEmpty()) {
                    LivingEntity nearestTarget = findNearestTarget(nearbyEntities);
                    if (nearestTarget != null && nearestTarget != target) {
                        target = nearestTarget;
                        mob.setTarget(target);
                    }
                }
            }
        } else {
            // 没有目标，寻找新目标
            List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                    LivingEntity.class,
                    mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                    entity -> isValidTarget(entity)
            );
            
            if (!nearbyEntities.isEmpty()) {
                target = findNearestTarget(nearbyEntities);
                mob.setTarget(target);
            }
        }
    }

    // 开始执行目标
    @Override
    public void start() {
        if (target != null) {
            mob.setTarget(target);
        }
        super.start();
    }

    // 停止执行目标
    @Override
    public void stop() {
        // 不清除目标，长期保留，避免频繁设置/清除导致的开销
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
    private boolean isValidTarget(LivingEntity target) {
        if (target == mob) return false;
        // 完全基于阵营判定，不检查视线（避免战斗中 LOS 短暂失效导致丢失目标）
        boolean isHostile = FactionHelper.isEntitiesHostile(this.mob, target);
        if (!isHostile) {
            if (mob.getTarget() == target) {
                mob.setTarget(null);
            }
            return false;
        }
        return true;
    }
}
