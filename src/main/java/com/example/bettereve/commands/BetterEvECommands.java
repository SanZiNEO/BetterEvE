package com.example.bettereve.commands;

import com.example.bettereve.capture.ProbeRequests;
import com.example.bettereve.crystal.CrystalSystem;
import com.example.bettereve.crystal.CrystalDeathCounter;
import com.example.bettereve.entitylv.EntityLevelSystem;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.mixin.MobAccessor;
import com.example.bettereve.network.FactionSyncHandler;
import com.example.bettereve.network.CrystalDeathPacket;
import com.example.bettereve.phase.CommandExecutor;
import com.example.bettereve.phase.PhaseManager;
import com.example.bettereve.phase.TriggerEngine;
import com.example.bettereve.scenario.KeepAliveMarker;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioIO;
import com.example.bettereve.scenario.ScenarioManager;
import com.example.bettereve.scoreboard.ScoreboardManager;
import com.example.bettereve.waypoint.WaypointVisualizer;
import com.example.bettereve.faction.PlayerFactionData;
import com.example.bettereve.network.NetworkManager;
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
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.DifficultyInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BetterEvECommands {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/Commands");

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
                .then(Commands.literal("join")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .executes(BetterEvECommands::executeFactionJoin)))
                .then(Commands.literal("setplayer")
                    .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word())
                            .suggests(FACTION_ID_SUGGESTIONS)
                            .requires(source -> source.hasPermission(2))
                            .executes(BetterEvECommands::executeFactionSetPlayer))))
                .then(Commands.literal("clear")
                    .executes(BetterEvECommands::executeFactionClearSelf)
                    .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(BetterEvECommands::executeFactionClearPlayer)))
                .then(Commands.literal("my").executes(BetterEvECommands::executeFactionMy))
            )  // close faction
            .then(Commands.literal("scenario")
                .then(Commands.literal("list").executes(BetterEvECommands::executeScenarioList))
                .then(Commands.literal("load")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(SCENARIO_SUGGESTIONS)
                        .executes(BetterEvECommands::executeScenarioLoad)))
            )  // close scenario
            .then(Commands.literal("phase")
                .then(Commands.literal("list").executes(BetterEvECommands::executePhaseList))
                .then(Commands.literal("switch")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(PHASE_SUGGESTIONS)
                        .executes(BetterEvECommands::executePhaseSwitch)))
            )  // close phase
            .then(Commands.literal("waypoint")
                .then(Commands.literal("visualize")
                    .executes(BetterEvECommands::executeWaypointVisualizeToggle))
            )  // close waypoint
            .then(Commands.literal("crystal")
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
                                                                .executes(BetterEvECommands::executeCreateCrystalOptimized)
                                                                .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                                    .executes(BetterEvECommands::executeCreateCrystalWithHealthBar)
                                                                    .then(Commands.argument("crystalId", StringArgumentType.word())
                                                                        .executes(BetterEvECommands::executeCreateCrystalWithHealthBarAndId)
                                                                    )
                                                                )
                                                                .then(Commands.argument("crystalId", StringArgumentType.word())
                                                                    .executes(BetterEvECommands::executeCreateCrystalOptimizedWithId)
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
                        .executes(BetterEvECommands::executeSpawnCrystalFromTemplate)
                    )
                )
            )  // close crystal
            .then(Commands.literal("spawn")
                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                    .suggests(ENTITY_TYPE_SUGGESTIONS)
                    .then(Commands.argument("factionId", StringArgumentType.word())
                        .suggests(FACTION_ID_SUGGESTIONS)
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 100000))
                            // 上限放宽到 100000：面板逐条等级要能写大值；
                            // 实际等级仍会被剧本等级表的 maxLevel 夹住（EntityLevelSystem.setEntityLevel）
                            // 与原生 /summon 一致：坐标用 Vec3Argument，支持 ~（相对执行位置）与 ^（局部坐标）
                            .then(Commands.argument("pos", Vec3Argument.vec3())
                                // 上限不设：只有下界。一条指令要几只就几只，卡不卡由使用者按场景控制
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                    .executes(BetterEvECommands::executeSpawnEntitiesAtPosition)
                                    // keepAlive：单生物防清除标记。必须注册在 routeId 之前——
                                    // routeId 是 word 参数，会把末尾的 true/false 吞掉，放在它后面等于静默失效。
                                    .then(Commands.argument("keepAlive", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                        // showHealthBar 必须排在 routeId 前面：两者都是"裸 token"时，
                                        // routeId(word) 会把 true/false 抢走（实测 ... 1 true true 会解析成 route="true"）
                                        .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                            .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                            )
                                        )
                                        .then(Commands.argument("routeId", StringArgumentType.word())
                                            .suggests(ROUTE_ID_SUGGESTIONS)
                                            .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                            .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                    .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                                )
                                            )
                                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                            )
                                        )
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(BetterEvECommands::executeSpawnWithKeepAlive)
                                        )
                                    )
                                    .then(Commands.argument("routeId", StringArgumentType.word())
                                        .suggests(ROUTE_ID_SUGGESTIONS)
                                        .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithRoute)
                                        .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                            .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithRouteAndHealthBar)
                                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithRouteAndHealthBarAndNBT)
                                            )
                                        )
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithRouteAndNBT)
                                        )
                                    )
                                    .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithHealthBar)
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithHealthBarAndNBT)
                                        )
                                    )
                                    .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(BetterEvECommands::executeSpawnEntitiesAtPositionWithNBT)
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
                    .then(Commands.literal("crystal")
                        .executes(BetterEvECommands::clearCrystalScoreboard)
                        .then(Commands.argument("factionName", StringArgumentType.greedyString())
                            .suggests(FACTION_NAME_SUGGESTIONS)
                            .executes(BetterEvECommands::clearCrystalFactionScoreboard)
                        )
                    )
                    .then(Commands.argument("factionName", StringArgumentType.greedyString())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(BetterEvECommands::clearFactionScoreboard)
                    )
                )
            )
            .then(Commands.literal("healthbar")
                .then(Commands.literal("reset")
                    .executes(BetterEvECommands::executeResetHealthBarScale)
                )
                .then(Commands.literal("get")
                    .executes(BetterEvECommands::executeGetHealthBarScale)
                )
            )
            .then(Commands.literal("modeldump")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("probe")
                    .then(Commands.argument("entity", ResourceLocationArgument.id())
                        .suggests(ENTITY_TYPE_SUGGESTIONS)
                        .executes(BetterEvECommands::executeModelDumpProbe)
                    )
                )
            )
        );
    }

    /** `/bettereve modeldump probe <entity>` —— 把请求排进队列，由客户端 tick 执行。 */
    private static int executeModelDumpProbe(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "entity");
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            context.getSource().sendFailure(Component.literal("未知实体类型: " + id));
            return 0;
        }
        ProbeRequests.request(id);
        context.getSource().sendSuccess(
                () -> Component.literal("已请求探针: " + id + "（结果见客户端聊天栏 / bettereve-dump/probe/）"), false);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> FACTION_ID_SUGGESTIONS = (context, builder) -> {
        for (Faction faction : FactionRegistry.getAllFactions()) {
            builder.suggest(faction.getId(), Component.literal(faction.getName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> ROUTE_ID_SUGGESTIONS = (context, builder) -> {
        for (com.example.bettereve.waypoint.Route route : com.example.bettereve.waypoint.RouteManager.getAllRoutes()) {
            builder.suggest(route.getId());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SCENARIO_SUGGESTIONS = (context, builder) -> {
        for (String name : ScenarioManager.list()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PHASE_SUGGESTIONS = (context, builder) -> {
        var scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getPhases() != null) {
            for (var phase : scenario.getPhases()) {
                builder.suggest(phase.getId());
            }
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

    private static final SuggestionProvider<CommandSourceStack> SIZE_SUGGESTIONS = (context, builder) -> {
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


    private static final SuggestionProvider<CommandSourceStack> CRYSTAL_TEMPLATE_ID_SUGGESTIONS = (context, builder) -> {
        var scenario = com.example.bettereve.scenario.ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getCrystals() != null) {
            for (var tpl : scenario.getCrystals()) {
                builder.suggest(tpl.getId());
            }
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


    private static int executeSpawnEntitiesAtPosition(CommandContext<CommandSourceStack> context) {
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, null, true);
    }

    private static int executeSpawnEntitiesAtPositionWithHealthBar(CommandContext<CommandSourceStack> context) {
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, null, showHealthBar);
    }

    private static int executeSpawnEntitiesAtPositionWithHealthBarAndNBT(CommandContext<CommandSourceStack> context) {
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");
        CompoundTag nbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, showHealthBar);
    }

    private static int executeSpawnEntitiesAtPositionWithNBT(CommandContext<CommandSourceStack> context) {
        CompoundTag nbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, true);
    }

    /**
     * 带 keepAlive 参数的生成路径。
     *
     * 其余可选参数（routeId / showHealthBar / nbt）用 {@link #optArg} 按"本次解析路径里有没有"读取，
     * 所以 keepAlive 分支下的所有终端节点共用这一个执行器。
     */
    private static int executeSpawnWithKeepAlive(CommandContext<CommandSourceStack> context) {
        CompoundTag nbt = optArg(context, "nbt", CompoundTag.class);
        String routeId = optArg(context, "routeId", String.class);
        Boolean showHealthBar = optArg(context, "showHealthBar", Boolean.class);
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt,
                showHealthBar == null || showHealthBar, routeId,
                Boolean.TRUE.equals(optArg(context, "keepAlive", Boolean.class)));
    }

    /** 读取可选参数：该参数不在本次解析路径里时 Brigadier 抛 IllegalArgumentException，这里返回 null */
    private static <T> T optArg(CommandContext<CommandSourceStack> context, String name, Class<T> type) {
        try {
            return context.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int executeSpawnEntitiesAtPositionWithRoute(CommandContext<CommandSourceStack> context) {
        String routeId = StringArgumentType.getString(context, "routeId");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, null, true, routeId);
    }

    private static int executeSpawnEntitiesAtPositionWithRouteAndHealthBar(CommandContext<CommandSourceStack> context) {
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");
        String routeId = StringArgumentType.getString(context, "routeId");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, null, showHealthBar, routeId);
    }

    private static int executeSpawnEntitiesAtPositionWithRouteAndHealthBarAndNBT(CommandContext<CommandSourceStack> context) {
        boolean showHealthBar = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "showHealthBar");
        CompoundTag nbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        String routeId = StringArgumentType.getString(context, "routeId");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, showHealthBar, routeId);
    }

    private static int executeSpawnEntitiesAtPositionWithRouteAndNBT(CommandContext<CommandSourceStack> context) {
        CompoundTag nbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        String routeId = StringArgumentType.getString(context, "routeId");
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, true, routeId);
    }

    private static int executeSpawnEntitiesAtPositionWithNBTInternal(CommandContext<CommandSourceStack> context, CompoundTag nbt, boolean showHealthBar) {
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, showHealthBar, null);
    }

    private static int executeSpawnEntitiesAtPositionWithNBTInternal(CommandContext<CommandSourceStack> context, CompoundTag nbt, boolean showHealthBar, String routeId) {
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt, showHealthBar, routeId, false);
    }

    private static int executeSpawnEntitiesAtPositionWithNBTInternal(CommandContext<CommandSourceStack> context, CompoundTag nbt, boolean showHealthBar, String routeId, boolean keepAlive) {
        ResourceLocation entityRL = ResourceLocationArgument.getId(context, "entityType");
        String factionId = StringArgumentType.getString(context, "factionId");
        int level = IntegerArgumentType.getInteger(context, "level");
        // 与原生 /summon 一致：Vec3Argument.getVec3 会按执行位置把 ~ / ^ 解析成绝对坐标
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) {
            context.getSource().sendFailure(Component.translatable("commands.summon.outOfWorld"));
            return 0;
        }
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        int count = IntegerArgumentType.getInteger(context, "count");
        
        ServerLevel serverLevel = context.getSource().getLevel();
        
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityRL)) {
            context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL));
            return 0;
        }
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityRL);

        // 阵型偏移：默认圆形（见 Scenario.ScenarioOptions.DEFAULT_SPAWN_FORMATION），
        // 剧本可用 options.spawnLayout 覆盖；count=1 时全部阵型退化为原点。
        List<SpawnFormation.Offset> offsets = SpawnFormation.compute(count, resolveSpawnLayout());

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            try {
                if (entityType != null) {
                    Entity entityObj = entityType.create(serverLevel);
                    if (entityObj instanceof Mob mob) {
                        SpawnFormation.Offset off = offsets.get(i);
                        double px = x + off.x();
                        double pz = z + off.z();
                        mob.setPos(px, y, pz);
                        
                        DifficultyInstance difficulty = serverLevel.getCurrentDifficultyAt(new BlockPos((int)x, (int)y, (int)z));
                        
                        // 直接设置阵营，不调用 finalizeSpawn() 方法
                        FactionHelper.setEntityFactionId(mob, factionId);
                        FactionSyncHandler.syncEntityImmediately(mob);
                        
                        if (level > 0) {
                            EntityLevelSystem.setEntityLevel(mob, level);
                        }
                        
                        // 设置血条可见性
                        FactionHelper.setEntityShowHealthBar(mob, showHealthBar);
                        // 默认显示 Lv 与血量文字（可通过 NBT 单独关闭）
                        FactionHelper.setEntityShowLevelText(mob, true);
                        FactionHelper.setEntityShowHealthText(mob, true);

                        // 指定路线：写入 NBT，PathfindToWaypointGoal 会读取该键
                        if (routeId != null && !routeId.isEmpty()) {
                            mob.getPersistentData().putString("bettereve:route_id", routeId);
                        }

                        // NBT 加载移到 equipment 初始化之前
                        if (nbt != null) {
                            mob.load(nbt);
                            mob.setPos(px, y, pz);
                            // 如果NBT数据中有血条可见性设置，覆盖之前的值
                            if (nbt.contains("BetterEvE:ShowHealthBar")) {
                                showHealthBar = nbt.getBoolean("BetterEvE:ShowHealthBar");
                                FactionHelper.setEntityShowHealthBar(mob, showHealthBar);
                            }
                            if (nbt.contains("BetterEvE:ShowLevelText")) {
                                FactionHelper.setEntityShowLevelText(mob, nbt.getBoolean("BetterEvE:ShowLevelText"));
                            }
                            if (nbt.contains("BetterEvE:ShowHealthText")) {
                                FactionHelper.setEntityShowHealthText(mob, nbt.getBoolean("BetterEvE:ShowHealthText"));
                            }
                            if (nbt.contains("bettereve:route_id")) {
                                routeId = nbt.getString("bettereve:route_id");
                                mob.getPersistentData().putString("bettereve:route_id", routeId);
                            }
                        }
                        
                        MobAccessor mobAccessor = (MobAccessor) mob;
                        mobAccessor.invokePopulateDefaultEquipmentSlots(serverLevel.random, difficulty);
                        mobAccessor.invokePopulateDefaultEquipmentEnchantments(serverLevel.random, difficulty);
                        
                        // 直接调用 finalizeSpawn，不走 Forge 的 MobSpawnEvent.FinalizeSpawn 事件：
                        // 走事件会让 hook 该事件的模组有机会取消生成（实测会把 COMMAND 生成的怪也拦掉，
                        // 变成"生成被拦截"）。原版调用点才会被 Forge 重定向到事件，mod 自己调不会。
                        mob.finalizeSpawn(serverLevel, difficulty, MobSpawnType.COMMAND, null, null);

                        // keepAlive：原版 persistence（整条 despawn 流程直接跳过）+ 模组标记
                        // （AllowDespawn 里再拦一道，且标记可查询/可单独清除）
                        if (keepAlive) {
                            mob.setPersistenceRequired();
                            KeepAliveMarker.mark(mob);
                        }
                        
                        if (mob.getType() == EntityType.WARDEN) {
                            net.minecraft.world.entity.monster.warden.Warden warden = (net.minecraft.world.entity.monster.warden.Warden) mob;
                            
                            // 这里不再手动灌怒气：怒气由 WardenMixin#onGetActiveAnger 维持 ——
                            // 只要「当前目标是敌对阵营实体」，怒气读数就恒为 150（原版上限，
                            // AngerManagement.MAX_ANGER），而 FIGHT 活动的闸门
                            // StopAttackingIfTargetInvalid 读的是「对当前目标的怒气」
                            // （getAngerLevel() → getActiveAnger() → angerManagement.getActiveAnger(getTarget())），
                            // 所以不用（也不能）靠一次性写入撑住：
                            // 原版怒气每 20 tick 衰减 1，写在 ANGRY 阈值 80 上刚好一秒后就掉到 79，
                            // 目标随即被 StopAttackingIfTargetInvalid 摘掉，而且之后没人再点火。
                            // 原先那行 increaseAngerAt(命令执行者, 150, true) 同理没用：怒气是按实体记账的，
                            // 记在执行者头上不参与「对目标」的判定。
                            
                            // 查找敌对目标
                            if (warden.getTarget() == null) {
                                java.util.List<net.minecraft.world.entity.LivingEntity> nearbyEntities = serverLevel.getEntitiesOfClass(
                                    net.minecraft.world.entity.LivingEntity.class, 
                                    mob.getBoundingBox().inflate(50.0D),
                                    targetEntity -> targetEntity != warden && 
                                            targetEntity.isAlive() && 
                                            !targetEntity.isInvulnerable() && 
                                            !targetEntity.isDeadOrDying() && 
                                            FactionHelper.isEntitiesHostile(mob, targetEntity)
                                );
                                
                                if (!nearbyEntities.isEmpty()) {
                                    net.minecraft.world.entity.LivingEntity nearestTarget = null;
                                    double nearestDistance = Double.MAX_VALUE;
                                    
                                    for (net.minecraft.world.entity.LivingEntity targetEntity : nearbyEntities) {
                                        double distance = mob.distanceToSqr(targetEntity);
                                        if (distance < nearestDistance) {
                                            nearestDistance = distance;
                                            nearestTarget = targetEntity;
                                        }
                                    }
                                    
                                    if (nearestTarget != null) {
                                        // setAttackTarget 是官方设目标方法：erase ROAR_TARGET、
                                        // set ATTACK_TARGET、清 CANT_REACH_WALK_TARGET_SINCE、
                                        // 设 sonic boom 冷却。它写的就是 getTarget() 读的那个脑记忆，
                                        // 所以后面不用再补一次 setMemory(ATTACK_TARGET, ...)（同一份写入）。
                                        // 怒气也不再手动灌：目标是敌对阵营时由 WardenMixin 顶到上限。
                                        warden.setAttackTarget(nearestTarget);
                                    }
                                }
                            }
                            
                            // 设置 Warden 的行为状态，确保它处于攻击状态
                            warden.getBrain().setMemoryWithExpiry(
                                net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN, 
                                net.minecraft.util.Unit.INSTANCE, 
                                3000L
                            );
                            
                            // 原来这里还有两处死写入，已删：
                            // 1) ROAR_TARGET：Roar 活动的启动条件要求 ATTACK_TARGET 为空（上面刚设了目标），
                            //    而且 Warden.setAttackTarget 本身就会 erase ROAR_TARGET，写进去也留不住；
                            // 2) NEAREST_ATTACKABLE：监守者 AI 里只有 Sniffing.stop() 读它（嗅探结束时涨怒气），
                            //    对「攻击」没有作用；它本来就是原版 WardenEntitySensor 每 20 tick 重算的记忆，
                            //    手写一个 6000 tick 的过期值会在 20 tick 内被传感器覆盖掉。
                        }
                        
                        // 重要：在将实体添加到世界之前，确保阵营设置正确
                        // 这样 onEntityJoinLevel 事件会正确检测到我们已经设置了阵营
                        // 并且不会尝试继承其他阵营
                        LOGGER.debug("Entity {} will be spawned with faction {}, position {},{},{}", mob.getName(), factionId, x+i, y, z);
                        
                        if (!serverLevel.addFreshEntity(mob)) {
                            context.getSource().sendFailure(Component.literal("生成被拦截（实体未加入世界）: " + entityRL));
                            continue;
                        }
                        spawned++;
                        
                        // 同步血条可见性到所有客户端
                        com.example.bettereve.network.NetworkManager.syncEntityHealthBarVisibilityToAll(mob, showHealthBar);
                        // 同步血条文字显示开关到所有客户端
                        com.example.bettereve.network.NetworkManager.syncEntityHealthBarTextVisibilityToAll(
                                mob,
                                FactionHelper.getEntityShowLevelText(mob),
                                FactionHelper.getEntityShowHealthText(mob));
                    } else {
                        context.getSource().sendFailure(Component.literal("无效的生物类型: " + entityRL + " (必须是可生成的生物)"));
                        return 0;
                    }
                }
            } catch (Exception e) {
                context.getSource().sendFailure(Component.literal("生成生物失败: " + e.getMessage()));
                return 0;
            }
        }
        
        String posString = String.format("%.1f, %.1f, %.1f", x, y, z);
        String routeMsg = (routeId == null || routeId.isEmpty()) ? "Auto" : routeId;
        String formationMsg = count > 1 ? resolveSpawnLayout().getFormation().name().toLowerCase() : "point";
        String message = "Successfully spawned " + spawned + " " + entityRL + " (Faction: " + factionId + ", Level: " + level + ", Health Bar: " + (showHealthBar ? "Visible" : "Hidden") + ", Route: " + routeMsg + ", Formation: " + formationMsg + ") at (" + posString + ")";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    /**
     * 清除所有阵营的计分板数据。
     * HUD 的真实数据源是客户端的 CrystalDeathCounter（由数据包驱动），
     * 因此必须发包通知所有客户端归零，服务器侧的 SavedData 与 HUD 无关。
     */
    /**
     * 取当前指令生效的召唤阵型配置，优先级：
     * 1. 剧本指令自带的 layout（CommandDef.layout，只在该指令执行期间可见）
     * 2. 剧本级 options.spawnLayout
     * 3. 代码内默认值（圆形）
     */
    private static Scenario.SpawnLayout resolveSpawnLayout() {
        Scenario.SpawnLayout commandOverride = CommandExecutor.currentCommandLayout();
        if (commandOverride != null) {
            return commandOverride;
        }
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getOptions() != null && scenario.getOptions().getSpawnLayout() != null) {
            return scenario.getOptions().getSpawnLayout();
        }
        return new Scenario.SpawnLayout();
    }

    private static int clearAllScoreboards(CommandContext<CommandSourceStack> context) {
        // factionId = null → 清除全部阵营的死亡分与击败数
        com.example.bettereve.network.NetworkManager.clearCrystalDeathCounter(null);
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

        com.example.bettereve.network.NetworkManager.clearCrystalDeathCounter(targetFaction.getId());
        TriggerEngine.resetScoreFor(targetFaction.getId());   // score 触发器的服务端累计比分一并清零

        context.getSource().sendSuccess(() ->
            Component.literal("已清除阵营 " + factionName + " 的计分板数据（得分与击败数）"),
            true
        );
        return 1;
    }

    private static int executeResetHealthBarScale(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
            Component.literal("血条缩放已由剧本 options.healthBarScale 控制，请修改剧本后 /bettereve scenario reload"),
            false
        );

        return 1;
    }

    private static int executeGetHealthBarScale(CommandContext<CommandSourceStack> context) {
        float scale = com.example.bettereve.render.SimpleHealthBarRenderer.getGlobalScale();
        context.getSource().sendSuccess(() ->
            Component.literal("当前血条缩放: " + String.format("%.2f", scale) + "x（来自剧本 options.healthBarScale）"),
            false
        );

        return 1;
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
            com.example.bettereve.faction.FactionRegistry.getFaction(factionId).getName() + 
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
            com.example.bettereve.faction.FactionRegistry.getFaction(factionId).getName() +
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
            com.example.bettereve.faction.FactionRegistry.getFaction(factionId).getName() +
            " ID: " + crystalId), true);
        return 1;
    }

    private static int executeSpawnCrystalFromTemplate(CommandContext<CommandSourceStack> context) {
        String crystalId = StringArgumentType.getString(context, "crystalId");
        var scenario = com.example.bettereve.scenario.ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getCrystals() == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("当前未加载剧本或剧本中无水晶模板"));
            return 0;
        }
        com.example.bettereve.scenario.Scenario.CrystalTemplateDef template = null;
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
    
    /**
     * 清除所有基地死亡计数
     */
    private static int clearCrystalScoreboard(CommandContext<CommandSourceStack> context) {
        // 通过网络数据包通知所有客户端清除水晶基地死亡计数
        try {
            // 使用NetworkManager的现有方法清除所有水晶基地死亡计数
            com.example.bettereve.network.NetworkManager.clearCrystalDeathCounts();
            
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
        com.example.bettereve.faction.Faction targetFaction = null;
        for (com.example.bettereve.faction.Faction faction : com.example.bettereve.faction.FactionRegistry.getAllFactions()) {
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

    private static int executeWaypointVisualizeToggle(CommandContext<CommandSourceStack> context) {
        boolean newState = !WaypointVisualizer.isEnabled();
        WaypointVisualizer.setEnabled(newState);
        context.getSource().sendSuccess(() -> 
            Component.literal("路径点可视化已" + (newState ? "启用" : "禁用")), true);
        return 1;
    }
}
