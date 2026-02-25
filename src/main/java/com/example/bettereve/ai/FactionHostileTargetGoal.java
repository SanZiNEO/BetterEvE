package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.List;

// 自定义目标选择器：在原版逻辑基础上，新增阵营敌对判定
public class FactionHostileTargetGoal extends TargetGoal {
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
        // 在周围找到符合条件的目标
        List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                this::isValidTarget
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
        return mob.distanceToSqr(target) <= mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) *
                mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) &&
                isValidTarget(target);
    }

    // 每2Tick刷新一次目标，不丢失仇恨，强化寻敌积极性
    public void forceRefreshTarget() {
        if (mob.level().isClientSide) {
            return;
        }
        
        List<? extends LivingEntity> nearbyEntities = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                this::isValidTarget
        );

        if (!nearbyEntities.isEmpty()) {
            LivingEntity nearestTarget = findNearestTarget(nearbyEntities);
            if (nearestTarget != null && nearestTarget != target) {
                target = nearestTarget;
                mob.setTarget(target);
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

    // 检查目标是否有效
    private boolean isValidTarget(LivingEntity target) {
        // 不能攻击自己
        if (target == mob) {
            return false;
        }

        // 完全基于阵营判定：只攻击不同阵营的生物
        return FactionHelper.isEntitiesHostile(this.mob, target);
    }
}
