package com.example.bettereve.phase;

import com.example.bettereve.base.CommandQueue;
import com.example.bettereve.base.BaseSystem;
import net.minecraft.core.BlockPos;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 阶段管理器 — 根据 Scenario JSON 的 phases 定义驱动游戏流程。
 * 
 * 典型流程：
 * game_start → [实体推进] → red_tower_1_down → [推进] → game_over → game_start（循环）
 */
public class PhaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseManager.class);
    
    private static String currentPhaseId = null;
    private static Scenario currentScenario = null;
    private static int resetTickCounter = 0;
    private static boolean isLoaded = false;

    /**
     * 从剧本加载阶段配置。
     */
    public static void loadFromScenario(Scenario scenario) {
        currentScenario = scenario;
        currentPhaseId = null;
        isLoaded = scenario != null && scenario.getPhases() != null && !scenario.getPhases().isEmpty();
        if (isLoaded) {
            // 默认进入第一个阶段
            switchTo(scenario.getPhases().get(0).getId());
        }
    }

    /**
     * 切换到指定阶段。
     */
    public static void switchTo(String phaseId) {
        if (currentScenario == null || currentScenario.getPhases() == null) {
            LOGGER.warn("Cannot switch phase: no scenario loaded");
            return;
        }
        
        Scenario.PhaseDef phase = currentScenario.getPhases().stream()
                .filter(p -> phaseId.equals(p.getId()))
                .findFirst().orElse(null);
        
        if (phase == null) {
            LOGGER.warn("Phase not found: {}", phaseId);
            return;
        }
        
        currentPhaseId = phaseId;
        LOGGER.info("Switched to phase: {}", phaseId);
        
        // 执行播报
        if (phase.getAnnounce() != null) {
            broadcast(phase.getAnnounce().getMessage());
        }
        
        // 执行指令
        if (phase.getCommands() != null) {
            CommandExecutor.executeCommands(phase.getCommands());
        }
        
        // 生成建筑
        if (phase.getSpawnBases() != null) {
            spawnBasesFromPhase(phase);
        }
        
        // 设置重置倒计时
        if (phase.getResetAfter() > 0) {
            resetTickCounter = phase.getResetAfter() * 20; // 秒转 tick
        }
    }

    /**
     * 获取当前阶段 ID。
     */
    public static String getCurrentPhaseId() {
        return currentPhaseId;
    }

    /**
     * 获取当前阶段的定义。
     */
    public static Scenario.PhaseDef getCurrentPhase() {
        if (currentScenario == null || currentPhaseId == null || currentScenario.getPhases() == null) return null;
        return currentScenario.getPhases().stream()
                .filter(p -> currentPhaseId.equals(p.getId()))
                .findFirst().orElse(null);
    }

    /**
     * 广播消息到所有玩家。
     */
    private static void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        MinecraftForge.EVENT_BUS.post(new PhaseBroadcastEvent(message));
    }

    /**
     * 从阶段配置生成建筑。
     */
    private static void spawnBasesFromPhase(Scenario.PhaseDef phase) {
        if (phase.getSpawnBases() == null || phase.getSpawnBases().getPositions() == null) return;
        // 建筑生成由 BaseSystem.spawnBase 执行，需要找到匹配的模板
        // 简化实现：遍历 bases 模板用第一个匹配的
        if (currentScenario.getBases() == null) return;
        
        for (Scenario.BaseTemplateDef template : currentScenario.getBases()) {
            if (template.getId().equals(phase.getSpawnBases().getTemplateId())) {
                // 在指定位置生成建筑
                // spawnBase 需要 Level 和 BlockPos，此处使用通知机制
                MinecraftForge.EVENT_BUS.post(new PhaseSpawnBasesEvent(template, phase.getSpawnBases().getPositions()));
                break;
            }
        }
    }

    /**
     * 服务器 tick 事件，处理阶段重置计时。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (resetTickCounter <= 0 || currentScenario == null) return;
        
        resetTickCounter--;
        if (resetTickCounter <= 0 && currentScenario.getPhases() != null && !currentScenario.getPhases().isEmpty()) {
            // 重置到第一个阶段
            switchTo(currentScenario.getPhases().get(0).getId());
        }
    }

    /**
     * 检查是否已加载阶段配置。
     */
    public static boolean isLoaded() {
        return isLoaded;
    }

    @SubscribeEvent
    public static void onPhaseSpawnBases(PhaseSpawnBasesEvent event) {
        if (event.getTemplate() == null || event.getPositions() == null) return;
        
        var template = event.getTemplate();
        for (var pos : event.getPositions()) {
            BlockPos blockPos = BlockPos.containing(pos.getX(), pos.getY(), pos.getZ());
            BaseSystem.spawnBase(
                null, blockPos,
                template.getFactionId(), template.getHealth(),
                template.getAttackDamage(), template.getArmor(),
                template.getAttackRange(), template.getAttackCooldown(),
                template.getSizeKey(), template.getModelScale(),
                template.isShowHealthBar(), template.isEnableDeathClear(),
                template.isEnableBroadcast(), template.isEnableDeathCount()
            );
        }
    }
}
