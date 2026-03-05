package com.example.bettereve.targeting;

import com.example.bettereve.Config;
import com.example.bettereve.ai.FactionHostileTargetGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
                
                // 同时强制刷新目标选择器，确保目标不会丢失
                mob.targetSelector.tick();
            }
        }
    }

    private static void setupExpandedFollowRange(Mob mob) {
        // 使用配置文件中的默认寻敌范围
        double followRange = Config.targetingDefaultFollowRange;

        // 使用属性系统设置寻敌范围，这是更稳定的方法
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRange);
        }
    }

    private static void setupFactionHostileTargetGoal(Mob mob) {
        // 优化方案：保留非敌对目标选择器，只替换敌对目标选择器
        // 这样可以确保我们的阵营判定逻辑生效，同时保留生物的基本AI行为
        
        // 检查是否已添加过我们的自定义目标选择器，如果是则返回，避免重复添加
        boolean hasCustomGoal = false;
        
        try {
            hasCustomGoal = mob.targetSelector.getAvailableGoals().stream()
                    .anyMatch(goalEntry -> goalEntry.getGoal() instanceof FactionHostileTargetGoal);
        } catch (Exception e) {
        }
        
        if (hasCustomGoal) {
            return;
        }
        
        // 只替换敌对目标选择器，保留其他目标选择器
        try {
            // 移除原版的敌对目标选择器（如 NearestAttackableTargetGoal 等）
            java.lang.reflect.Method removeAllGoalsMethod = net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredMethod("removeAllGoals", java.util.function.Predicate.class);
            removeAllGoalsMethod.setAccessible(true);
            removeAllGoalsMethod.invoke(mob.targetSelector, (java.util.function.Predicate<net.minecraft.world.entity.ai.goal.Goal>) goal -> 
                    goal.getClass().getName().contains("Attackable") || 
                    goal.getClass().getName().contains("Hostile") || 
                    goal.getClass().getName().contains("Target") && !goal.getClass().getName().contains("Faction")
            );
        } catch (Exception e) {
        }
        
        // 为生物添加自定义的阵营敌对目标选择器
        // 优先级设置为1，确保它高于其他可能存在的目标选择器
        mob.targetSelector.addGoal(1, new FactionHostileTargetGoal(
                mob,
                true,               // 检查视线（原版逻辑）
                true                // 更新目标（原版逻辑）
        ));
    }
    
    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
    }
}
