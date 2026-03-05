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

import com.example.bettereve.mixin.MobAccessor;
import com.example.bettereve.network.HealthBarScalePacket;

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
            .then(Commands.literal("healthbar")
                .then(Commands.literal("scale")
                    .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.1, 3.0))
                        .executes(BetterEvECommands::executeSetHealthBarScale)
                    )
                )
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
                if (entityType != null) {
                    Entity entityObj = entityType.create(serverLevel);
                    if (entityObj instanceof Mob mob) {
                        mob.setPos(x + i, y, z);
                        
                        DifficultyInstance difficulty = serverLevel.getCurrentDifficultyAt(new BlockPos((int)x, (int)y, (int)z));
                        try {
                            mob.finalizeSpawn(serverLevel, difficulty, MobSpawnType.COMMAND, null, null);
                        } catch (Exception e) {
                            context.getSource().sendFailure(Component.literal("初始化生物失败: " + e.getMessage()));
                            continue;
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
                        
                        FactionHelper.setEntityFactionId(mob, factionId);
                        FactionSyncHandler.syncEntityImmediately(mob);
                        
                        if (level > 0) {
                            LevelSystem.setEntityLevel(mob, level);
                        }
                        
                        MobAccessor mobAccessor = (MobAccessor) mob;
                        mobAccessor.invokePopulateDefaultEquipmentSlots(serverLevel.random, difficulty);
                        mobAccessor.invokePopulateDefaultEquipmentEnchantments(serverLevel.random, difficulty);
                        
                        if (nbt != null) {
                            mob.load(nbt);
                            mob.setPos(x + i, y, z);
                        }
                        
                        serverLevel.addFreshEntity(mob);
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
        context.getSource().sendSuccess(() -> 
            Component.literal("成功在 (" + posString + ") 生成 " + 
                             count + " 个 " + entityRL + " (阵营: " + factionId + ", 等级: " + level + ")"), 
            true
        );
        return 1;
    }

    private static int clearAllScoreboards(CommandContext<CommandSourceStack> context) {
        ServerLevel serverLevel = context.getSource().getLevel();
        String worldId = serverLevel.dimension().location().toString();
        
        DeathCounter counter = DeathCounter.getInstance(serverLevel);
        counter.resetDeathCounts(worldId, null);
        
        forceRefreshScoreboard(serverLevel);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("已清除所有阵营的记分板数据并刷新显示"), 
            true
        );
        return 1;
    }

    private static int clearFactionScoreboard(CommandContext<CommandSourceStack> context) {
        String factionName = StringArgumentType.getString(context, "factionName");
        ServerLevel serverLevel = context.getSource().getLevel();
        String worldId = serverLevel.dimension().location().toString();
        
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
        
        DeathCounter counter = DeathCounter.getInstance(serverLevel);
        counter.resetDeathCounts(worldId, targetFaction.getId());
        
        forceRefreshScoreboard(serverLevel);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("已清除阵营 " + factionName + " 的记分板数据并刷新显示"), 
            true
        );
        return 1;
    }

    private static void forceRefreshScoreboard(ServerLevel serverLevel) {
        Scoreboard scoreboard = serverLevel.getScoreboard();
        Objective objective = scoreboard.getObjective("bettereve_deaths");
        
        if (objective != null) {
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
            
            for (Faction faction : FactionRegistry.getAllFactions()) {
                Score score = scoreboard.getOrCreatePlayerScore(faction.getName(), objective);
                score.setScore(0);
            }
            
            updateScoreboardForAllPlayers(serverLevel, scoreboard, objective);
        }
    }

    private static void updateScoreboardForAllPlayers(ServerLevel serverLevel, Scoreboard scoreboard, Objective objective) {
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel) {
                for (Faction faction : FactionRegistry.getAllFactions()) {
                    Score score = scoreboard.getOrCreatePlayerScore(faction.getName(), objective);
                    score.setScore(0);
                }
            }
        }
    }
    
    private static int executeSetHealthBarScale(CommandContext<CommandSourceStack> context) {
        double scale = DoubleArgumentType.getDouble(context, "scale");
        HealthBarScalePacket.sendToAllClients((float) scale);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("血条缩放已设置为: " + String.format("%.1f", scale) + "x"), 
            true
        );
        
        return 1;
    }
    
    private static int executeResetHealthBarScale(CommandContext<CommandSourceStack> context) {
        HealthBarScalePacket.sendResetToAllClients();
        
        context.getSource().sendSuccess(() -> 
            Component.literal("血条缩放已重置为默认值: 1.0x"), 
            true
        );
        
        return 1;
    }
    
    private static int executeGetHealthBarScale(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> 
            Component.literal("当前血条缩放: 1.0x (服务器端无法直接获取客户端渲染状态)"), 
            false
        );
        
        return 1;
    }
}