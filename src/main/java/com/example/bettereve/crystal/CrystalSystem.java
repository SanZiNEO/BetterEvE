package com.example.bettereve.crystal;

import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.entity.CrystalEntityType;
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
public class CrystalSystem {
    private static final String CRYSTAL_NBT_KEY = "BetterEvE:CrystalFaction";
    private static final String CRYSTAL_HEALTH_NBT_KEY = "BetterEvE:CrystalHealth";

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
        if (isCrystalEntity(entity)) {
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
        
        if (victim instanceof CrystalEntity crystal) {
            NetworkManager.sendCrystalDeathToAll(attackerFaction);
            
            ServerLevel level = (ServerLevel) victim.level();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    victim.getX(), victim.getY(), victim.getZ(),
                    100, 0.5, 0.5, 0.5, 0.1);
            
            String crystalDefId = crystal.getCrystalDefId();
            PhaseManager.checkTriggers("crystal_destroyed", attackerFaction, crystalDefId.isEmpty() ? null : crystalDefId, 0);
            
        } else {
            String victimFaction = FactionHelper.getEntityFactionId(victim);
            if (!attackerFaction.equals(victimFaction)) {
                NetworkManager.sendEntityKillToAll(attackerFaction);
            }
        }
    }
    public static boolean isCrystalEntity(Entity entity) {
        return entity instanceof CrystalEntity;
    }

    public static String getCrystalFactionId(Entity entity) {
        if (entity instanceof CrystalEntity crystalEntity) {
            return crystalEntity.getFactionId();
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getString(CRYSTAL_NBT_KEY);
    }

    public static void setCrystalFactionId(Entity entity, String factionId) {
        if (factionId == null) {
            factionId = "neutral";
        }

        if (entity instanceof CrystalEntity crystalEntity) {
            crystalEntity.setFactionId(factionId);
        } else {
            CompoundTag nbt = entity.getPersistentData();
            nbt.putString(CRYSTAL_NBT_KEY, factionId);
        }
    }

    public static int getCrystalHealth(Entity entity) {
        if (entity instanceof CrystalEntity crystalEntity) {
            return (int) crystalEntity.getMaxHealth();
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getInt(CRYSTAL_HEALTH_NBT_KEY);
    }

    public static void setCrystalHealth(Entity entity, int health) {
        if (entity instanceof CrystalEntity crystalEntity) {
            crystalEntity.setMaxHealth(health);
        } else {
            CompoundTag nbt = entity.getPersistentData();
            nbt.putInt(CRYSTAL_HEALTH_NBT_KEY, health);
        }
    }

    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health) {
        spawnCrystal(level, pos, factionId, health, 100, 10, 10.0, 3.0, "3x10");
    }

    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, 1.0);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, true);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, false);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, enableDeathClear, null);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear, @org.jetbrains.annotations.Nullable String crystalDefId) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, enableDeathClear, false, false, crystalDefId);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear, boolean enableBroadcast, boolean enableDeathCount) {
        spawnCrystal(level, pos, factionId, health, attackDamage, armor, attackRange, attackCooldown, sizeKey, modelScale, showHealthBar, enableDeathClear, enableBroadcast, enableDeathCount, null);
    }
    
    public static void spawnCrystal(Level level, BlockPos pos, String factionId, int health, int attackDamage, int armor, double attackRange, double attackCooldown, String sizeKey, double modelScale, boolean showHealthBar, boolean enableDeathClear, boolean enableBroadcast, boolean enableDeathCount, @org.jetbrains.annotations.Nullable String crystalDefId) {
        if (level instanceof ServerLevel serverLevel) {
            try {
                if (!CrystalEntityType.isValidSizeKey(sizeKey)) {
                    throw new IllegalArgumentException("Invalid size key: " + sizeKey + ". Valid keys are: " + String.join(", ", CrystalEntityType.getAllSizeKeys()));
                }

                EntityType<CrystalEntity> entityType = CrystalEntityType.getEntityTypeBySize(sizeKey);
                if (entityType == null) {
                    throw new IllegalStateException("CrystalEntity type not found for size: " + sizeKey);
                }

                CrystalEntity crystalEntity = new CrystalEntity(entityType, level);
                crystalEntity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                crystalEntity.setFactionId(factionId);
                crystalEntity.setMaxHealth(health);
                crystalEntity.setHealth(health);
                crystalEntity.setAttackDamage(attackDamage);
                crystalEntity.setArmor(armor);
                crystalEntity.setAttackRange(attackRange);
                crystalEntity.setAttackCooldown(attackCooldown);
                crystalEntity.setModelScale(modelScale);
                crystalEntity.setShowHealthBar(showHealthBar);
                if (crystalDefId != null) {
                    crystalEntity.setCrystalDefId(crystalDefId);
                }

                serverLevel.addFreshEntity(crystalEntity);

                // 注册碰撞箱，让 A* 寻路绕过基地
                int[] sz = CrystalEntityType.parseSizeKey(sizeKey);
                CrystalCollisionRegistry.register(
                    crystalDefId != null ? crystalDefId : factionId,
                    factionId, pos.getX() + 0.5, pos.getZ() + 0.5, sz[0], sz[1]);
                System.out.println("CrystalEntity spawned successfully at " + pos + " for faction " + factionId + " with size " + sizeKey +
                    ", health bar: " + (showHealthBar ? "visible" : "hidden") +
                    ", death clear: " + (enableDeathClear ? "enabled" : "disabled") +
                    ", broadcast: " + (enableBroadcast ? "enabled" : "disabled") +
                    ", death count: " + (enableDeathCount ? "enabled" : "disabled"));

                int[] size = CrystalEntityType.parseSizeKey(sizeKey);
                System.out.println("Size details: width=" + size[0] + ", height=" + size[1]);
            } catch (Exception e) {
                System.err.println("Failed to spawn CrystalEntity: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取所有可用的基地尺寸键值
     */
    public static String[] getAllCrystalSizes() {
        return CrystalEntityType.getAllSizeKeys();
    }
    
    /**
     * 检查尺寸键值是否有效
     */
    public static boolean isValidSizeKey(String sizeKey) {
        return CrystalEntityType.isValidSizeKey(sizeKey);
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
        return CrystalEntityType.getSizeKey(width, height);
    }
}
