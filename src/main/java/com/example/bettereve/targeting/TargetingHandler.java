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

    // 监听生物的 tick 事件，每2Tick刷新一次目标
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            // 每2Tick刷新一次目标，强化寻敌积极性
            if (mob.tickCount % 20 == 0) {
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
        // 优化方案：完全清除所有目标选择器，只保留我们的自定义目标选择器
        // 这样可以确保我们的阵营判定完全生效，同时保留生物的基本AI行为
        
        // 检查是否已添加过我们的自定义目标选择器，如果是则返回，避免重复添加
        boolean hasCustomGoal = false;
        
        // 首先检查是否已包含我们的自定义目标选择器
        try {
            // 使用 Java Stream API 检查是否已存在自定义目标选择器
            hasCustomGoal = mob.targetSelector.getAvailableGoals().stream()
                    .anyMatch(goalEntry -> goalEntry.getGoal() instanceof FactionHostileTargetGoal);
        } catch (Exception e) {
            LOGGER.debug("Failed to check for custom goal:", e);
        }
        
        if (hasCustomGoal) {
            return;
        }
        
        // 暴力清除 targetSelector 中的所有目标选择器 - 这是确保完全控制的最有效方法
        try {
            // 使用反射调用 removeAllGoals 方法，彻底清除所有目标选择器
            java.lang.reflect.Method removeAllGoalsMethod = net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredMethod("removeAllGoals", java.util.function.Predicate.class);
            removeAllGoalsMethod.setAccessible(true);
            removeAllGoalsMethod.invoke(mob.targetSelector, (java.util.function.Predicate<net.minecraft.world.entity.ai.goal.Goal>) goal -> true);
            LOGGER.debug("Cleared all target goals from mob: " + mob.getName().getString());
        } catch (Exception e) {
            LOGGER.debug("Failed to clear target goals using removeAllGoals method:", e);
            
            // 如果 removeAllGoals 方法不可用（可能是旧版本），尝试使用反射直接清空
            try {
                java.lang.reflect.Field field = net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredField("availableGoals");
                field.setAccessible(true);
                
                Object goals = field.get(mob.targetSelector);
                if (goals != null) {
                    // 如果 goals 是 Collection 类型，直接清空
                    if (goals instanceof java.util.Collection) {
                        ((java.util.Collection<?>) goals).clear();
                        LOGGER.debug("Cleared all target goals from mob (Collection method): " + mob.getName().getString());
                    } else if (goals instanceof Iterable) {
                        // 如果是其他 Iterable 类型，尝试获取其所属类并查找清空方法
                        java.lang.reflect.Method clearMethod = goals.getClass().getMethod("clear");
                        clearMethod.invoke(goals);
                        LOGGER.debug("Cleared all target goals from mob (clear method): " + mob.getName().getString());
                    }
                }
            } catch (Exception e2) {
                LOGGER.debug("Failed to clear target goals using reflection:", e2);
            }
        }
        
        // 为生物添加自定义的阵营敌对目标选择器
        // 优先级设置为1，确保它高于其他可能存在的目标选择器
        mob.targetSelector.addGoal(1, new FactionHostileTargetGoal(
                mob,
                true,               // 检查视线（原版逻辑）
                true                // 更新目标（原版逻辑）
        ));
        
        LOGGER.debug("Added custom faction hostile target goal to mob: " + mob.getName().getString());
    }
    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
    }
}