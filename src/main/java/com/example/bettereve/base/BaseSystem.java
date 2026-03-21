package com.example.bettereve.base;

import com.example.bettereve.Config;
import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.entity.BaseEntity;
import com.example.bettereve.entity.BaseEntityType;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

import java.util.List;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BaseSystem {
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

            // 播放爆炸特效
            if (!entity.level().isClientSide()) {
                ServerLevel level = (ServerLevel) entity.level();
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        entity.getX(), entity.getY(), entity.getZ(),
                        100, 0.5, 0.5, 0.5, 0.1);
            }

            // 不再触发胜利事件和显示公告
            // triggerVictory(entity.level(), factionId, entity.blockPosition());
        }
    }

    /*
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

        List<? extends Entity> entities = level.getEntitiesOfClass(Entity.class, area);
        for (Entity entity : entities) {
            if (!(entity instanceof Player) && !isBaseEntity(entity)) {
                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            }
        }
    }
    */

    public static boolean isBaseEntity(Entity entity) {
        return entity instanceof BaseEntity;
    }

    public static String getBaseFactionId(Entity entity) {
        if (entity instanceof BaseEntity baseEntity) {
            return baseEntity.getFactionId();
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getString(BASE_NBT_KEY);
    }

    public static void setBaseFactionId(Entity entity, String factionId) {
        if (factionId == null) {
            factionId = "neutral";
        }

        if (entity instanceof BaseEntity baseEntity) {
            baseEntity.setFactionId(factionId);
        } else {
            CompoundTag nbt = entity.getPersistentData();
            nbt.putString(BASE_NBT_KEY, factionId);
        }
    }

    public static int getBaseHealth(Entity entity) {
        if (entity instanceof BaseEntity baseEntity) {
            return (int) baseEntity.getMaxHealth();
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getInt(BASE_HEALTH_NBT_KEY);
    }

    public static void setBaseHealth(Entity entity, int health) {
        if (entity instanceof BaseEntity baseEntity) {
            baseEntity.setMaxHealth(health);
        } else {
            CompoundTag nbt = entity.getPersistentData();
            nbt.putInt(BASE_HEALTH_NBT_KEY, health);
        }
    }

    public static void spawnBase(Level level, BlockPos pos, String factionId, int health) {
        spawnBase(level, pos, factionId, health, 100, 10, 10.0, 3.0, "3x10");
    }

    public static void spawnBase(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey) {
        spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, 1.0); // 默认模型缩放为1.0
    }
    
    public static void spawnBase(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale) {
        spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, true); // 默认显示血条
    }
    
    public static void spawnBase(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar) {
        if (level instanceof ServerLevel serverLevel) {
            try {
                // 检查尺寸键值是否有效
                if (!BaseEntityType.isValidSizeKey(sizeKey)) {
                    throw new IllegalArgumentException("Invalid size key: " + sizeKey + ". Valid keys are: " + String.join(", ", BaseEntityType.getAllSizeKeys()));
                }

                // 根据尺寸键值获取对应的实体类型
                EntityType<BaseEntity> entityType = BaseEntityType.getEntityTypeBySize(sizeKey);
                if (entityType == null) {
                    throw new IllegalStateException("BaseEntity type not found for size: " + sizeKey);
                }

                // 在指定位置生成新的 BaseEntity 作为基地
                BaseEntity baseEntity = new BaseEntity(entityType, level);
                baseEntity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                baseEntity.setFactionId(factionId);
                baseEntity.setMaxHealth(health);
                baseEntity.setHealth(health);
                baseEntity.setAttackDamage(attackDamage);
                baseEntity.setArmor(armor);
                baseEntity.setAttackRange(attackRange);
                baseEntity.setAttackCooldown(attackCooldown);
                baseEntity.setModelScale(modelScale);
                baseEntity.setShowHealthBar(showHealthBar); // 设置血条可见性

                // 生成实体
                serverLevel.addFreshEntity(baseEntity);
                System.out.println("BaseEntity spawned successfully at " + pos + " for faction " + factionId + " with size " + sizeKey + ", health bar: " + (showHealthBar ? "visible" : "hidden"));
                
                // 解析尺寸键值，显示尺寸信息
                int[] size = BaseEntityType.parseSizeKey(sizeKey);
                System.out.println("Size details: width=" + size[0] + ", height=" + size[1]);
            } catch (Exception e) {
                System.err.println("Failed to spawn BaseEntity: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取所有可用的基地尺寸键值
     */
    public static String[] getAllBaseSizes() {
        return BaseEntityType.getAllSizeKeys();
    }
    
    /**
     * 检查尺寸键值是否有效
     */
    public static boolean isValidSizeKey(String sizeKey) {
        return BaseEntityType.isValidSizeKey(sizeKey);
    }
    
    /**
     * 根据需求推荐合适的尺寸键值
     * @param width 期望的宽度（1-10）
     * @param height 期望的高度（1-15）
     * @return 尺寸键值，如 "3x10"
     */
    public static String getRecommendedSizeKey(int width, int height) {
        // 确保宽度在有效范围内
        width = Math.max(1, Math.min(10, width));
        // 确保高度在有效范围内
        height = Math.max(1, Math.min(15, height));
        return BaseEntityType.getSizeKey(width, height);
    }
}