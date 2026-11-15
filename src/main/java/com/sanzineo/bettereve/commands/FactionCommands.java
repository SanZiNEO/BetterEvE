package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.faction.Faction;
import com.sanzineo.bettereve.faction.FactionRegistry;
import com.sanzineo.bettereve.faction.PlayerFactionData;
import com.sanzineo.bettereve.network.FactionSyncHandler;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import static com.sanzineo.bettereve.commands.CommandSuggestions.COLOR_SUGGESTIONS;
import static com.sanzineo.bettereve.commands.CommandSuggestions.FACTION_ID_SUGGESTIONS;

/** `/bettereve faction ...` 子树。 */
public final class FactionCommands {

    private FactionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> faction() {
        return Commands.literal("faction")
            .then(Commands.literal("list").executes(FactionCommands::listFactions))
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .then(Commands.argument("color", StringArgumentType.word())
                            .suggests(COLOR_SUGGESTIONS)
                            .executes(FactionCommands::executeCreateFaction)))))
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(FACTION_ID_SUGGESTIONS)
                    .executes(FactionCommands::executeDeleteFaction)))
            .then(Commands.literal("join")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(FACTION_ID_SUGGESTIONS)
                    .executes(FactionCommands::executeFactionJoin)))
            .then(Commands.literal("setplayer")
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .requires(source -> source.hasPermission(2))
                        .executes(FactionCommands::executeFactionSetPlayer))))
            .then(Commands.literal("clear")
                .executes(FactionCommands::executeFactionClearSelf)
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                    .requires(source -> source.hasPermission(2))
                    .executes(FactionCommands::executeFactionClearPlayer)))
            .then(Commands.literal("my").executes(FactionCommands::executeFactionMy));
    }

    private static int executeCreateFaction(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String name = StringArgumentType.getString(context, "name");
        String color = StringArgumentType.getString(context, "color");
        Faction faction = FactionRegistry.createFaction(id, name, color, true);
        if (faction != null) {
            context.getSource().sendSuccess(() -> Component.literal("阵营创建成功: " + faction.getName()), true);
        } else {
            context.getSource().sendFailure(Component.literal("阵营创建失败: 阵营ID已存在"));
        }
        return 1;
    }

    private static int executeDeleteFaction(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        FactionRegistry.deleteFaction(id);
        context.getSource().sendSuccess(() -> Component.literal("阵营删除成功: " + id), true);
        return 1;
    }

    private static int listFactions(CommandContext<CommandSourceStack> context) {
        StringBuilder sb = new StringBuilder("可用阵营列表:");
        for (Faction faction : FactionRegistry.getAllFactions()) {
            sb.append("\n- ").append(faction.getName()).append(" (").append(faction.getId()).append(")");
        }
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeFactionJoin(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "id");
        Faction faction = FactionRegistry.getFaction(factionId);
        if (faction == null) {
            context.getSource().sendFailure(Component.literal("阵营不存在: " + factionId));
            return 0;
        }
        if (!faction.isEnabled()) {
            context.getSource().sendFailure(Component.literal("该阵营未启用: " + faction.getName()));
            return 0;
        }
        Player player = (Player) context.getSource().getEntity();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("只有玩家可以执行此命令"));
            return 0;
        }
        PlayerFactionData.setPlayerFaction(player, factionId);
        context.getSource().sendSuccess(() -> Component.literal("已加入阵营: " + faction.getName()), true);
        return 1;
    }

    private static int executeFactionSetPlayer(CommandContext<CommandSourceStack> context) {
        try {
            net.minecraft.world.entity.player.Player target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            String factionId = StringArgumentType.getString(context, "id");
            Faction faction = FactionRegistry.getFaction(factionId);
            if (faction == null) {
                context.getSource().sendFailure(Component.literal("阵营不存在: " + factionId));
                return 0;
            }
            PlayerFactionData.setPlayerFaction(target, factionId);
            context.getSource().sendSuccess(() -> Component.literal("已将 " + target.getName().getString() + " 设置为阵营: " + faction.getName()), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("设置失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int executeFactionMy(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getEntity();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("只有玩家可以执行此命令"));
            return 0;
        }
        String factionId = PlayerFactionData.getPlayerFaction(player);
        Faction faction = FactionRegistry.getFaction(factionId);
        String name = faction != null ? faction.getName() : factionId;
        context.getSource().sendSuccess(() -> Component.literal("当前阵营: " + name + " (" + factionId + ")"), false);
        return 1;
    }

    /** /bettereve faction clear —— 清除自己的阵营 */
    private static int executeFactionClearSelf(CommandContext<CommandSourceStack> context) {
        Entity sourceEntity = context.getSource().getEntity();
        if (!(sourceEntity instanceof Player player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以清除自己的阵营，管理员可用 /bettereve faction clear <玩家>"));
            return 0;
        }
        return clearFactionFor(context, player);
    }

    /** /bettereve faction clear <玩家> —— 清除指定玩家的阵营（需权限 2） */
    private static int executeFactionClearPlayer(CommandContext<CommandSourceStack> context) {
        Player target;
        try {
            target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("找不到该玩家: " + e.getMessage()));
            return 0;
        }
        return clearFactionFor(context, target);
    }

    /** 共同的清除逻辑：删持久化键 + 立刻同步到所有客户端（否则血条/头顶颜色会留着旧阵营） */
    private static int clearFactionFor(CommandContext<CommandSourceStack> context, Player target) {
        String previous = PlayerFactionData.clearPlayerFaction(target);
        // syncEntityImmediately 内部即 syncEntityFactionToAll，立即推包，
        // 避免客户端还缓存着旧阵营颜色
        FactionSyncHandler.syncEntityImmediately(target);

        String name = target.getName().getString();
        if (previous == null || previous.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.literal(name + " 本来就没有阵营"), false);
        } else {
            final String prev = previous;
            context.getSource().sendSuccess(() ->
                    Component.literal("已清除 " + name + " 的阵营（原为: " + prev + "）"), true);
        }
        return 1;
    }
}
