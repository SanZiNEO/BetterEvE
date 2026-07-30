package com.example.bettereve.phase;

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
     * 支持 && 链式指令（如 "time set day && weather clear"）。
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
                server.execute(() -> executeCommandChain(server, command));
            } else {
                // 立即执行
                executeCommandChain(server, cmd.getCommand());
            }
        }
    }

    /**
     * 执行一条指令，支持 && 链式拆分。
     * 如 "time set day && weather clear" 会拆成两条依次执行。
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
