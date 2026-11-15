package com.sanzineo.bettereve.phase;
import com.sanzineo.bettereve.Bettereve;
import net.minecraftforge.fml.common.Mod;

import com.sanzineo.bettereve.crystal.CrystalSystem;

import com.sanzineo.bettereve.scenario.Scenario;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)

public class PhaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseManager.class);

    private static String currentPhaseId = null;
    private static Scenario currentScenario = null;
    private static int timerTicks = 0;
    private static boolean isLoaded = false;

    // ==================== 阶段计时（服务器主线程独占） ====================
    /** 服务器 tick 计数（onServerTick END 自增），用于 elapsed 与触发器超时 */
    private static int serverTicks = 0;
    /** 进入当前阶段的 tick 时刻 */
    private static int phaseStartTick = 0;

    public static void loadFromScenario(Scenario scenario) {
        // 新剧本加载：清理上一局残留的延迟指令，防止串场
        CommandExecutor.clearPending();
        // 比分是"每局"的：剧本加载时清零（score 触发器用）
        TriggerEngine.resetScore();
        currentScenario = scenario;
        currentPhaseId = null;
        timerTicks = 0;
        phaseStartTick = serverTicks;
        isLoaded = scenario != null && scenario.getPhases() != null && !scenario.getPhases().isEmpty();
        if (isLoaded) {
            switchToInternal(scenario.getPhases().get(0).getId(), "load", "none");
        }
    }

    /** 手动切换阶段（`/bettereve phase switch`），日志 reason=manual */
    public static void switchTo(String phaseId) {
        switchToInternal(phaseId, "manual", "none");
    }

    /**
     * 切换阶段并打一行结构化日志（douququ 数据台按此行自动记比分）。
     *
     * 日志格式固定：`[bettereve] phase <from> -> <to> winner=<w> reason=<r> elapsed=<n>s`
     * - winner：red / blue / none（只有 faction_wiped 能算出具体阵营）
     * - reason：wiped / timeout / timer / crystal_destroyed / manual / load
     * - elapsed：停留在 from 阶段的整数秒（首次加载 = 0s）
     */
    private static void switchToInternal(String phaseId, String reason, String winner) {
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

        // 幂等：同一阶段重复触发直接忽略（防 timer/trigger/外部指令重复执行副作用）
        if (phaseId.equals(currentPhaseId)) return;

        // 清理上一阶段的延迟指令，防止旧指令串场到新阶段
        CommandExecutor.clearPending();

        String from = currentPhaseId == null ? "-" : currentPhaseId;
        int elapsedSeconds = (serverTicks - phaseStartTick) / 20;
        currentPhaseId = phaseId;
        timerTicks = 0;
        phaseStartTick = serverTicks;
        // 触发器状态是"每阶段"的：计数器清零、锁定复位、采样基线重记
        TriggerEngine.onPhaseEnter(phase);

        LOGGER.info("[bettereve] phase {} -> {} winner={} reason={} elapsed={}s",
                from, phaseId, winner, reason, elapsedSeconds);

        if (phase.getAnnounce() != null) {
            broadcast(phase.getAnnounce().getMessage());
        }
        if (phase.getCommands() != null) {
            CommandExecutor.executeCommands(phase.getCommands());
        }
        if (phase.getSpawnBases() != null) {
            spawnBasesFromPhase(phase);
        }
    }

    public static String getCurrentPhaseId() {
        return currentPhaseId;
    }

    public static Scenario.PhaseDef getCurrentPhase() {
        if (currentScenario == null || currentPhaseId == null || currentScenario.getPhases() == null) return null;
        return currentScenario.getPhases().stream()
                .filter(p -> currentPhaseId.equals(p.getId()))
                .findFirst().orElse(null);
    }

    private static void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        MinecraftForge.EVENT_BUS.post(new PhaseBroadcastEvent(message));
    }

    private static void spawnBasesFromPhase(Scenario.PhaseDef phase) {
        if (phase.getSpawnBases() == null || phase.getSpawnBases().getPositions() == null) return;
        if (currentScenario.getCrystals() == null) return;

        for (Scenario.CrystalTemplateDef template : currentScenario.getCrystals()) {
            if (template.getId().equals(phase.getSpawnBases().getTemplateId())) {
                MinecraftForge.EVENT_BUS.post(new PhaseSpawnBasesEvent(template, phase.getSpawnBases().getPositions()));
                break;
            }
        }
    }

    /** 引擎命中后切阶段（TriggerEngine 调用）；reason = 触发器 type（faction_wiped 记成 wiped） */
    static void fireTrigger(Scenario.TriggerDef trigger, String reason, String winner) {
        if (trigger == null || trigger.getTargetPhase() == null) return;
        switchToInternal(trigger.getTargetPhase(), reason, winner);
    }

    /** 引擎需要的当前 tick 数（elapsed 与轮询节流） */
    static int serverTicks() {
        return serverTicks;
    }

    /** 实体死亡 → 触发器引擎（entity_death / player_death，含组合条件里的子条件） */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        TriggerEngine.onEntityDeath(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (currentScenario == null) return;
        serverTicks++;

        Scenario.PhaseDef current = getCurrentPhase();
        if (current == null || current.getTriggers() == null) return;

        // 1. timer 触发器（阶段级单一计数器，一个阶段只应写一个）
        //    已有"待切换"时冻结：延迟期间不再计时，免得抢在预定切换之前切走
        if (!TriggerEngine.isSwitchPending()) {
            for (Scenario.TriggerDef trigger : current.getTriggers()) {
                if (!"timer".equals(trigger.getType())) continue;
                if (trigger.getTargetPhase() == null) continue;
                if (trigger.getThreshold() > 0) {
                    timerTicks++;
                    if (timerTicks % 100 == 0) {
                        LOGGER.debug("Timer tick: phase={}, timerTicks={}/{}", currentPhaseId, timerTicks, trigger.getThreshold() * 20);
                    }
                    if (timerTicks >= trigger.getThreshold() * 20) {
                        timerTicks = 0;
                        switchToInternal(trigger.getTargetPhase(), "timer", "none");
                        return;
                    }
                }
            }
        }

        // 2. 条件引擎（faction_wiped / entity_state / entity_health / zone / score / command_check / 组合条件）。
        //    真结果优先于超时 —— 同一 tick 同时到期时记条件命中（可能带胜者）而不是 timeout（none）
        String phaseBefore = currentPhaseId;
        TriggerEngine.tick(current, serverTicks);
        if (!phaseBefore.equals(currentPhaseId)) return;
        if (TriggerEngine.isSwitchPending()) return;   // 已预定切换：冻结 timeout

        // 3. 触发器级 timeout：各自计时，进本阶段满 N 秒仍未触发 → 切到该触发器的 targetPhase
        int elapsedSeconds = (serverTicks - phaseStartTick) / 20;
        for (Scenario.TriggerDef trigger : current.getTriggers()) {
            if (trigger.getTimeout() <= 0 || trigger.getTargetPhase() == null) continue;
            if (elapsedSeconds >= trigger.getTimeout()) {
                switchToInternal(trigger.getTargetPhase(), "timeout", "none");
                return;
            }
        }
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    @SubscribeEvent
    public static void onPhaseSpawnBases(PhaseSpawnBasesEvent event) {
        if (event.getTemplate() == null || event.getPositions() == null) return;

        var template = event.getTemplate();
        for (var pos : event.getPositions()) {
            BlockPos blockPos = BlockPos.containing(pos.getX(), pos.getY(), pos.getZ());
            CrystalSystem.spawnCrystal(
                null, blockPos,
                template.getFactionId(), template.getHealth(),
                template.getAttackDamage(), template.getArmor(),
                template.getAttackRange(), template.getAttackCooldown(),
                template.getSizeKey(), template.getModelScale(),
                template.isShowHealthBar()
            );
        }
    }
}
