package com.example.bettereve.crystal;

import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.entity.CrystalEntityType;
import com.example.bettereve.phase.PhaseManager;
import com.example.bettereve.phase.TriggerEngine;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.faction.FactionRelationManager;
import com.example.bettereve.faction.SummonRule;
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
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("BetterEvE/CrystalSystem");

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity victim = event.getEntity();

        // 检查攻击者和受害者是否都是生物
        if (attacker instanceof LivingEntity && victim instanceof LivingEntity) {
            // 攻击者一侧取"有效阵营"：投掷物自己还没继承到阵营时，回退到发射者的阵营
            // （见 FactionHelper.getEffectiveFactionId）。这一层是非 Projectile 攻击实体
            // （projectile_slash 这类 Mob）唯一的通用拦截点 —— 它们的命中判定写在模组自己
            // 的代码里（实测咒术回战 RangeAttackProcedure 直接调 victim.hurt(DamageSource(type, self), dmg)），
            // vanilla 没有 canHitEntity 那样的谓词可以挂。
            String attackerFaction = FactionHelper.getEffectiveFactionId(attacker);
            String victimFaction = FactionHelper.getEntityFactionId(victim);

            // 同阵营的生物不会攻击彼此，取消攻击事件
            if (FactionRelationManager.isHostile(attackerFaction, victimFaction) == false) {
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

        // 格斗血条死亡通知：与计分板链路独立，不受攻击者/计分开关影响。
        // 只要死亡实体带阵营就通知客户端，供其判断主血条绑定是否失效。
        if (!FactionHelper.getEntityFactionId(victim).isEmpty()) {
            NetworkManager.sendVersusBarDeathToAll(victim.getId());
        }

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
            TriggerEngine.onCrystalDestroyed(attackerFaction, crystalDefId.isEmpty() ? null : crystalDefId);
            
        } else if (SummonRule.countsForScoreboard(victim)) {
            // 召唤物不计分：被击杀的若是召唤物则跳过累加。
            // 开关见剧本 options.summon.countsForScore（默认 false）。
            String victimFaction = FactionHelper.getEntityFactionId(victim);
            if (!attackerFaction.equals(victimFaction)) {
                NetworkManager.sendEntityKillToAll(attackerFaction);
                TriggerEngine.onEntityKill(attackerFaction);   // score 触发器用（服务端累计比分）
            }
        } else {
            // 诊断：多部件生物按"整体结算"，这里能看出是哪一部分、被谁打死的。
            // 用于确认长条 boss 的"最后一节死亡"实际由哪个实体触发。
            LOGGER.info("[计分跳过] victim={} uuid={} 是多部件部件={} 是多部件主体={} 召唤物={} 攻击者={} 位置=({}, {}, {})",
                    victim.getType(), victim.getUUID(),
                    com.example.bettereve.faction.SummonedMarker.isMultipartPiece(victim),
                    com.example.bettereve.faction.SummonedMarker.isMultipartRoot(victim),
                    com.example.bettereve.faction.SummonedMarker.isSummoned(victim),
                    attacker.getType(),
                    String.format("%.1f", victim.getX()),
                    String.format("%.1f", victim.getY()),
                    String.format("%.1f", victim.getZ()));
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
            factionId = "";
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
