package com.example.bettereve.level;

import com.example.bettereve.config.BetterEvEConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LevelSystem {
    private static final Map<UUID, Integer> entityLevels = new HashMap<>();
    private static final String NBT_LEVEL_KEY = "BetterEvE_Level";

    /**
     * 获取生物等级
     */
    public static int getEntityLevel(LivingEntity entity) {
        // 首先尝试从 NBT 中获取等级（持久性存储）
        if (entity.getPersistentData().contains(NBT_LEVEL_KEY)) {
            return entity.getPersistentData().getInt(NBT_LEVEL_KEY);
        }
        // 如果 NBT 中没有，再从内存中获取
        return entityLevels.getOrDefault(entity.getUUID(), 0);
    }

    /**
     * 设置生物等级（仅用于召唤时设置）
     */
    public static void setEntityLevel(LivingEntity entity, int level) {
        if (level < 0) level = 0;
        int maxLevel = BetterEvEConfig.INSTANCE.level_maxLevel.get();
        if (level > maxLevel) level = maxLevel;
        
        // 同时存储到内存和 NBT 中，确保持久性
        entityLevels.put(entity.getUUID(), level);
        entity.getPersistentData().putInt(NBT_LEVEL_KEY, level);
        applySpawnLevel(entity, level);
        
        // 同步到客户端
        // 由于无法直接访问level字段，我们可以通过是否是客户端线程来判断
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            com.example.bettereve.network.NetworkManager.syncEntityLevelToAll(entity, level);
        }
    }

    /**
     * 从 NBT 中读取等级信息（实体加载时调用）
     */
    public static void loadLevelFromNBT(LivingEntity entity) {
        if (entity.getPersistentData().contains(NBT_LEVEL_KEY)) {
            int level = entity.getPersistentData().getInt(NBT_LEVEL_KEY);
            entityLevels.put(entity.getUUID(), level);
        }
    }

    /**
     * 保存等级信息到 NBT（实体保存时调用）
     */
    public static void saveLevelToNBT(LivingEntity entity) {
        if (entityLevels.containsKey(entity.getUUID())) {
            entity.getPersistentData().putInt(NBT_LEVEL_KEY, entityLevels.get(entity.getUUID()));
        }
    }

    /**
     * 清理实体等级数据（实体离开世界时调用）
     */
    public static void removeEntityLevel(LivingEntity entity) {
        entityLevels.remove(entity.getUUID());
        entity.getPersistentData().remove(NBT_LEVEL_KEY);
    }

    /**
     * 应用召唤等级属性
     * 每级增长10%生命值和攻击力，不增加移动速度
     * 护甲值每级+2，击退抗性每级+10%
     */
    public static void applySpawnLevel(LivingEntity entity, int level) {
        if (level <= 0) return;
        
        // 从配置获取加成值
        double healthBonus = BetterEvEConfig.INSTANCE.level_healthBonus.get();
        double attackBonus = BetterEvEConfig.INSTANCE.level_attackBonus.get();
        
        // 生命值增强：每级+配置百分比，最大200%
        if (entity.getAttribute(Attributes.MAX_HEALTH) != null) {
            double baseHealth = entity.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            double healthMultiplier = 1 + (level * healthBonus / 100);
            double maxMultiplier = 2.0; // 最大200%
            double finalMultiplier = Math.min(healthMultiplier, maxMultiplier);
            double newHealth = baseHealth * finalMultiplier;
            entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
            entity.setHealth((float)newHealth);
        }
        
        // 攻击力增强：每级+配置百分比
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double baseDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
            double damageMultiplier = 1 + (level * attackBonus / 100);
            double newDamage = baseDamage * damageMultiplier;
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
        }
        
        // 护甲值增强：每级+2，最大30
        if (entity.getAttribute(Attributes.ARMOR) != null) {
            double baseArmor = entity.getAttribute(Attributes.ARMOR).getBaseValue();
            double armorBonus = level * 2.0; // 每级+2护甲
            double maxArmor = 30.0; // 最大30护甲
            double newArmor = Math.min(baseArmor + armorBonus, maxArmor);
            entity.getAttribute(Attributes.ARMOR).setBaseValue(newArmor);
        }
        
        // 击退抗性增强：每级+10%，最大100%
        if (entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            double baseKnockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue();
            double knockbackBonus = level * 0.10; // 每级+10%
            double maxKnockback = 1.0; // 最大100%
            double newKnockback = Math.min(baseKnockback + knockbackBonus, maxKnockback);
            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(newKnockback);
        }
        
        // 移除速度增强（解决瞬移问题）
        // 不修改MOVEMENT_SPEED属性
    }

    /**
     * 获取等级数值表（用于显示）
     */
    public static String getLevelTable() {
        StringBuilder sb = new StringBuilder("等级属性表 (0-10级):\n");
        sb.append("等级 | 生命加成 | 攻击加成 | 护甲 | 击退抗性\n");
        sb.append("-----|----------|----------|------|----------\n");
        
        // 从配置获取加成值
        double healthBonus = BetterEvEConfig.INSTANCE.level_healthBonus.get();
        double attackBonus = BetterEvEConfig.INSTANCE.level_attackBonus.get();
        
        for (int level = 0; level <= 10; level++) {
            double healthMultiplier = 1 + (level * healthBonus / 100);
            double attackMultiplier = 1 + (level * attackBonus / 100);
            double armor = level * 2.0;
            double knockbackResistance = level * 0.10;
            
            // 应用上限
            healthMultiplier = Math.min(healthMultiplier, 2.0);
            armor = Math.min(armor, 30.0);
            knockbackResistance = Math.min(knockbackResistance, 1.0);
            
            sb.append(String.format("%2d  | %5.0f%%   | %5.0f%%   | %4.0f | %5.0f%%\n", 
                level, 
                (healthMultiplier - 1) * 100,
                (attackMultiplier - 1) * 100,
                armor,
                knockbackResistance * 100));
        }
        
        return sb.toString();
    }
}