package com.sanzineo.bettereve.phase;

import com.sanzineo.bettereve.scenario.Scenario;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sanzineo.bettereve.Bettereve;
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

    // 延迟指令队列：targetTick -> 待执行指令（含各自的阵型覆盖）
    private static final java.util.Map<Integer, java.util.List<PendingCommand>> pendingCommands = new java.util.HashMap<>();

    /** 一条待执行的剧本指令 */
    private record PendingCommand(String command, Scenario.SpawnLayout layout) {}

    /** 正在执行的剧本指令的阵型覆盖（只有 bettereve spawn 读它）；null = 用剧本级 options.spawnLayout */
    private static Scenario.SpawnLayout commandLayout = null;

    // 剧本指令的执行者 = 加载剧本的那个玩家（原版语义：谁敲的谁执行）。
    // 服务端 source 的世界位置在世界原点，`~` 会解析到原点而不是玩家；
    // 换成玩家 source 后，剧本里直接写 `bettereve spawn … ~-10 ~ ~` 就跟玩家自己敲一样。
    private static java.util.UUID sourcePlayerId = null;

    /** 记录执行剧本指令的玩家（加载剧本时调用）。 */
    public static void setSourcePlayer(ServerPlayer player) {
        sourcePlayerId = player == null ? null : player.getUUID();
    }

    /** 每次执行现取玩家 source：位置、朝向、维度都是当下的（玩家一直在动）。 */
    static CommandSourceStack commandSource(MinecraftServer server) {
        if (sourcePlayerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(sourcePlayerId);
            if (player != null) {
                // 剧本指令最高只要求权限 2（gamerule/title 等），加载剧本时玩家已通过该检查
                return player.createCommandSourceStack().withMaximumPermission(2);
            }
        }
        return server.createCommandSourceStack();
    }

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
                    .add(new PendingCommand(cmd.getCommand(), cmd.getLayout()));
            } else {
                executeCommandChain(server, cmd.getCommand(), cmd.getLayout());
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
        java.util.List<PendingCommand> due = pendingCommands.remove(currentTick);
        if (due != null) {
            for (PendingCommand pending : due) {
                executeCommandChain(server, pending.command(), pending.layout());
            }
        }
    }

    /**
     * 清空所有待执行的延迟指令（阶段切换/剧本加载时调用，防止旧阶段/旧剧本的指令串场）。
     */
    public static void clearPending() {
        pendingCommands.clear();
    }

    /**
     * 执行一条指令，支持 && 链式拆分。
     * layout 是这条剧本指令自带的阵型覆盖：只在执行期间可见（bettereve spawn 读它），
     * 执行完立刻还原，不污染后续指令或手动敲的指令。
     */
    private static void executeCommandChain(MinecraftServer server, String command, Scenario.SpawnLayout layout) {
        String[] parts = command.split("\\s*&&\\s*");
        Scenario.SpawnLayout previous = commandLayout;
        commandLayout = layout;
        try {
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    server.getCommands().performPrefixedCommand(commandSource(server), trimmed);
                } catch (Exception e) {
                    LOGGER.error("Failed to execute command: {}", trimmed, e);
                }
            }
        } finally {
            commandLayout = previous;
        }
    }

    /** 当前正在执行的剧本指令的阵型覆盖（bettereve spawn 读取）；null = 没有覆盖 */
    public static Scenario.SpawnLayout currentCommandLayout() {
        return commandLayout;
    }
}
