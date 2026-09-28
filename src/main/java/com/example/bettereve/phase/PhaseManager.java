package com.example.bettereve.phase;
import com.example.bettereve.Bettereve;
import net.minecraftforge.fml.common.Mod;

import com.example.bettereve.crystal.CrystalSystem;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)

public class PhaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseManager.class);

    private static String currentPhaseId = null;
    private static Scenario currentScenario = null;
    private static int timerTicks = 0;
    private static boolean isLoaded = false;

    // ==================== 阶段计时与 faction_wiped 状态（服务器主线程独占） ====================
    /** 服务器 tick 计数（onServerTick END 自增），用于 elapsed 与触发器超时 */
    private static int serverTicks = 0;
    /** 进入当前阶段的 tick 时刻 */
    private static int phaseStartTick = 0;
    /** faction_wiped 扫描节流：每 N tick 统计一次存活数 */
    private static final int FACTION_WIPE_SCAN_INTERVAL = 5;
    /** faction_wiped：上一次扫描时各阵营的存活实体数；阶段进入时清空（第一次扫描只记基线） */
    private static final Map<String, Integer> lastAliveCounts = new HashMap<>();
    /** faction_wiped：本阶段出现过（0 → 有）的阵营；只有它们归零才算"被消灭" */
    private static final Set<String> wipedArmed = new HashSet<>();

    public static void loadFromScenario(Scenario scenario) {
        // 新剧本加载：清理上一局残留的延迟指令，防止串场
        CommandExecutor.clearPending();
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
        lastAliveCounts.clear();
        wipedArmed.clear();

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

    public static void checkTriggers(String type, String factionId, int value) {
        checkTriggers(type, factionId, null, value);
    }

    public static void checkTriggers(String type, String factionId, String crystalDefId, int value) {
        Scenario.PhaseDef current = getCurrentPhase();
        if (current == null || current.getTriggers() == null) return;

        for (Scenario.TriggerDef trigger : current.getTriggers()) {
            if (!type.equals(trigger.getType())) continue;

            boolean triggered = false;
            switch (type) {
                case "base_destroyed":
                    if (factionId.equals(trigger.getFactionId()) || "*".equals(trigger.getFactionId())) {
                        triggered = true;
                    }
                    break;
                case "crystal_destroyed":
                    if (crystalDefId != null && crystalDefId.equals(trigger.getTargetId())) {
                        triggered = true;
                    }
                    break;
                case "entity_death_count":
                    if (factionId.equals(trigger.getFactionId()) && value >= trigger.getThreshold()) {
                        triggered = true;
                    }
                    break;
                case "faction_wiped":
                    // 存活数归零即命中；"本阶段召唤过才算"的武装判定在 checkFactionWipeTriggers 里
                    if (factionId != null && factionId.equals(trigger.getFactionId()) && value <= 0) {
                        triggered = true;
                    }
                    break;
                case "custom":
                    triggered = true;
                    break;
            }

            if (triggered && trigger.getTargetPhase() != null) {
                // 首个命中即切换并跳出（同一 tick 内死多个也只切一次）
                switchToInternal(trigger.getTargetPhase(),
                        "faction_wiped".equals(type) ? "wiped" : type,
                        "faction_wiped".equals(type) ? survivingFaction() : "none");
                break;
            }
        }
    }

    /**
     * 唯一存活阵营 id：恰好一个阵营还有存活实体才算胜者；
     * 双灭 / 多阵营并存 / 一个都没有 → "none"（超时未分胜负也记 none）。
     */
    private static String survivingFaction() {
        Map<String, Integer> counts = countAliveByFaction();
        String survivor = null;
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (counts.getOrDefault(faction.getId(), 0) > 0) {
                if (survivor != null) return "none";
                survivor = faction.getId();
            }
        }
        return survivor != null ? survivor : "none";
    }

    /** 统计各阵营的存活实体数（含玩家与召唤物，只数已加载的实体；无阵营不计） */
    private static Map<String, Integer> countAliveByFaction() {
        Map<String, Integer> counts = new HashMap<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return counts;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                String factionId = FactionHelper.getEntityFactionId(entity);
                if (factionId.isEmpty()) continue;
                counts.merge(factionId, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * faction_wiped 扫描（每 {@link #FACTION_WIPE_SCAN_INTERVAL} tick 一次；阶段没配就零开销）。
     *
     * 语义（plans/complete/03_MULTI_MATCH_SCENARIO.md）：
     * - 只有本阶段内"从 0 涨到有"的阵营，归零时才算被消灭 —— 上一场清场后的空阵营不会立刻触发，
     *   必须等本阶段的召唤落地（0 → 有）之后才算数
     * - 用存活实体数（含召唤物与玩家）而不是死亡计数：被误杀 / 被 despawn / 数量算错都不会提前触发
     * - 首个命中的触发器即切换并跳出（同一 tick 内死多个也只切一次）
     */
    private static void checkFactionWipeTriggers() {
        Scenario.PhaseDef current = getCurrentPhase();
        if (current == null || current.getTriggers() == null) return;

        Set<String> watched = new HashSet<>();
        for (Scenario.TriggerDef trigger : current.getTriggers()) {
            if ("faction_wiped".equals(trigger.getType()) && trigger.getFactionId() != null) {
                watched.add(trigger.getFactionId());
            }
        }
        if (watched.isEmpty()) return;

        Map<String, Integer> counts = countAliveByFaction();
        for (String factionId : watched) {
            int now = counts.getOrDefault(factionId, 0);
            Integer last = lastAliveCounts.put(factionId, now);
            if (last == null) {
                continue;   // 阶段进入后的第一次扫描：只记基线
            }
            if (last == 0 && now > 0) {
                wipedArmed.add(factionId);   // 本阶段召唤过 → 武装
                continue;
            }
            if (last > 0 && now == 0 && wipedArmed.contains(factionId)) {
                checkTriggers("faction_wiped", factionId, 0);
                return;   // 切换后状态已重置，本 tick 不再继续扫
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (currentScenario == null) return;
        serverTicks++;

        Scenario.PhaseDef current = getCurrentPhase();
        if (current == null || current.getTriggers() == null) return;

        // 1. timer 触发器（阶段级单一计数器，一个阶段只应写一个）
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

        // 2. faction_wiped：节流扫描存活数。真结果优先于超时 ——
        //    同一 tick 同时到期时记 wiped（有胜者）而不是 timeout（none）
        if (serverTicks % FACTION_WIPE_SCAN_INTERVAL == 0) {
            String phaseBefore = currentPhaseId;
            checkFactionWipeTriggers();
            if (!phaseBefore.equals(currentPhaseId)) return;
        }

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
