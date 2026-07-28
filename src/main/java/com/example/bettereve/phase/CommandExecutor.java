package com.example.bettereve.phase;

import com.example.bettereve.base.CommandQueue;
import com.example.bettereve.scenario.Scenario;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 指令执行引擎 — 支持延迟执行的多指令队列。
 * 参考 ImmersiveCinematics 的 keyframe 模式。
 */
public class CommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutor.class);

    /**
     * 执行指令列表，每个指令支持 delay 延迟（tick）。
     */
    public static void executeCommands(List<Scenario.CommandDef> commands) {
        if (commands == null || commands.isEmpty()) return;
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        for (Scenario.CommandDef cmd : commands) {
            if (cmd.getCommand() == null || cmd.getCommand().isEmpty()) continue;
            
            if (cmd.getDelay() > 0) {
                // 带延迟执行
                int delay = cmd.getDelay() * 20; // 秒转 tick
                String command = cmd.getCommand();
                server.execute(() -> {
                    try {
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack(), command);
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute delayed command: {}", command, e);
                    }
                });
            } else {
                // 立即执行
                try {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), cmd.getCommand());
                } catch (Exception e) {
                    LOGGER.error("Failed to execute command: {}", cmd.getCommand(), e);
                }
            }
        }
    }
}
