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
        super(mob, false, mustReach); // 关键：改为false，取消视线检查（穿墙寻敌）
        this.mob = mob;
        // 创建目标条件：取消可见性检查，只检查范围
        this.targetingConditions = TargetingConditions.forCombat()
                .range(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))
                .ignoreLineOfSight(); // 忽略视线阻挡
        this.lastRefreshTime = 0;
    }

    // 判断是否应该开始执行该目标
    @Override
    public boolean canUse() {
        // 如果有已有的目标，长期保留，不频繁清除
        if (target != null && target.isAlive() && isValidTarget(target)) {
            return true;
        }

        // 在周围找到符合条件的目标，完全排除玩家
        List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                entity -> isValidTarget(entity)
        );

        if (nearbyEntities.isEmpty()) {
            // 如果没有找到敌对目标，保留当前目标（长期保留）
            // 不清除目标，避免频繁设置/清除导致的开销
            return target != null;
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

    // 检查目标是否有效 - 增强版友军判定
    private boolean isValidTarget(LivingEntity target) {
        // 不能攻击自己
        if (target == mob) {
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
            return false;
        }
        
        return true;
    }
}
