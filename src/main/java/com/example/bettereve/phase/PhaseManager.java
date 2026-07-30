package com.example.bettereve.phase;
import com.example.bettereve.Bettereve;
import net.minecraftforge.fml.common.Mod;

import com.example.bettereve.crystal.CrystalSystem;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
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

    public static void loadFromScenario(Scenario scenario) {
        currentScenario = scenario;
        currentPhaseId = null;
        timerTicks = 0;
        isLoaded = scenario != null && scenario.getPhases() != null && !scenario.getPhases().isEmpty();
        if (isLoaded) {
            switchTo(scenario.getPhases().get(0).getId());
        }
    }

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
        timerTicks = 0;
        LOGGER.info("Switched to phase: {}", phaseId);

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
                case "custom":
                    triggered = true;
                    break;
            }

            if (triggered && trigger.getTargetPhase() != null) {
                LOGGER.info("Trigger fired: {} (targetId={}) -> phase {}", trigger.getType(), trigger.getTargetId(), trigger.getTargetPhase());
                switchTo(trigger.getTargetPhase());
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (currentScenario == null) return;

        Scenario.PhaseDef current = getCurrentPhase();
        if (current != null && current.getTriggers() != null) {
            for (Scenario.TriggerDef trigger : current.getTriggers()) {
                if (!"timer".equals(trigger.getType())) continue;
                if (trigger.getTargetPhase() == null) continue;
                if (trigger.getThreshold() > 0) {
                    timerTicks++;
                    if (timerTicks % 100 == 0) {
                        LOGGER.debug("Timer tick: phase={}, timerTicks={}/{}", currentPhaseId, timerTicks, trigger.getThreshold() * 20);
                    }
                    if (timerTicks >= trigger.getThreshold() * 20) {
                        LOGGER.info("Timer trigger fired: -> phase {}", trigger.getTargetPhase());
                        timerTicks = 0;
                        switchTo(trigger.getTargetPhase());
                        break;
                    }
                }
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
