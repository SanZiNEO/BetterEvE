package com.example.bettereve.commands;

import com.example.bettereve.crystal.CrystalSystem;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.scenario.ScenarioManager;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** `/bettereve` 命令树使用的全部 {@link SuggestionProvider}。 */
public final class CommandSuggestions {

    private CommandSuggestions() {
    }

    public static final SuggestionProvider<CommandSourceStack> FACTION_ID_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getId(), Component.literal(faction.getName()));
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> ROUTE_ID_SUGGESTIONS = (context, builder) -> {
        for (com.example.bettereve.waypoint.Route route : com.example.bettereve.waypoint.RouteManager.getAllRoutes()) {
            builder.suggest(route.getId());
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> SCENARIO_SUGGESTIONS = (context, builder) -> {
        for (String name : ScenarioManager.list()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> PHASE_SUGGESTIONS = (context, builder) -> {
        var scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getPhases() != null) {
            for (var phase : scenario.getPhases()) {
                builder.suggest(phase.getId());
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> FACTION_NAME_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getName(), Component.literal("ID: " + faction.getId()));
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> ENTITY_TYPE_SUGGESTIONS =
        (context, builder) -> {
            for (ResourceLocation entityRL : ForgeRegistries.ENTITY_TYPES.getKeys()) {
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityRL);
                if (entityType != null) {
                    builder.suggest(entityRL.toString(), Component.translatable(entityType.getDescriptionId()));
                }
            }
            return builder.buildFuture();
        };

    public static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (context, builder) -> {
        java.util.Map<String, String> colorNames = new java.util.HashMap<>();
        colorNames.put("FF0000", "红色");
        colorNames.put("00FF00", "绿色");
        colorNames.put("0000FF", "蓝色");
        colorNames.put("FFFF00", "黄色");
        colorNames.put("FF00FF", "品红色");
        colorNames.put("00FFFF", "青色");
        colorNames.put("FFFFFF", "白色");
        colorNames.put("000000", "黑色");
        colorNames.put("FF8800", "橙色");
        colorNames.put("88FF00", "黄绿色");
        colorNames.put("00FF88", "青绿色");
        colorNames.put("0088FF", "浅蓝色");
        colorNames.put("8800FF", "紫色");
        colorNames.put("FF0088", "粉红色");
        colorNames.put("888888", "灰色");
        colorNames.put("CCCCCC", "浅灰色");

        for (java.util.Map.Entry<String, String> entry : colorNames.entrySet()) {
            builder.suggest(entry.getKey(), Component.literal(entry.getValue()));
        }

        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> SIZE_SUGGESTIONS = (context, builder) -> {
        // 获取所有可用的尺寸键值
        String[] sizeKeys = CrystalSystem.getAllCrystalSizes();

        // 添加常见的尺寸建议
        String[] commonSizes = {
            "3x10",   // 默认尺寸
            "5x15",   // 中等尺寸
            "10x15",  // 大尺寸
            "1x5",    // 小尺寸
            "8x12",   // 宽尺寸
            "3x20"    // 高尺寸
        };

        // 首先添加常见尺寸
        for (String commonSize : commonSizes) {
            String[] parts = commonSize.split("x");
            if (parts.length == 2) {
                builder.suggest(commonSize, Component.literal("宽度=" + parts[0] + "，高度=" + parts[1]));
            }
        }

        // 然后添加所有可用的尺寸键值
        for (String sizeKey : sizeKeys) {
            String[] parts = sizeKey.split("x");
            if (parts.length == 2) {
                builder.suggest(sizeKey, Component.literal("宽度=" + parts[0] + "，高度=" + parts[1]));
            }
        }

        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> CRYSTAL_TEMPLATE_ID_SUGGESTIONS = (context, builder) -> {
        var scenario = com.example.bettereve.scenario.ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getCrystals() != null) {
            for (var tpl : scenario.getCrystals()) {
                builder.suggest(tpl.getId());
            }
        }
        return builder.buildFuture();
    };
}
