package com.example.bettereve.entitylv;

import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityLevelSystem {
    private static final Map<UUID, Integer> entityLevels = new HashMap<>();
    private static final String NBT_LEVEL_KEY = "BetterEvE_Level";

    /**
     * 获取实体等级（支持所有实体类型，包括投掷物）
     */
    public static int getEntityLevel(Entity entity) {
        // 首先尝试从 NBT 中获取等级（持久性存储）
        if (entity.getPersistentData().contains(NBT_LEVEL_KEY)) {
            return entity.getPersistentData().getInt(NBT_LEVEL_KEY);
        }
        // 如果 NBT 中没有，再从内存中获取
        return entityLevels.getOrDefault(entity.getUUID(), 0);
    }

    /**
     * 获取生物等级（保持兼容性）
     */
    public static int getEntityLevel(LivingEntity entity) {
        return getEntityLevel((Entity) entity);
    }

    /**
     * 设置实体等级（支持所有实体类型，包括投掷物）
     */
    public static void setEntityLevel(Entity entity, int level) {
        if (level < 0) level = 0;
        Scenario.EntityLevelConfig levelCfg = getEntityLevelConfig(entity);
        int maxLevel = levelCfg != null ? levelCfg.getMaxLevel() : BetterEvEConfig.INSTANCE.entitylv_maxLevel.get();
        if (level > maxLevel) level = maxLevel;
        
        // 同时存储到内存和 NBT 中，确保持久性
        entityLevels.put(entity.getUUID(), level);
        entity.getPersistentData().putInt(NBT_LEVEL_KEY, level);
        
        // 如果是生物，应用属性加成
        if (entity instanceof LivingEntity) {
            applySpawnLevel((LivingEntity) entity, level);
        } else if (entity instanceof AbstractArrow) {
            // 为投掷物应用属性加成
            applyProjectileLevel((AbstractArrow) entity, level);
        }
        
        // 同步到客户端
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            com.example.bettereve.network.NetworkManager.syncEntityLevelToAll(entity, level);
        }
    }

    /**
     * 设置生物等级（保持兼容性）
     */
    public static void setEntityLevel(LivingEntity entity, int level) {
        setEntityLevel((Entity) entity, level);
    }

    /**
     * 从 NBT 中读取等级信息（实体加载时调用）
     */
    public static void loadLevelFromNBT(Entity entity) {
        if (entity.getPersistentData().contains(NBT_LEVEL_KEY)) {
            int level = entity.getPersistentData().getInt(NBT_LEVEL_KEY);
            entityLevels.put(entity.getUUID(), level);
            
            // 如果是投掷物，加载时应用属性
            if (entity instanceof AbstractArrow) {
                applyProjectileLevel((AbstractArrow) entity, level);
            }
        }
    }

    /**
     * 从 NBT 中读取等级信息（保持兼容性）
     */
    public static void loadLevelFromNBT(LivingEntity entity) {
        loadLevelFromNBT((Entity) entity);
    }

    /**
     * 保存等级信息到 NBT（实体保存时调用）
     */
    public static void saveLevelToNBT(Entity entity) {
        if (entityLevels.containsKey(entity.getUUID())) {
            entity.getPersistentData().putInt(NBT_LEVEL_KEY, entityLevels.get(entity.getUUID()));
        }
    }

    /**
     * 保存等级信息到 NBT（保持兼容性）
     */
    public static void saveLevelToNBT(LivingEntity entity) {
        saveLevelToNBT((Entity) entity);
    }

    /**
     * 清理实体等级数据（实体离开世界时调用）
     */
    public static void removeEntityLevel(Entity entity) {
        entityLevels.remove(entity.getUUID());
        entity.getPersistentData().remove(NBT_LEVEL_KEY);
    }

    /**
     * 清理实体等级数据（保持兼容性）
     */
    public static void removeEntityLevel(LivingEntity entity) {
        removeEntityLevel((Entity) entity);
    }

    /**
     * 应用召唤等级属性（非线性增长，支持NBT属性突破上限）
     */
    /**
     * 获取实体的等级配置（优先从剧本读取，fallback 到全局配置）。
     */
    private static Scenario.EntityLevelConfig getEntityLevelConfig(Entity entity) {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getFactions() == null || entity == null) return null;
        String factionId = com.example.bettereve.faction.FactionHelper.getEntityFactionId(entity);
        for (Scenario.FactionDef faction : scenario.getFactions()) {
            if (faction.getId().equals(factionId)) {
                return faction.getEntityLevelConfig();
            }
        }
        return null;
    }
    public static void applySpawnLevel(LivingEntity entity, int level) {
        if (level <= 0) return;

        // 检查实体是否有NBT属性（用于判断是否可以突破上限）
        boolean hasNbtAttributes = hasCustomNbtAttributes(entity);

        Scenario.EntityLevelConfig levelCfg = getEntityLevelConfig(entity);
        double healthBonus = (levelCfg != null ? levelCfg.getHealthBonus() : BetterEvEConfig.INSTANCE.entitylv_healthBonus.get()) / 100;
        double attackBonus = (levelCfg != null ? levelCfg.getAttackBonus() : BetterEvEConfig.INSTANCE.entitylv_attackBonus.get()) / 100;
        int maxLevel = levelCfg != null ? levelCfg.getMaxLevel() : BetterEvEConfig.INSTANCE.entitylv_maxLevel.get();

        // 生命值
        if (entity.getAttribute(Attributes.MAX_HEALTH) != null) {
            double healthMultiplier = 1 + healthBonus * Math.pow(level, 1.4);
            double maxHealth = hasNbtAttributes ? Double.MAX_VALUE : 200.0; // NBT属性突破上限
            double finalHealth = Math.min(healthMultiplier, maxHealth);
            double baseHealth = entity.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            double newHealth = baseHealth * finalHealth;
            entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
            
            if (entity.getHealth() == baseHealth) {
                entity.setHealth((float)newHealth);
            } else {
                if (entity.getHealth() > newHealth) {
                    entity.setHealth((float)newHealth);
                }
            }
        }

        // 攻击力
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double attackMultiplier = 1 + attackBonus * Math.pow(level, 1.42);
            double maxAttack = hasNbtAttributes ? Double.MAX_VALUE : 200.0; // NBT属性突破上限
            double finalAttack = Math.min(attackMultiplier, maxAttack);
            double baseDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
            double newDamage = baseDamage * finalAttack;
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
        }

        // 护甲值
        if (entity.getAttribute(Attributes.ARMOR) != null) {
            double armorMultiplier = 1 + 0.07 * Math.pow(level, 0.9);
            double maxArmor = hasNbtAttributes ? Double.MAX_VALUE : 2.0; // NBT属性突破上限
            double finalArmor = Math.min(armorMultiplier, maxArmor);
            double baseArmor = entity.getAttribute(Attributes.ARMOR).getBaseValue();
            double newArmor = baseArmor * finalArmor;
            entity.getAttribute(Attributes.ARMOR).setBaseValue(newArmor);
        }

        // 击退抗性
        if (entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            double knockbackResistance = 0.012 * Math.pow(level, 0.95);
            double maxKnockback = hasNbtAttributes ? Double.MAX_VALUE : 1.0; // NBT属性突破上限
            double finalKnockback = Math.min(knockbackResistance, maxKnockback);
            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(finalKnockback);
        }
    }

    /**
     * 应用投掷物等级属性
     */
    public static void applyProjectileLevel(AbstractArrow projectile, int level) {
        if (level <= 0) return;

        // 获取配置的属性加成百分比
        double attackBonus = BetterEvEConfig.INSTANCE.entitylv_attackBonus.get() / 100;
        
        // 伤害加成（与 EntityLevelSystem 相同的非线性增长公式）
        double damageMultiplier = 1 + attackBonus * Math.pow(level, 1.42);
        double maxDamage = 200.0; // 限制最大伤害倍数为 200 倍
        double finalDamage = Math.min(damageMultiplier, maxDamage);
        
        // 设置新的基础伤害
        double currentDamage = projectile.getBaseDamage();
        double newDamage = currentDamage * finalDamage;
        projectile.setBaseDamage(newDamage);
        
        // 穿透加成（非线性增长，限制最大值为 20 倍穿透）
        double pierceBonus = 0.05 * Math.pow(level, 1.2);
        double maxPierce = 20.0; // 限制最大穿透倍数为 20 倍
        double finalPierce = Math.min(pierceBonus, maxPierce);
        
        // 设置新的穿透等级（确保至少为 0）
        byte currentPierce = projectile.getPierceLevel();
        byte newPierce = (byte) Math.max((int) finalPierce, currentPierce);
        projectile.setPierceLevel(newPierce);
    }

    /**
     * 检查实体是否有自定义NBT属性
     */
    private static boolean hasCustomNbtAttributes(LivingEntity entity) {
        // 对于通过 BetterEvE 命令召唤的实体，默认允许突破属性上限
        // 因为我们需要支持高等级生物的超高属性
        return true;
    }

    /**
     * 获取等级数值表（用于显示，根据配置动态生成）
     */
    public static String getLevelTable() {
        int maxLevel = BetterEvEConfig.INSTANCE.entitylv_maxLevel.get();
        StringBuilder sb = new StringBuilder(String.format("等级属性表 (0-%d级):\n", maxLevel));
        sb.append("等级 | 生命值 | 攻击力 | 护甲 | 击退抗性\n");
        sb.append("-----|--------|--------|------|----------\n");
        
        // 计算显示的等级间隔
        int step = 1;
        if (maxLevel > 100) step = maxLevel / 10;
        else if (maxLevel > 50) step = 10;
        else if (maxLevel > 20) step = 5;
        
        // 添加0级到最大级的属性表
        for (int level = 0; level <= maxLevel; level += step) {
            // 计算属性加成
            double healthBonus = BetterEvEConfig.INSTANCE.entitylv_healthBonus.get() / 100;
            double attackBonus = BetterEvEConfig.INSTANCE.entitylv_attackBonus.get() / 100;
            
            double healthMultiplier = 1 + healthBonus * Math.pow(level, 1.4);
            double finalHealth = Math.min(healthMultiplier, 200.0);
            
            double attackMultiplier = 1 + attackBonus * Math.pow(level, 1.42);
            double finalAttack = Math.min(attackMultiplier, 200.0);
            
            double armorMultiplier = 1 + 0.07 * Math.pow(level, 0.9);
            double finalArmor = Math.min(armorMultiplier, 2.0);
            
            double knockbackResistance = 0.012 * Math.pow(level, 0.95);
            double finalKnockback = Math.min(knockbackResistance, 1.0);
            
            sb.append(String.format("%3d  | %4.0f倍 | %4.0f倍 | %3.1f倍 | %3.0f%%\n", 
                level, 
                finalHealth,
                finalAttack,
                finalArmor,
                finalKnockback * 100));
        }
        
        // 添加最大级的属性
        if (maxLevel % step != 0) {
            double healthBonus = BetterEvEConfig.INSTANCE.entitylv_healthBonus.get() / 100;
            double attackBonus = BetterEvEConfig.INSTANCE.entitylv_attackBonus.get() / 100;
            
            double healthMultiplier = 1 + healthBonus * Math.pow(maxLevel, 1.4);
            double finalHealth = Math.min(healthMultiplier, 200.0);
            
            double attackMultiplier = 1 + attackBonus * Math.pow(maxLevel, 1.42);
            double finalAttack = Math.min(attackMultiplier, 200.0);
            
            double armorMultiplier = 1 + 0.07 * Math.pow(maxLevel, 0.9);
            double finalArmor = Math.min(armorMultiplier, 2.0);
            
            double knockbackResistance = 0.012 * Math.pow(maxLevel, 0.95);
            double finalKnockback = Math.min(knockbackResistance, 1.0);
            
            sb.append(String.format("%3d  | %4.0f倍 | %4.0f倍 | %3.1f倍 | %3.0f%%\n", 
                maxLevel, 
                finalHealth,
                finalAttack,
                finalArmor,
                finalKnockback * 100));
        }
        
        sb.append("\n注意：使用NBT属性的实体可以突破属性上限！");
        
        return sb.toString();
    }
}
