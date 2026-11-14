package com.example.bettereve.commands;

import com.example.bettereve.entitylv.EntityLevelSystem;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.mixin.MobAccessor;
import com.example.bettereve.network.FactionSyncHandler;
import com.example.bettereve.phase.CommandExecutor;
import com.example.bettereve.scenario.KeepAliveMarker;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.commands.arguments.CompoundTagArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.example.bettereve.commands.CommandSuggestions.ENTITY_TYPE_SUGGESTIONS;
import static com.example.bettereve.commands.CommandSuggestions.FACTION_ID_SUGGESTIONS;
import static com.example.bettereve.commands.CommandSuggestions.ROUTE_ID_SUGGESTIONS;

/** `/bettereve spawn ...` 子树：按阵型批量生成带阵营/等级的实体。 */
public final class SpawnCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/Commands");

    private SpawnCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn")
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
                                .executes(SpawnCommands::executeSpawnEntitiesAtPosition)
                                // keepAlive：单生物防清除标记。必须注册在 routeId 之前——
                                // routeId 是 word 参数，会把末尾的 true/false 吞掉，放在它后面等于静默失效。
                                .then(Commands.argument("keepAlive", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                    .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                    // showHealthBar 必须排在 routeId 前面：两者都是"裸 token"时，
                                    // routeId(word) 会把 true/false 抢走（实测 ... 1 true true 会解析成 route="true"）
                                    .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                        )
                                    )
                                    .then(Commands.argument("routeId", StringArgumentType.word())
                                        .suggests(ROUTE_ID_SUGGESTIONS)
                                        .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                        .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                            .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                            .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                            )
                                        )
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                        )
                                    )
                                    .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(SpawnCommands::executeSpawnWithKeepAlive)
                                    )
                                )
                                .then(Commands.argument("routeId", StringArgumentType.word())
                                    .suggests(ROUTE_ID_SUGGESTIONS)
                                    .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithRoute)
                                    .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithRouteAndHealthBar)
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                            .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithRouteAndHealthBarAndNBT)
                                        )
                                    )
                                    .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithRouteAndNBT)
                                    )
                                )
                                .then(Commands.argument("showHealthBar", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                    .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithHealthBar)
                                    .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithHealthBarAndNBT)
                                    )
                                )
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(SpawnCommands::executeSpawnEntitiesAtPositionWithNBT)
                                )
                            )
                        )
                    )
                )
            );
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
     * 其余可选参数（routeId / showHealthBar / nbt）用 {@link CommandArgs#optArg} 按"本次解析路径里有没有"读取，
     * 所以 keepAlive 分支下的所有终端节点共用这一个执行器。
     */
    private static int executeSpawnWithKeepAlive(CommandContext<CommandSourceStack> context) {
        CompoundTag nbt = CommandArgs.optArg(context, "nbt", CompoundTag.class);
        String routeId = CommandArgs.optArg(context, "routeId", String.class);
        Boolean showHealthBar = CommandArgs.optArg(context, "showHealthBar", Boolean.class);
        return executeSpawnEntitiesAtPositionWithNBTInternal(context, nbt,
                showHealthBar == null || showHealthBar, routeId,
                Boolean.TRUE.equals(CommandArgs.optArg(context, "keepAlive", Boolean.class)));
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
}
