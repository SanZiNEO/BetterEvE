package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.crystal.CrystalSystem;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import static com.sanzineo.bettereve.commands.CommandSuggestions.CRYSTAL_TEMPLATE_ID_SUGGESTIONS;
import static com.sanzineo.bettereve.commands.CommandSuggestions.FACTION_ID_SUGGESTIONS;
import static com.sanzineo.bettereve.commands.CommandSuggestions.SIZE_SUGGESTIONS;

/** `/bettereve crystal ...` 子树：手工创建水晶基地或按剧本模板生成。 */
public final class CrystalCommands {

    private CrystalCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> crystal() {
        return Commands.literal("crystal")
            .then(Commands.literal("create")
                .then(Commands.argument("factionId", StringArgumentType.word())
                    .suggests(FACTION_ID_SUGGESTIONS)
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("health", IntegerArgumentType.integer(1))
                                    .then(Commands.argument("attackDamage", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("armor", IntegerArgumentType.integer(1))
                                            .then(Commands.argument("attackRange", DoubleArgumentType.doubleArg(1.0))
                                                .then(Commands.argument("attackCycle", DoubleArgumentType.doubleArg(0.05, 20.0))
                                                    .then(Commands.argument("size", StringArgumentType.word())
                                                        .suggests(SIZE_SUGGESTIONS)
                                                        .then(Commands.argument("modelScale", DoubleArgumentType.doubleArg(0.01, 4.0))
                                                            .executes(CrystalCommands::executeCreateCrystalOptimized)
                                                            .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                                .executes(CrystalCommands::executeCreateCrystalWithHealthBar)
                                                                .then(Commands.argument("crystalId", StringArgumentType.word())
                                                                    .executes(CrystalCommands::executeCreateCrystalWithHealthBarAndId)
                                                                )
                                                            )
                                                            .then(Commands.argument("crystalId", StringArgumentType.word())
                                                                .executes(CrystalCommands::executeCreateCrystalOptimizedWithId)
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("spawn")
                .then(Commands.argument("crystalId", StringArgumentType.word())
                    .suggests(CRYSTAL_TEMPLATE_ID_SUGGESTIONS)
                    .executes(CrystalCommands::executeSpawnCrystalFromTemplate)
                )
            );
    }

    private static int executeCreateCrystalOptimized(CommandContext<CommandSourceStack> context) {
        return BetterEvECommandsAdditionalMethods.executeCreateCrystalOptimized(context);
    }

    private static int executeCreateCrystalWithHealthBar(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        int attackDamage = IntegerArgumentType.getInteger(context, "attackDamage");
        int armor = IntegerArgumentType.getInteger(context, "armor");
        double attackRange = DoubleArgumentType.getDouble(context, "attackRange");
        double attackCycle = DoubleArgumentType.getDouble(context, "attackCycle");
        String sizeKey = StringArgumentType.getString(context, "size");
        double modelScale = DoubleArgumentType.getDouble(context, "modelScale");
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");

        // 解析坐标
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);

        // 检查尺寸键值是否有效
        if (!CrystalSystem.isValidSizeKey(sizeKey)) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("无效的尺寸键值: " + sizeKey +
                "，有效的尺寸格式为: 宽度x高度（例如 3x10）"));
            return 0;
        }

        // 检查模型缩放范围
        if (modelScale < 0.01 || modelScale > 4.0) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("模型缩放必须在0.01到4.0之间"));
            return 0;
        }

        // 调用 CrystalSystem
        net.minecraft.server.level.ServerLevel level = context.getSource().getLevel();

        CrystalSystem.spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCycle, sizeKey, modelScale, showHealthBar);

        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("水晶基地创建成功: " +
            com.sanzineo.bettereve.faction.FactionRegistry.getFaction(factionId).getName() +
            " 位置: " + pos.toShortString() +
            " 尺寸: " + sizeKey +
            " 模型缩放: " + String.format("%.2f", modelScale) + "x" +
            " 血条: " + (showHealthBar ? "可见" : "隐藏")), true);
        return 1;
    }

    private static int executeCreateCrystalWithHealthBarAndId(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        int attackDamage = IntegerArgumentType.getInteger(context, "attackDamage");
        int armor = IntegerArgumentType.getInteger(context, "armor");
        double attackRange = DoubleArgumentType.getDouble(context, "attackRange");
        double attackCycle = DoubleArgumentType.getDouble(context, "attackCycle");
        String sizeKey = StringArgumentType.getString(context, "size");
        double modelScale = DoubleArgumentType.getDouble(context, "modelScale");
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");
        String crystalId = StringArgumentType.getString(context, "crystalId");

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        if (!CrystalSystem.isValidSizeKey(sizeKey)) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("无效的尺寸键值: " + sizeKey));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = context.getSource().getLevel();
        CrystalSystem.spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCycle, sizeKey, modelScale, showHealthBar, false, crystalId);
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("水晶基地创建成功: " +
            com.sanzineo.bettereve.faction.FactionRegistry.getFaction(factionId).getName() +
            " ID: " + crystalId), true);
        return 1;
    }

    private static int executeCreateCrystalOptimizedWithId(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        int attackDamage = IntegerArgumentType.getInteger(context, "attackDamage");
        int armor = IntegerArgumentType.getInteger(context, "armor");
        double attackRange = DoubleArgumentType.getDouble(context, "attackRange");
        double attackCycle = DoubleArgumentType.getDouble(context, "attackCycle");
        String sizeKey = StringArgumentType.getString(context, "size");
        double modelScale = DoubleArgumentType.getDouble(context, "modelScale");
        String crystalId = StringArgumentType.getString(context, "crystalId");

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        if (!CrystalSystem.isValidSizeKey(sizeKey)) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("无效的尺寸键值: " + sizeKey));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = context.getSource().getLevel();
        CrystalSystem.spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCycle, sizeKey, modelScale, true, false, crystalId);
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("水晶基地创建成功: " +
            com.sanzineo.bettereve.faction.FactionRegistry.getFaction(factionId).getName() +
            " ID: " + crystalId), true);
        return 1;
    }

    private static int executeSpawnCrystalFromTemplate(CommandContext<CommandSourceStack> context) {
        String crystalId = StringArgumentType.getString(context, "crystalId");
        var scenario = com.sanzineo.bettereve.scenario.ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getCrystals() == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("当前未加载剧本或剧本中无水晶模板"));
            return 0;
        }
        com.sanzineo.bettereve.scenario.Scenario.CrystalTemplateDef template = null;
        for (var t : scenario.getCrystals()) {
            if (crystalId.equals(t.getId())) { template = t; break; }
        }
        if (template == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("未找到水晶模板: " + crystalId));
            return 0;
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
            (int) Math.floor(template.getX()),
            (int) Math.floor(template.getY()),
            (int) Math.floor(template.getZ()));
        String factionId = template.getFactionId();
        if (factionId == null || factionId.isEmpty()) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("水晶模板 " + crystalId + " 未设置阵营"));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = context.getSource().getLevel();
        CrystalSystem.spawnCrystal(level, pos, factionId, template.getHealth(),
            template.getAttackDamage(), template.getArmor(), template.getAttackRange(),
            template.getAttackCooldown(), template.getSizeKey(), template.getModelScale(),
            template.isShowHealthBar(), false, crystalId);
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("水晶已从模板创建: " + crystalId + " (" + factionId + ")"), true);
        return 1;
    }
}
