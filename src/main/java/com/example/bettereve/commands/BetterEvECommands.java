package com.example.bettereve.commands;

import com.example.bettereve.crystal.CrystalSystem;
import com.example.bettereve.crystal.CrystalDeathCounter;
import com.example.bettereve.entitylv.EntityLevelSystem;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.mixin.MobAccessor;
import com.example.bettereve.network.FactionSyncHandler;
import com.example.bettereve.network.CrystalDeathPacket;
import com.example.bettereve.phase.PhaseManager;
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
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 500))
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
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
        );
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

        // 阵型偏移：默认圆形（见 Scenario.ScenarioOptions.DEFAULT_SPAWN_FORMATION），
        // 剧本可用 options.spawnLayout 覆盖；count=1 时全部阵型退化为原点。
        List<SpawnFormation.Offset> offsets = SpawnFormation.compute(count, resolveSpawnLayout());

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
                        
                        // 调用 finalizeSpawn 以确保生物正确初始化
                        mob.finalizeSpawn(serverLevel, difficulty, MobSpawnType.COMMAND, null, null);

                        // keepAlive：原版 persistence（整条 despawn 流程直接跳过）+ 模组标记
                        // （AllowDespawn 里再拦一道，且标记可查询/可单独清除）
                        if (keepAlive) {
                            mob.setPersistenceRequired();
                            KeepAliveMarker.mark(mob);
                        }
                        
                        if (mob.getType() == EntityType.WARDEN) {
                            net.minecraft.world.entity.monster.warden.Warden warden = (net.minecraft.world.entity.monster.warden.Warden) mob;
                            
                            // 设置最大愤怒值，确保 Warden 进入完全愤怒状态
                            warden.increaseAngerAt(context.getSource().getEntity(), 150, true);
                            
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
                                        // 同时设置传统目标和 Brain 系统的 ATTACK_TARGET
                                        warden.setAttackTarget(nearestTarget);
                                        warden.getBrain().setMemory(
                                            net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, 
                                            nearestTarget
                                        );
                                        warden.increaseAngerAt(nearestTarget, 80, false);
                                    }
                                }
                            }
                            
                            // 设置 Warden 的行为状态，确保它处于攻击状态
                            warden.getBrain().setMemoryWithExpiry(
                                net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN, 
                                net.minecraft.util.Unit.INSTANCE, 
                                3000L
                            );
                            
                            if (warden.getTarget() != null) {
                                warden.getBrain().setMemory(
                                    net.minecraft.world.entity.ai.memory.MemoryModuleType.ROAR_TARGET, 
                                    warden.getTarget()
                                );
                                // 确保 Warden 对目标有足够的记忆
                                warden.getBrain().setMemoryWithExpiry(
                                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE, 
                                    warden.getTarget(), 
                                    6000L
                                );
                            }
                        }
                        
                        // 重要：在将实体添加到世界之前，确保阵营设置正确
                        // 这样 onEntityJoinLevel 事件会正确检测到我们已经设置了阵营
                        // 并且不会尝试继承其他阵营
                        LOGGER.debug("Entity {} will be spawned with faction {}, position {},{},{}", mob.getName(), factionId, x+i, y, z);
                        
                        serverLevel.addFreshEntity(mob);
                        
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
        String message = "Successfully spawned " + count + " " + entityRL + " (Faction: " + factionId + ", Level: " + level + ", Health Bar: " + (showHealthBar ? "Visible" : "Hidden") + ", Route: " + routeMsg + ", Formation: " + formationMsg + ") at (" + posString + ")";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    /**
     * 清除所有阵营的计分板数据。
     * HUD 的真实数据源是客户端的 CrystalDeathCounter（由数据包驱动），
     * 因此必须发包通知所有客户端归零，服务器侧的 SavedData 与 HUD 无关。
     */
    /**
     * 取当前剧本的召唤阵型配置。
     * 未加载剧本、或剧本未写 options.spawnLayout 时，返回代码内默认值（圆形）。
     */
    private static Scenario.SpawnLayout resolveSpawnLayout() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getOptions() != null && scenario.getOptions().getSpawnLayout() != null) {
            return scenario.getOptions().getSpawnLayout();
        }
        return new Scenario.SpawnLayout();
    }

    private static int clearAllScoreboards(CommandContext<CommandSourceStack> context) {
        // factionId = null → 清除全部阵营的死亡分与击败数
        com.example.bettereve.network.NetworkManager.clearCrystalDeathCounter(null);

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
            ScenarioManager.load(name);
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
