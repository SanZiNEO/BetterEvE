package com.example.bettereve.base;

import com.example.bettereve.Config;
import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BaseSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSystem.class);
    private static final String BASE_NBT_KEY = "BetterEvE:BaseFaction";
    private static final String BASE_HEALTH_NBT_KEY = "BetterEvE:BaseHealth";

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity victim = event.getEntity();

        // 检查攻击者和受害者是否都是生物
        if (attacker instanceof LivingEntity && victim instanceof LivingEntity) {
            // 检查是否是同阵营的
            if (FactionHelper.isEntitiesHostile((LivingEntity) attacker, (LivingEntity) victim) == false) {
                // 同阵营的生物不会攻击彼此，取消攻击事件
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity entity = event.getEntity();
        if (isBaseEntity(entity)) {
            // 简化逻辑：所有阵营都可以攻击基地，不进行敌对检查
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (isBaseEntity(entity)) {
            String factionId = getBaseFactionId(entity);
            Faction faction = FactionRegistry.getFaction(factionId);

            LOGGER.debug("基地被摧毁！阵营: {} ({})", faction.getName(), factionId);

            // 播放爆炸特效
            if (!entity.level().isClientSide()) {
                ServerLevel level = (ServerLevel) entity.level();
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        entity.getX(), entity.getY(), entity.getZ(),
                        100, 0.5, 0.5, 0.5, 0.1);
            }

            // 触发胜利事件
            triggerVictory(entity.level(), factionId, entity.blockPosition());
        }
    }

    private static void triggerVictory(Level level, String losingFactionId, BlockPos basePos) {
        // 获取胜利阵营ID（与失败阵营敌对的阵营）
        String winningFactionId = null;
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (!faction.getId().equals(losingFactionId) &&
                FactionRegistry.isHostile(losingFactionId, faction.getId())) {
                winningFactionId = faction.getId();
                break;
            }
        }

        if (winningFactionId != null) {
            Faction winningFaction = FactionRegistry.getFaction(winningFactionId);
            LOGGER.debug("阵营胜利！胜利阵营: {} ({})", winningFaction.getName(), winningFactionId);

            // 显示胜利公告
            showVictoryAnnouncement(level, winningFaction);

            // 清空地图内的所有实体
            clearEntitiesAroundBase(level, basePos);

            // 重置死亡计数器
            DeathCounter counter = DeathCounter.getInstance(level);
            counter.resetDeathCounts(level.dimension().location().toString(), null);
        }
    }

    private static void showVictoryAnnouncement(Level level, Faction winningFaction) {
        // 在服务器端向所有玩家发送公告
        if (level instanceof ServerLevel serverLevel) {
            String message = String.format("§c§l[BetterEvE] §r§a%s 阵营胜利！§r", winningFaction.getName());
            serverLevel.getPlayers(player -> true).forEach(player -> {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
            });
        }
    }

    private static void clearEntitiesAroundBase(Level level, BlockPos basePos) {
        AABB area = new AABB(basePos)
                .inflate(Config.baseClearRadius)
                .move(0, -10, 0)
                .expandTowards(0, 20, 0);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, area);
        entities.forEach(entity -> {
            if (!(entity instanceof Player) && !isBaseEntity(entity)) {
                entity.remove(Entity.RemovalReason.KILLED);
            }
        });

        LOGGER.debug("已清除基地周围 {} 格内的 {} 个实体", Config.baseClearRadius, entities.size());
    }

    public static boolean isBaseEntity(Entity entity) {
        CompoundTag nbt = entity.getPersistentData();
        return entity instanceof EndCrystal && nbt.contains(BASE_NBT_KEY);
    }

    public static String getBaseFactionId(Entity entity) {
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getString(BASE_NBT_KEY);
    }

    public static void setBaseFactionId(Entity entity, String factionId) {
        if (factionId == null) {
            factionId = "neutral";
        }

        CompoundTag nbt = entity.getPersistentData();
        nbt.putString(BASE_NBT_KEY, factionId);
    }

    public static int getBaseHealth(Entity entity) {
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getInt(BASE_HEALTH_NBT_KEY);
    }

    public static void setBaseHealth(Entity entity, int health) {
        CompoundTag nbt = entity.getPersistentData();
        nbt.putInt(BASE_HEALTH_NBT_KEY, health);
    }

    public static void spawnBase(Level level, BlockPos pos, String factionId, int health) {
        if (level instanceof ServerLevel serverLevel) {
            // 在指定位置生成末影水晶作为基地
            EndCrystal crystal = new EndCrystal(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 设置基地的NBT数据
            CompoundTag nbt = new CompoundTag();
            nbt.putString(BASE_NBT_KEY, factionId);
            nbt.putInt(BASE_HEALTH_NBT_KEY, health);
            crystal.getPersistentData().merge(nbt);

            // 生成水晶
            serverLevel.addFreshEntity(crystal);

            LOGGER.debug("基地创建成功！位置: {}, 阵营: {}, 血量: {}", pos, factionId, health);
        }
    }
}