package com.example.bettereve.base;

import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.entity.BaseEntity;
import com.example.bettereve.entity.BaseEntityType;
import com.example.bettereve.phase.PhaseManager;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.network.NetworkManager;
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
        Entity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker == null) return;
        
        String attackerFaction = FactionHelper.getEntityFactionId(attacker);
        if (attackerFaction == null) return;
        
        if (victim instanceof BaseEntity) {
            NetworkManager.sendBaseDeathToAll(attackerFaction);
            
            ServerLevel level = (ServerLevel) victim.level();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    victim.getX(), victim.getY(), victim.getZ(),
                    100, 0.5, 0.5, 0.5, 0.1);
            
            PhaseManager.checkTriggers("base_destroyed", attackerFaction, 0);
            
        } else {
            String victimFaction = FactionHelper.getEntityFactionId(victim);
            if (!attackerFaction.equals(victimFaction)) {
                NetworkManager.sendEntityKillToAll(attackerFaction);
            }
        }
    }
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
        spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, false); // 默认不触发自动清场
    }
    
    public static void spawnBase(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear) {
        spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, enableDeathClear, false, false); // 默认不播报，不加入计数
    }
    
    public static void spawnBase(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear, boolean enableBroadcast, boolean enableDeathCount) {
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
                System.out.println("BaseEntity spawned successfully at " + pos + " for faction " + factionId + " with size " + sizeKey + 
                    ", health bar: " + (showHealthBar ? "visible" : "hidden") + 
                    ", death clear: " + (enableDeathClear ? "enabled" : "disabled") +
                    ", broadcast: " + (enableBroadcast ? "enabled" : "disabled") +
                    ", death count: " + (enableDeathCount ? "enabled" : "disabled"));
                
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