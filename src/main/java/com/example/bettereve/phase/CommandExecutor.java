package com.example.bettereve.phase;

import com.example.bettereve.scenario.Scenario;
import net.minecraft.server.MinecraftServer;
import com.example.bettereve.Bettereve;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 指令执行引擎 — 支持延迟执行的多指令队列。
 * 参考 ImmersiveCinematics 的 keyframe 模式。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutor.class);

    // 延迟指令队列：targetTick -> List<command>
    private static final java.util.Map<Integer, java.util.List<String>> pendingCommands = new java.util.HashMap<>();

    /**
     * 执行指令列表，每个指令支持 delay 延迟（秒）。
     * 支持 && 链式指令（如 "time set day && weather clear"）。
     */
    public static void executeCommands(List<Scenario.CommandDef> commands) {
        if (commands == null || commands.isEmpty()) return;
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        for (Scenario.CommandDef cmd : commands) {
            if (cmd.getCommand() == null || cmd.getCommand().isEmpty()) continue;
            
            if (cmd.getDelay() > 0) {
                int targetTick = server.getTickCount() + cmd.getDelay() * 20;
                pendingCommands.computeIfAbsent(targetTick, k -> new java.util.ArrayList<>())
                    .add(cmd.getCommand());
            } else {
                executeCommandChain(server, cmd.getCommand());
            }
        }
    }

    /**
     * 每 tick 检查是否有到期的延迟指令并执行。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        int currentTick = server.getTickCount();
        java.util.List<String> due = pendingCommands.remove(currentTick);
        if (due != null) {
            for (String command : due) {
                executeCommandChain(server, command);
            }
        }
    }

    /**
     * 执行一条指令，支持 && 链式拆分。
     */
    private static void executeCommandChain(MinecraftServer server, String command) {
        String[] parts = command.split("\\s*&&\\s*");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), trimmed);
            } catch (Exception e) {
                LOGGER.error("Failed to execute command: {}", trimmed, e);
            }
        }
    }
}
