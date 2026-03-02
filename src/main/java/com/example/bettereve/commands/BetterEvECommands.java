package com.example.bettereve.commands;

import com.example.bettereve.Config;
import com.example.bettereve.base.BaseSystem;
import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.level.LevelSystem;
import com.example.bettereve.network.FactionSyncHandler;
import com.example.bettereve.scoreboard.ScoreboardManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BetterEvECommands {

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
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 500))
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                            .executes(BetterEvECommands::executeSpawnEntitiesAtPosition)
                                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithNBT)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("scoreboard")
                .then(Commands.literal("clear")
                    .executes(BetterEvECommands::clearAllScoreboards)
                    .then(Commands.argument("factionName", StringArgumentType.greedyString())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(BetterEvECommands::clearFactionScoreboard)
                    )
                )
            )
        );
    }

    private static final SuggestionProvider<CommandSourceStack> FACTION_ID_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getId(), Component.literal(faction.getName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> FACTION_NAME_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getName(), Component.literal("ID: " + faction.getId()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> ENTITY_TYPE_SUGGESTIONS = 
        (context, builder) -> {
            for (ResourceLocation entityRL : ForgeRegistries.ENTITY_TYPES.getKeys()) {
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityRL);
                if (entityType != null) {
                    builder.suggest(entityRL.toString(), Component.translatable(entityType.getDescriptionId()));
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (context, builder) -> {
        // 创建颜色名称映射，提供更好的用户体验
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

    private static int executeSpawnEntitiesAtPosition(CommandContext<CommandSourceStack> context) {
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, null);
    }

    private static int executeSpawnEntitiesAtPositionWithNBT(CommandContext<CommandSourceStack> context) {
        CompoundTag nbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt);
    }

    private static int executeSpawnEntitiesAtPositionWithNBTInternal(CommandContext<CommandSourceStack> context, CompoundTag nbt) {
        ResourceLocation entityRL = ResourceLocationArgument.getId(context, "entityType");
        String factionId = StringArgumentType.getString(context, "factionId");
        int level = IntegerArgumentType.getInteger(context, "level");
        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");
        int count = IntegerArgumentType.getInteger(context, "count");
        
        ServerLevel serverLevel = context.getSource().getLevel();
        
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityRL)) {
            context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL));
            return 0;
        }
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityRL);
        
        for (int i = 0; i < count; i++) {
            try {
                if (entityType.create(serverLevel) instanceof Mob mob) {
                    mob.setPos(x + i, y, z);
                    FactionHelper.setEntityFactionId(mob, factionId);
                    
                    FactionSyncHandler.syncEntityImmediately(mob);
                    
                    if (nbt != null) {
                        mob.load(nbt);
                    }
                    
                    if (level > 0) {
                        LevelSystem.setEntityLevel(mob, level);
                    }
                    
                    serverLevel.addFreshEntity(mob);
                } else {
                    context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL + " (必须是可生成的生物)"));
                    return 0;
                }
            } catch (Exception e) {
                context.getSource().sendFailure(Component.literal("生成生物失败: " + e.getMessage()));
                return 0;
            }
        }
        
        String posString = String.format("%.1f, %.1f, %.1f", x, y, z);
        context.getSource().sendSuccess(() -> 
            Component.literal("成功在 (" + posString + ") 生成 " + 
                             count + " 个 " + entityRL + " (阵营: " + factionId + ", 等级: " + level + ")"), 
            true
        );
        return 1;
    }

    private static int clearAllScoreboards(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        String worldId = level.dimension().location().toString();
        
        DeathCounter counter = DeathCounter.getInstance(level);
        counter.resetDeathCounts(worldId, null);
        
        forceRefreshScoreboard(level);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("已清除所有阵营的记分板数据并刷新显示"), 
            true
        );
        return 1;
    }

    private static int clearFactionScoreboard(CommandContext<CommandSourceStack> context) {
        String factionName = StringArgumentType.getString(context, "factionName");
        ServerLevel level = context.getSource().getLevel();
        String worldId = level.dimension().location().toString();
        
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
        
        DeathCounter counter = DeathCounter.getInstance(level);
        counter.resetDeathCounts(worldId, targetFaction.getId());
        
        forceRefreshScoreboard(level);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("已清除阵营 " + factionName + " 的记分板数据并刷新显示"), 
            true
        );
        return 1;
    }

    private static void forceRefreshScoreboard(ServerLevel level) {
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective("bettereve_deaths");
        
        if (objective != null) {
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
            
            for (Faction faction : FactionRegistry.getAllFactions()) {
                Score score = scoreboard.getOrCreatePlayerScore(faction.getName(), objective);
                score.setScore(0);
            }
            
            updateScoreboardForAllPlayers(level, scoreboard, objective);
        }
    }

    private static void updateScoreboardForAllPlayers(ServerLevel level, Scoreboard scoreboard, Objective objective) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level) {
                for (Faction faction : FactionRegistry.getAllFactions()) {
                    Score score = scoreboard.getOrCreatePlayerScore(faction.getName(), objective);
                    score.setScore(0);
                }
            }
        }
    }
}