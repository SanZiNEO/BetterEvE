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
            if (mob.tickCount % 2 == 0) {
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
        } else {
            LOGGER.warn("生物 {} 没有 FOLLOW_RANGE 属性", mob.getType().getDescriptionId());
        }
    }

    private static void setupFactionHostileTargetGoal(Mob mob) {
        // 移除原版的寻敌AI，防止同阵营生物相互攻击
        // 对于不同类型的生物，它们的原版寻敌AI可能不同，这里统一移除所有目标选择器
        // 使用反射来访问 private 的 availableGoals 字段
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredField("availableGoals");
            field.setAccessible(true);
            Object goals = field.get(mob.targetSelector);
            
            // 调用 clear() 方法，不管具体类型是什么
            java.lang.reflect.Method clearMethod = goals.getClass().getMethod("clear");
            clearMethod.invoke(goals);
        } catch (Exception e) {
            LOGGER.error("无法清除生物的目标选择器: {}", e.getMessage());
        }
        
        // 为生物添加自定义的阵营敌对目标选择器
        // 优先级设置为1，确保它高于其他目标选择器
        mob.targetSelector.addGoal(1, new FactionHostileTargetGoal(
                mob,
                true,               // 检查视线（原版逻辑）
                true                // 更新目标（原版逻辑）
        ));

        LOGGER.debug("为生物 {} 添加阵营敌对目标选择器", mob.getType().getDescriptionId());
    }

    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
        LOGGER.debug("寻敌系统配置加载完成，默认寻敌范围: {} 格", Config.targetingDefaultFollowRange);
    }
}