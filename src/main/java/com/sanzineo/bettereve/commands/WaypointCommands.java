package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.waypoint.WaypointVisualizer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** `/bettereve waypoint ...` 子树。 */
public final class WaypointCommands {

    private WaypointCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> waypoint() {
        return Commands.literal("waypoint")
            .then(Commands.literal("visualize")
                .executes(WaypointCommands::executeWaypointVisualizeToggle));
    }

    private static int executeWaypointVisualizeToggle(CommandContext<CommandSourceStack> context) {
        boolean newState = !WaypointVisualizer.isEnabled();
        WaypointVisualizer.setEnabled(newState);
        context.getSource().sendSuccess(() ->
            Component.literal("路径点可视化已" + (newState ? "启用" : "禁用")), true);
        return 1;
    }
}
