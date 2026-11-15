package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.faction.Faction;
import com.sanzineo.bettereve.faction.FactionRegistry;
import com.sanzineo.bettereve.network.CrystalDeathPacket;
import com.sanzineo.bettereve.network.NetworkManager;
import com.sanzineo.bettereve.phase.TriggerEngine;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sanzineo.bettereve.commands.CommandSuggestions.FACTION_NAME_SUGGESTIONS;

/** `/bettereve scoreboard ...` 与 `/bettereve healthbar ...` 子树。 */
public final class ScoreboardCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/Commands");

    private ScoreboardCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> scoreboard() {
        return Commands.literal("scoreboard")
            .then(Commands.literal("clear")
                .executes(ScoreboardCommands::clearAllScoreboards)
                .then(Commands.literal("crystal")
                    .executes(ScoreboardCommands::clearCrystalScoreboard)
                    .then(Commands.argument("factionName", StringArgumentType.greedyString())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(ScoreboardCommands::clearCrystalFactionScoreboard)
                    )
                )
                .then(Commands.argument("factionName", StringArgumentType.greedyString())
                    .suggests(FACTION_NAME_SUGGESTIONS)
                    .executes(ScoreboardCommands::clearFactionScoreboard)
                )
            );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> healthbar() {
        return Commands.literal("healthbar")
            .then(Commands.literal("reset")
                .executes(ScoreboardCommands::executeResetHealthBarScale)
            )
            .then(Commands.literal("get")
                .executes(ScoreboardCommands::executeGetHealthBarScale)
            );
    }

    /**
     * 清除所有阵营的计分板数据。
     * HUD 的真实数据源是客户端的 CrystalDeathCounter（由数据包驱动），
     * 因此必须发包通知所有客户端归零，服务器侧的 SavedData 与 HUD 无关。
     */
    private static int clearAllScoreboards(CommandContext<CommandSourceStack> context) {
        // factionId = null → 清除全部阵营的死亡分与击败数
        com.sanzineo.bettereve.network.NetworkManager.clearCrystalDeathCounter(null);
        TriggerEngine.resetScore();   // score 触发器的服务端累计比分一并清零

        context.getSource().sendSuccess(() ->
            Component.literal("已清除所有阵营的计分板数据（得分与击败数）"),
            true
        );
        return 1;
    }

    /**
     * 清除指定阵营的计分板数据（死亡分 + 击败数）。
     */
    private static int clearFactionScoreboard(CommandContext<CommandSourceStack> context) {
        String factionName = StringArgumentType.getString(context, "factionName");

        Faction targetFaction = null;
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (faction.getName().equals(factionName)) {
                targetFaction = faction;
                break;
            }
        }

        if (targetFaction == null) {
            context.getSource().sendFailure(Component.literal("未找到阵营: " + factionName));
            return 0;
        }

        com.sanzineo.bettereve.network.NetworkManager.clearCrystalDeathCounter(targetFaction.getId());
        TriggerEngine.resetScoreFor(targetFaction.getId());   // score 触发器的服务端累计比分一并清零

        context.getSource().sendSuccess(() ->
            Component.literal("已清除阵营 " + factionName + " 的计分板数据（得分与击败数）"),
            true
        );
        return 1;
    }

    /**
     * 清除所有基地死亡计数
     */
    private static int clearCrystalScoreboard(CommandContext<CommandSourceStack> context) {
        // 通过网络数据包通知所有客户端清除水晶基地死亡计数
        try {
            // 使用NetworkManager的现有方法清除所有水晶基地死亡计数
            com.sanzineo.bettereve.network.NetworkManager.clearCrystalDeathCounts();

            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("已发送清除水晶基地死亡计数的指令到所有客户端"), true);
            LOGGER.info("Crystal death counters cleared via command");
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("清除水晶基地死亡计数失败: " + e.getMessage()));
            LOGGER.error("Failed to clear crystal death counters", e);
            return 0;
        }
    }

    /**
     * 清除指定阵营的基地死亡计数
     */
    private static int clearCrystalFactionScoreboard(CommandContext<CommandSourceStack> context) {
        String factionName = StringArgumentType.getString(context, "factionName");
        net.minecraft.server.level.ServerLevel serverLevel = context.getSource().getLevel();

        // 查找对应的阵营
        com.sanzineo.bettereve.faction.Faction targetFaction = null;
        for (com.sanzineo.bettereve.faction.Faction faction : com.sanzineo.bettereve.faction.FactionRegistry.getAllFactions()) {
            if (faction.getName().equals(factionName)) {
                targetFaction = faction;
                break;
            }
        }

        if (targetFaction == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("未找到阵营: " + factionName));
            return 0;
        }

        try {
            // 创建清除指定阵营的数据包
            CrystalDeathPacket packet = new CrystalDeathPacket(targetFaction.getId(), true);

            // 使用NetworkManager的通道发送数据包给所有玩家
            NetworkManager.getChannel().send(
                net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                packet
            );

            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("已发送清除阵营 " + factionName + " 的水晶基地死亡计数的指令到所有客户端"), true);
            LOGGER.info("Crystal death counters for faction {} cleared via command", factionName);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("清除阵营水晶基地死亡计数失败: " + e.getMessage()));
            LOGGER.error("Failed to clear crystal death counters for faction {}", factionName, e);
            return 0;
        }
    }

    private static int executeResetHealthBarScale(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
            Component.literal("血条缩放已由剧本 options.healthBarScale 控制，请修改剧本后 /bettereve scenario reload"),
            false
        );

        return 1;
    }

    private static int executeGetHealthBarScale(CommandContext<CommandSourceStack> context) {
        float scale = com.sanzineo.bettereve.render.SimpleHealthBarRenderer.getGlobalScale();
        context.getSource().sendSuccess(() ->
            Component.literal("当前血条缩放: " + String.format("%.2f", scale) + "x（来自剧本 options.healthBarScale）"),
            false
        );

        return 1;
    }
}
