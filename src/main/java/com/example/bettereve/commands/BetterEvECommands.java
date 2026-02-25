package com.example.bettereve.commands;

import com.example.bettereve.Config;
import com.example.bettereve.base.BaseSystem;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BetterEvECommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterEvECommands.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("bettereve")
            .then(Commands.literal("faction")
                .then(Commands.literal("list").executes(BetterEvECommands::listFactions))
                .then(Commands.literal("create")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .then(Commands.argument("color", StringArgumentType.word())
                                .suggests(COLOR_SUGGESTIONS)
                                .executes(BetterEvECommands::executeCreateFaction)))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .executes(BetterEvECommands::executeDeleteFaction)))
            )
            .then(Commands.literal("base")
                .then(Commands.literal("create")
                    .then(Commands.argument("factionId", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(BetterEvECommands::executeCreateBase)
                                    .then(Commands.argument("health", IntegerArgumentType.integer())
                                        .executes(BetterEvECommands::executeCreateBaseWithHealth)
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("spawn")
                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                    .suggests(ENTITY_TYPE_SUGGESTIONS)
                    .then(Commands.argument("factionId", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                            .executes(BetterEvECommands::executeSpawnEntities)
                        )
                    )
                )
            )
        );
    }

    private static final SuggestionProvider<CommandSourceStack> FACTION_ID_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getId());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> ENTITY_TYPE_SUGGESTIONS = 
        (context, builder) -> SharedSuggestionProvider.suggestResource(
            ForgeRegistries.ENTITY_TYPES.getKeys(), 
            builder
        );

    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (context, builder) -> {
        // 为常见的颜色提供建议（16进制格式）
        String[] commonColors = {
            "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF", "FFFFFF", "000000",
            "FF8800", "88FF00", "00FF88", "0088FF", "8800FF", "FF0088", "888888", "CCCCCC"
        };
        
        for (String color : commonColors) {
            builder.suggest(color);
        }
        
        return builder.buildFuture();
    };

    // === 阵营管理 ===
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

    // === 基地管理 ===
    private static int executeCreateBase(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = context.getSource().getLevel();

        BaseSystem.spawnBase(level, pos, factionId, Config.baseDefaultHealth);
        context.getSource().sendSuccess(() -> Component.literal("基地创建成功: " + FactionRegistry.getFaction(factionId).getName()), true);
        return 1;
    }

    private static int executeCreateBaseWithHealth(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = context.getSource().getLevel();

        BaseSystem.spawnBase(level, pos, factionId, health);
        context.getSource().sendSuccess(() -> Component.literal("基地创建成功: " + FactionRegistry.getFaction(factionId).getName()), true);
        return 1;
    }

    // === 生物生成 ===
    private static int executeSpawnEntities(CommandContext<CommandSourceStack> context) {
        ResourceLocation entityRL = ResourceLocationArgument.getId(context, "entityType");
        String factionId = StringArgumentType.getString(context, "factionId");
        int count = IntegerArgumentType.getInteger(context, "count");
        
        Player player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("该命令必须由玩家执行"));
            return 0;
        }
        
        Level level = player.level();
        BlockPos spawnPos = player.blockPosition().offset(0, 1, 0);
        
        // 验证实体ID是否合法
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityRL)) {
            context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL));
            return 0;
        }
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityRL);
        
        for (int i = 0; i < count; i++) {
            try {
                if (entityType.create(level) instanceof Mob mob) {
                    mob.setPos(spawnPos.getX() + i, spawnPos.getY(), spawnPos.getZ());
                    FactionHelper.setEntityFactionId(mob, factionId);
                    level.addFreshEntity(mob);
                } else {
                    context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL + " (必须是可生成的生物)"));
                    return 0;
                }
            } catch (Exception e) {
                context.getSource().sendFailure(Component.literal("生成生物失败: " + e.getMessage()));
                return 0;
            }
        }
        
        context.getSource().sendSuccess(() -> Component.literal("成功生成 " + count + " 个 " + entityRL + " (阵营: " + factionId + ")"), true);
        return 1;
    }
}