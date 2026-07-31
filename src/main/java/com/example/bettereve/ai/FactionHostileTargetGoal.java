package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 阵营目标选择器：同阵营永不互打，异阵营按剧本关系判定（FactionRelationManager）。
// 继承原版 NearestAttackableTargetGoal，复用其随机间隔扫描（平均每 10 游戏 tick 一次）、
// 目标失效自动清除、生命周期与 TARGET flag 抢占机制。
public class FactionHostileTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/TargetGoal");

    public FactionHostileTargetGoal(Mob mob) {
        // (mob, 目标类型, randomInterval=10, mustSee=false, mustReach=false, selector=null)
        // mustSee=false：不检查视线（避免战斗中 LOS 短暂失效丢目标）
        super(mob, LivingEntity.class, 10, false, false, null);
    }

    // 目标中途失效时立即丢弃：目标阵营变化（如玩家切换阵营）或目标变为中立后不再追打。
    // 基类 canContinueToUse 不重查阵营，此覆写保证"同阵营永不互打"契约在战斗中也成立
    // （原 TargetGoalMixin 的兜底职责，用纯代码替代，Mixin 随之删除）。
    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (FactionHelper.isEntitiesHostile(this.mob, target)) {
            return true;
        }
        if (target != null) {
            LOGGER.debug("[{}] 阵营重查失败，放弃目标 #{} {} faction={} 位置=({}, {}, {})",
                    this.mob.getDisplayName().getString(), target.getId(),
                    target.getDisplayName().getString(), FactionHelper.getEntityFactionId(target),
                    String.format("%.1f", target.getX()), String.format("%.1f", target.getY()),
                    String.format("%.1f", target.getZ()));
        }
        return false;
    }

    protected void findTarget() {
        double range = this.getFollowDistance();
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : this.mob.level().getEntitiesOfClass(
                LivingEntity.class, this.getTargetSearchArea(range),
                // 原版语义：创造/旁观玩家不可成为目标（canBeSeenAsEnemy），隐身且不发光者也不可见
                e -> e != this.mob && e.isAlive() && e.canBeSeenAsEnemy() && e.canBeSeenByAnyone())) {
            // 唯一判定标准：阵营敌对关系（同阵营/中立 → false 排除；未定义关系 → 默认敌对）
            if (!FactionHelper.isEntitiesHostile(this.mob, e)) continue;
            double d = this.mob.distanceToSqr(e);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        this.target = nearest;
        // 目标选择变更时记录（调试用）：本机位置/阵营 + 选中的目标 id/阵营/位置
        if (nearest != null && nearest != this.mob.getTarget()) {
            LOGGER.debug("[{}] 选中目标 #{} {} faction={} 位置=({}, {}, {}) 本机=({}, {}, {}) faction={} 距离={}",
                    this.mob.getDisplayName().getString(), nearest.getId(),
                    nearest.getDisplayName().getString(), FactionHelper.getEntityFactionId(nearest),
                    String.format("%.1f", nearest.getX()), String.format("%.1f", nearest.getY()),
                    String.format("%.1f", nearest.getZ()),
                    String.format("%.1f", this.mob.getX()), String.format("%.1f", this.mob.getY()),
                    String.format("%.1f", this.mob.getZ()),
                    FactionHelper.getEntityFactionId(this.mob), String.format("%.1f", Math.sqrt(best)));
        }
    }
}
