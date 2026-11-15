package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.phase.PhaseManager;
import com.sanzineo.bettereve.scenario.Scenario;
import com.sanzineo.bettereve.scenario.ScenarioManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static com.sanzineo.bettereve.commands.CommandSuggestions.PHASE_SUGGESTIONS;
import static com.sanzineo.bettereve.commands.CommandSuggestions.SCENARIO_SUGGESTIONS;

/** `/bettereve scenario ...` 与 `/bettereve phase ...` 子树。 */
public final class ScenarioCommands {

    private ScenarioCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> scenario() {
        return Commands.literal("scenario")
            .then(Commands.literal("list").executes(ScenarioCommands::executeScenarioList))
            .then(Commands.literal("load")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(SCENARIO_SUGGESTIONS)
                    .executes(ScenarioCommands::executeScenarioLoad)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> phase() {
        return Commands.literal("phase")
            .then(Commands.literal("list").executes(ScenarioCommands::executePhaseList))
            .then(Commands.literal("switch")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(PHASE_SUGGESTIONS)
                    .executes(ScenarioCommands::executePhaseSwitch)));
    }

    private static int executeScenarioList(CommandContext<CommandSourceStack> context) {
        var scenarios = ScenarioManager.list();
        if (scenarios.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("没有找到剧本（检查 config/bettereve/scenarios/ 目录）"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("可用剧本: " + String.join(", ", scenarios)), false);
        }
        return 1;
    }

    private static int executeScenarioLoad(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        try {
            ScenarioManager.load(name, context.getSource().getPlayer());
            context.getSource().sendSuccess(() -> Component.literal("已加载剧本: " + name), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("加载剧本失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int executeScenarioInfo(CommandContext<CommandSourceStack> context) {
        var scenario = ScenarioManager.getActiveScenario();
        if (scenario == null) {
            context.getSource().sendFailure(Component.literal("当前未加载任何剧本"));
        } else {
            context.getSource().sendSuccess(() -> Component.literal("当前剧本: " + scenario.getName() + " (" + scenario.getId() + ")"), false);
            if (scenario.getDescription() != null) {
                context.getSource().sendSuccess(() -> Component.literal(scenario.getDescription()), false);
            }
        }
        return 1;
    }

    private static int executePhaseList(CommandContext<CommandSourceStack> context) {
        var scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getPhases() == null || scenario.getPhases().isEmpty()) {
            context.getSource().sendFailure(Component.literal("当前剧本没有定义任何阶段"));
            return 0;
        }
        var phaseIds = scenario.getPhases().stream().map(Scenario.PhaseDef::getId).collect(java.util.stream.Collectors.joining(", "));
        String current = PhaseManager.getCurrentPhaseId();
        context.getSource().sendSuccess(() ->
            Component.literal("阶段列表: " + phaseIds + " | 当前: " + (current != null ? current : "无")), false);
        return 1;
    }

    private static int executePhaseSwitch(CommandContext<CommandSourceStack> context) {
        String phaseId = StringArgumentType.getString(context, "id");
        PhaseManager.switchTo(phaseId);
        context.getSource().sendSuccess(() -> Component.literal("已切换到阶段: " + phaseId), true);
        return 1;
    }
}
