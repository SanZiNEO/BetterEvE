package com.example.bettereve.targeting;

import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.ai.FactionHostileTargetGoal;
import com.example.bettereve.ai.PathfindToWaypointGoal;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TargetingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetingHandler.class);

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob) {
            // 为新加入的生物设置扩大的寻敌范围
            setupExpandedFollowRange(mob);
            // 为新加入的生物添加阵营敌对目标选择器
            setupFactionHostileTargetGoal(mob);
        }
    }

    // 监听生物的 tick 事件，每100tick（5秒）刷新一次目标
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            // 每100tick（5秒）刷新一次目标，降低CPU开销
            if (mob.tickCount % 100 == 0) {
                // 遍历目标选择器中的所有目标选择器，找到我们的自定义目标选择器
                mob.targetSelector.getAvailableGoals().stream()
                        .filter(goal -> goal.getGoal() instanceof FactionHostileTargetGoal)
                        .forEach(goal -> ((FactionHostileTargetGoal) goal.getGoal()).forceRefreshTarget());
                
            }
        }
    }

    private static void setupExpandedFollowRange(Mob mob) {
        // 使用配置文件中的默认寻敌范围
        double followRange = BetterEvEConfig.INSTANCE.targeting_defaultFollowRange.get();

        // 使用属性系统设置寻敌范围，这是更稳定的方法
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRange);
        }
    }

    private static void setupFactionHostileTargetGoal(Mob mob) {
        // 检查是否已添加过我们的自定义目标选择器，如果是则返回，避免重复添加
        boolean hasCustomGoal = mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(goalEntry -> goalEntry.getGoal() instanceof FactionHostileTargetGoal);
        
        if (hasCustomGoal) {
            return;
        }
        
        // 移除原版敌对目标选择器
        mob.targetSelector.removeAllGoals(goal ->
            goal instanceof NearestAttackableTargetGoal ||
            goal instanceof HurtByTargetGoal
        );
        
        // 添加阵营目标选择器（优先级最高）
        mob.targetSelector.addGoal(0, new FactionHostileTargetGoal(mob, true, true));
        
        // 如果是被动生物（没有 RangedAttackGoal 也没有 MeleeAttackGoal），添加一个近战攻击 Goal
        if (mob instanceof PathfinderMob pathfinder) {
            boolean hasRanged = pathfinder.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof RangedAttackGoal);
            boolean hasMelee = pathfinder.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal);
            if (!hasRanged && !hasMelee) {
                pathfinder.goalSelector.addGoal(2, new MeleeAttackGoal(pathfinder, 1.0, false));
            }
            // 对非 neutral 阵营生物添加寻路 AI（优先级 1，在战斗 Goal 0 之后）
            String factionId = FactionHelper.getEntityFactionId(mob);
            if (!"neutral".equals(factionId)) {
                pathfinder.goalSelector.addGoal(1, new PathfindToWaypointGoal(pathfinder));
            }
        }
    }
    
    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
    }
}
