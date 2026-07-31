package com.example.bettereve.targeting;

import com.example.bettereve.ai.FactionHostileTargetGoal;
import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.waypoint.WaypointProgressRegistry;
import com.example.bettereve.ai.PathfindToWaypointGoal;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
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

    // 实体离开世界（死亡/卸载）时清理其兵线进度，防止全局注册表泄漏
    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            WaypointProgressRegistry.remove(mob);
        }
    }

    // 保活区域内的生物不允许被原版 despawn（直播地图无自然生成，区域内生物全部由模组管理）
    @SubscribeEvent
    public static void onMobDespawn(MobSpawnEvent.AllowDespawn event) {
        Mob mob = event.getEntity();
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getKeepAliveZone() == null) {
            return;
        }
        if (scenario.getKeepAliveZone().contains(mob.getX(), mob.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static void setupExpandedFollowRange(Mob mob) {
        // 常态巡敌距离：剧本 options 优先（随地图设计），未配置时用配置文件默认值
        double followRange = FactionHostileTargetGoal.targetingRange();

        // 使用属性系统设置寻敌范围，这是更稳定的方法
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRange);
        }
    }

    private static void setupFactionHostileTargetGoal(Mob mob) {
        // 基地（CrystalEntity）有自足的 CrystalEntityAttackGoal（自扫+蓄力攻击），
        // 不装 64 格大范围目标选择器，否则 TARGET flag 被抢、基地永远不攻击
        if (mob instanceof CrystalEntity) {
            return;
        }

        // 检查是否已添加过我们的自定义目标选择器，如果是则返回，避免重复添加
        boolean hasCustomGoal = mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(goalEntry -> goalEntry.getGoal() instanceof FactionHostileTargetGoal);
        
        if (hasCustomGoal) {
            return;
        }
        
        // 移除所有原版目标选择器（含 NeutralMob 的 ResetUniversalAngerTargetGoal 等），
        // 只保留我们的 FactionHostileTargetGoal，确保阵营判定完全独占
        mob.targetSelector.removeAllGoals(goal ->
            goal instanceof net.minecraft.world.entity.ai.goal.target.TargetGoal &&
            !(goal instanceof FactionHostileTargetGoal)
        );
        
        // 添加阵营目标选择器（优先级最高）
        mob.targetSelector.addGoal(0, new FactionHostileTargetGoal(mob));
        
        // 添加近战攻击 Goal（如无远程攻击）
        if (mob instanceof PathfinderMob pathfinder) {
            // 完全覆盖原版/模组干扰 AI：移除会与兵线寻路、阵营战斗冲突的移动类 goal
            pathfinder.goalSelector.removeAllGoals(g ->
                g instanceof RandomStrollGoal || g instanceof PanicGoal
                || g instanceof FleeSunGoal || g instanceof RestrictSunGoal
                || g instanceof AvoidEntityGoal || g instanceof TemptGoal
                || g instanceof FollowParentGoal || g instanceof BreedGoal
                || g instanceof EatBlockGoal || g instanceof MoveTowardsRestrictionGoal
                || g instanceof MoveThroughVillageGoal || g instanceof BreakDoorGoal
                || g instanceof OpenDoorGoal || g instanceof InteractGoal
            );
            boolean hasRanged = pathfinder.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof RangedAttackGoal);
            boolean hasMelee = pathfinder.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal);
            if (!hasRanged && !hasMelee) {
                pathfinder.goalSelector.addGoal(1, new MeleeAttackGoal(pathfinder, 1.0, false));
            }
            // 为非中立阵营生物替换导航器（A* 绕开基地）
            String factionId = FactionHelper.getEntityFactionId(mob);
            if (!"neutral".equals(factionId)) {
                replaceNavigation(pathfinder);
                // 添加寻路 AI（优先级 2，低于近战攻击 Goal，仅在无攻击目标时运行）
                pathfinder.goalSelector.addGoal(2, new PathfindToWaypointGoal(pathfinder));
            }
        }
    }

    // 替换生物的导航器为 FactionGroundNavigator，使 A* 路径绕过基地
    private static void replaceNavigation(Mob mob) {
        if (mob.getNavigation().getClass() == com.example.bettereve.ai.FactionGroundNavigator.class) return;
        try {
            java.lang.reflect.Field navField = net.minecraft.world.entity.Mob.class.getDeclaredField("navigation");
            navField.setAccessible(true);
            navField.set(mob, new com.example.bettereve.ai.FactionGroundNavigator(mob, mob.level()));
        } catch (Exception e) {
            LOGGER.error("Failed to replace navigation for {}", mob, e);
        }
    }
    
    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
    }
}
