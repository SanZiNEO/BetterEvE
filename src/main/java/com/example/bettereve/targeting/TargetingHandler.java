package com.example.bettereve.targeting;

import com.example.bettereve.ai.FactionHostileTargetGoal;
import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.waypoint.WaypointProgressRegistry;
import com.example.bettereve.mixin.MobAccessor;
import com.example.bettereve.ai.PathfindToWaypointGoal;
import com.example.bettereve.scenario.KeepAliveMarker;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TargetingHandler {

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
        // 单生物防清除标记：与区域判定并列，没配 keepAliveZone 的剧本也生效
        if (KeepAliveMarker.isMarked(mob)) {
            event.setResult(Event.Result.DENY);
            return;
        }
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
        
        // 不再给任何生物补近战攻击 Goal：保留各生物原本的攻击方式，
        // 避免“补近战”误伤原生远程生物（如骷髅只拉弓不射箭）。
        // 仅对 non-neutral 阵营替换导航器并添加兵线寻路。
        if (mob instanceof PathfinderMob pathfinder) {
            // 为有阵营的生物替换导航器（A* 绕开基地）；无阵营（空串）不参与
            String factionId = FactionHelper.getEntityFactionId(mob);
            if (factionId != null && !factionId.isEmpty()) {
                replaceNavigation(pathfinder);
                // 添加寻路 AI（优先级 2，仅在无攻击目标时运行）
                pathfinder.goalSelector.addGoal(2, new PathfindToWaypointGoal(pathfinder));
            }
        }
    }

    // 替换生物的导航器为 FactionGroundNavigator，使 A* 路径绕过基地
    // 使用 Mixin Accessor 访问 private 字段（类型安全、随映射自动适配，替代脆弱反射）
    private static void replaceNavigation(Mob mob) {
        if (mob.getNavigation().getClass() == com.example.bettereve.ai.FactionGroundNavigator.class) return;
        // 原生非地面导航的生物保持原导航（飞行/水生/模组自定义导航器不替换）
        if (!(mob.getNavigation() instanceof GroundPathNavigation)) return;
        ((MobAccessor) mob).setNavigation(new com.example.bettereve.ai.FactionGroundNavigator(mob, mob.level()));
    }
    
    public static void loadFromConfig() {
        // 简化配置加载，只需要确保配置已加载
    }
}
