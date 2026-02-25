package com.example.bettereve.level;

import com.example.bettereve.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LevelSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelSystem.class);
    private static final String LEVEL_NBT_KEY = "BetterEvE:Level";

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            // 为新加入的实体设置默认等级
            int level = getEntityLevel(livingEntity);
            if (level == 0) {
                setEntityLevel(livingEntity, 1);
            }

            // 应用等级加成
            applyLevelBonuses(livingEntity);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        // 每50个刻重新应用一次等级加成，以防属性被重置
        if (entity.tickCount % 50 == 0) {
            applyLevelBonuses(entity);
        }
    }

    public static int getEntityLevel(LivingEntity entity) {
        CompoundTag nbt = entity.getPersistentData();
        return nbt.getInt(LEVEL_NBT_KEY);
    }

    public static void setEntityLevel(LivingEntity entity, int level) {
        if (level < 1) {
            level = 1;
        }
        if (level > Config.levelMax) {
            level = Config.levelMax;
        }

        CompoundTag nbt = entity.getPersistentData();
        nbt.putInt(LEVEL_NBT_KEY, level);

        // 立即应用等级加成
        applyLevelBonuses(entity);
    }

    public static void applyLevelBonuses(LivingEntity entity) {
        int level = getEntityLevel(entity);
        if (level <= 1) {
            return;
        }

        // 计算加成倍数
        double bonusMultiplier = 1 + ((level - 1) * Config.levelHealthBonus / 100);

        // 应用血量加成
        double baseHealth = entity.getAttribute(Attributes.MAX_HEALTH).getBaseValue() / bonusMultiplier;
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth * bonusMultiplier);
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }

        // 应用攻击力加成
        if (entity instanceof Monster) {
            double attackBonusMultiplier = 1 + ((level - 1) * Config.levelAttackBonus / 100);
            double baseAttack = entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() / attackBonusMultiplier;
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(baseAttack * attackBonusMultiplier);
        }

        // 应用移动速度加成
        double speedBonusMultiplier = 1 + ((level - 1) * Config.levelSpeedBonus / 100);
        double baseSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() / speedBonusMultiplier;
        entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * speedBonusMultiplier);
    }

    public static int getMaxLevel() {
        return Config.levelMax;
    }

    public static double getHealthBonusPerLevel() {
        return Config.levelHealthBonus;
    }

    public static double getAttackBonusPerLevel() {
        return Config.levelAttackBonus;
    }

    public static double getSpeedBonusPerLevel() {
        return Config.levelSpeedBonus;
    }
}