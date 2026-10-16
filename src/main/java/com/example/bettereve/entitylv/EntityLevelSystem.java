package com.example.bettereve.entitylv;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.ProjectileRule;
import com.example.bettereve.faction.SummonedMarker;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityLevelSystem {
    private static final Map<UUID, Integer> entityLevels = new HashMap<>();
    private static final String NBT_LEVEL_KEY = "BetterEvE_Level";
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/Level");
    private static final Set<String> WARNED_MISSING_TABLES = new HashSet<>();

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
        // 剧本没配等级系统 → 完全不启用（不存、不加、不同步）
        if (levelCfg == null) {
            return;
        }
        if (level > levelCfg.getMaxLevel()) level = levelCfg.getMaxLevel();
        
        // 同时存储到内存和 NBT 中，确保持久性
        entityLevels.put(entity.getUUID(), level);
        entity.getPersistentData().putInt(NBT_LEVEL_KEY, level);
        
        // 应用属性加成。注意判定顺序：
        // 有些"投射物"其实是 LivingEntity 子类（咒术回战的 projectile_slash 就是 PathfinderMob），
        // 所以必须先判投射物，再判生物，否则这类实体会走生物加成、拿不到投掷物的伤害加成。
        if (entity instanceof AbstractArrow arrow) {
            applyProjectileLevel(arrow, level);
        } else if (entity instanceof Projectile projectile) {
            applyGenericProjectileLevel(projectile, level);
        } else if (entity instanceof LivingEntity living) {
            applySpawnLevel(living, level);
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
     * 获取实体的等级配置（只读剧本；剧本没配 = null = 无等级系统）。
     *
     * 解析顺序（越具体越优先）：
     * ① 实体类型覆盖表 levelTableOverrides[modid:name]
     * ② 投掷物专用表 options.projectile.levelTable
     * ③ 召唤物专用表 options.summon.levelTable
     * ④ 阵营表 factions[].levelTable
     */
    public static Scenario.EntityLevelConfig getEntityLevelConfig(Entity entity) {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || entity == null) return null;

        // ① 按实体类型覆盖（最具体）
        Map<String, String> overrides = scenario.getLevelTableOverrides();
        if (overrides != null) {
            Scenario.EntityLevelConfig cfg = tableByName(scenario, overrides.get(entity.getType().toString()));
            if (cfg != null) return cfg;
        }

        Scenario.ScenarioOptions opts = scenario.getOptions();

        // ② 投掷物自己的表
        if (ProjectileRule.isProjectileLike(entity)) {
            Scenario.EntityLevelConfig cfg = tableByName(scenario,
                    opts != null && opts.getProjectile() != null ? opts.getProjectile().getLevelTable() : null);
            if (cfg != null) return cfg;
        }

        // ③ 召唤物自己的表
        if (SummonedMarker.isSummoned(entity)) {
            Scenario.EntityLevelConfig cfg = tableByName(scenario,
                    opts != null && opts.getSummon() != null ? opts.getSummon().getLevelTable() : null);
            if (cfg != null) return cfg;
        }

        // ④ 阵营表
        if (scenario.getFactions() == null) return null;
        String factionId = FactionHelper.getEntityFactionId(entity);
        for (Scenario.FactionDef faction : scenario.getFactions()) {
            if (faction.getId() != null && faction.getId().equals(factionId)) {
                return tableByName(scenario, faction.getLevelTable());
            }
        }
        return null;
    }

    /** 按名字取等级表；名字为空返回 null，名字存在但查不到表则告警一次并返回 null */
    private static Scenario.EntityLevelConfig tableByName(Scenario scenario, String name) {
        if (name == null || name.isEmpty()) return null;
        Map<String, Scenario.EntityLevelConfig> tables = scenario.getLevelTables();
        Scenario.EntityLevelConfig cfg = tables == null ? null : tables.get(name);
        if (cfg == null && WARNED_MISSING_TABLES.add(name)) {
            LOGGER.warn("等级表 '{}' 未在 scenario.levelTables 中定义，该路径回退到下一优先级", name);
        }
        return cfg;
    }

    /** 该实体所在阵营是否启用了等级系统 */
    public static boolean isLevelSystemEnabled(Entity entity) {
        return getEntityLevelConfig(entity) != null;
    }

    /**
     * 等级表的「攻击倍率」：{@code 1 + attackBonus/100 × level^attackExp}。
     *
     * <p><b>给谁用</b>：伤害数值写死在原版代码里、走不到属性加成的那两类 BOSS 专用攻击 ——
     * 监守者音爆（{@link com.example.bettereve.mixin.SonicBoomMixin}）与凋灵头颅
     * （{@link com.example.bettereve.mixin.WitherSkullMixin}）。那两处的 {@code 10.0F} /
     * {@code 8.0F} 是字面量，等级表只能以倍率的形式在伤害产生点乘上去。
     * 普通近战不走这里 —— 那条路是 {@link #applySpawnLevel} 直接把倍率写进
     * {@code ATTACK_DAMAGE} 属性。
     *
     * <p>倍率公式与上限夹子跟 {@code applySpawnLevel} 里那条完全同源（那处也改成调本方法），
     * 所以近战与这两处特殊攻击的倍率永远同值。
     *
     * <p>无实体 / 无等级 / 无等级表 → {@code 1.0}（原版数值原样，等于没接）。
     */
    public static double getAttackMultiplier(Entity entity) {
        if (entity == null) return 1.0;
        int level = getEntityLevel(entity);
        if (level <= 0) return 1.0;
        Scenario.EntityLevelConfig cfg = getEntityLevelConfig(entity);
        if (cfg == null) return 1.0;
        double attackMultiplier = 1 + (cfg.getAttackBonus() / 100) * Math.pow(level, cfg.getAttackExponent());
        double maxAttack = hasCustomNbtAttributes() ? Double.MAX_VALUE : 200.0;
        return Math.min(attackMultiplier, maxAttack);
    }

    public static void applySpawnLevel(LivingEntity entity, int level) {
        if (level <= 0) return;

        boolean hasNbtAttributes = hasCustomNbtAttributes();

        Scenario.EntityLevelConfig cfg = getEntityLevelConfig(entity);
        if (cfg == null) return;
        double healthBonus = cfg.getHealthBonus() / 100;
        double armorBonus = cfg.getArmorBonus() / 100;
        double knockbackBonus = cfg.getKnockbackBonus() / 100;
        double healthExp = cfg.getHealthExponent();
        double armorExp = cfg.getArmorExponent();
        double knockbackExp = cfg.getKnockbackExponent();

        // 生命值
        if (entity.getAttribute(Attributes.MAX_HEALTH) != null) {
            double healthMultiplier = 1 + healthBonus * Math.pow(level, healthExp);
            double maxHealth = hasNbtAttributes ? Double.MAX_VALUE : 200.0;
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

        // 攻击力（倍率公式与 getAttackMultiplier 共用一处 —— 那处同时给"伤害写死在原版代码里"的
        // BOSS 专用攻击用，两边必须永远同值）
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double finalAttack = getAttackMultiplier(entity);
            double baseDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
            double newDamage = baseDamage * finalAttack;
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
        }

        // 护甲值
        if (entity.getAttribute(Attributes.ARMOR) != null) {
            double armorMultiplier = 1 + armorBonus * Math.pow(level, armorExp);
            double maxArmor = hasNbtAttributes ? Double.MAX_VALUE : 2.0;
            double finalArmor = Math.min(armorMultiplier, maxArmor);
            double baseArmor = entity.getAttribute(Attributes.ARMOR).getBaseValue();
            double newArmor = baseArmor * finalArmor;
            entity.getAttribute(Attributes.ARMOR).setBaseValue(newArmor);
        }

        // 击退抗性
        if (entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            double knockbackResistance = knockbackBonus * Math.pow(level, knockbackExp);
            double maxKnockback = hasNbtAttributes ? Double.MAX_VALUE : 1.0;
            double finalKnockback = Math.min(knockbackResistance, maxKnockback);
            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(finalKnockback);
        }
    }
    /**
     * 应用投掷物等级属性
     */
    public static void applyProjectileLevel(AbstractArrow projectile, int level) {
        if (level <= 0) return;

        // 按投掷物自身查配置：setEntityLevel 也按实体自己解析，
        // 这里再按 owner 查会出现"同一实体两处用不同表"的不一致。
        Scenario.EntityLevelConfig cfg = getEntityLevelConfig(projectile);
        if (cfg == null) {
            return;
        }
        double attackBonus = cfg.getAttackBonus() / 100;
        double attackExp = cfg.getAttackExponent();

        double damageMultiplier = 1 + attackBonus * Math.pow(level, attackExp);
        double maxDamage = cfg.getMaxDamage();
        double finalDamage = Math.min(damageMultiplier, maxDamage);
        double currentDamage = projectile.getBaseDamage();
        projectile.setBaseDamage(currentDamage * finalDamage);

        double pierceBonus = 0.05 * Math.pow(level, 1.2);
        double maxPierce = 20.0;
        double finalPierce = Math.min(pierceBonus, maxPierce);
        byte currentPierce = projectile.getPierceLevel();
        projectile.setPierceLevel((byte) Math.max((int) finalPierce, currentPierce));
    }

    /**
     * 非箭类投射物的等级加成。
     *
     * 说明：Projectile 与 LivingEntity 在类层级上互不相交（Projectile extends Entity），
     * 所以走到这里的都是"纯 Entity 投射物"。原版 Projectile 没有统一的伤害接口
     * （只有 AbstractArrow 有 getBaseDamage/setBaseDamage），也没有可写的伤害字段，
     * 因此这类投射物无法施加等级加成。
     *
     * 例外情况已在上层分支处理：像咒术回战 projectile_slash 那种"名字像投射物、
     * 实际是 PathfinderMob"的实体，因为它是 Mob 而不是 Projectile，会走
     * applySpawnLevel 拿到生物加成，不会落到这里。
     */
    public static void applyGenericProjectileLevel(Projectile projectile, int level) {
        // 纯 Entity 投射物没有可施加的等级加成字段，保持原样。
        // 保留此方法作为将来接入模组专属伤害接口的接入点。
    }
    /**
     * 检查实体是否有自定义NBT属性
     */
    private static boolean hasCustomNbtAttributes() {
        // 对于通过 BetterEvE 命令召唤的实体，默认允许突破属性上限
        // 因为我们需要支持高等级生物的超高属性
        return true;
    }

    /**
     * 获取等级数值表（用于显示，根据配置动态生成）
     */
    public static String getLevelTable(Scenario.EntityLevelConfig cfg) {
        if (cfg == null) return "未加载剧本或未配置等级参数";
        int maxLevel = cfg.getMaxLevel();
        double healthBonus = cfg.getHealthBonus() / 100;
        double attackBonus = cfg.getAttackBonus() / 100;
        double armorBonus = cfg.getArmorBonus() / 100;
        double knockbackBonus = cfg.getKnockbackBonus() / 100;
        double healthExp = cfg.getHealthExponent();
        double attackExp = cfg.getAttackExponent();
        double armorExp = cfg.getArmorExponent();
        double knockbackExp = cfg.getKnockbackExponent();

        StringBuilder sb = new StringBuilder(String.format("等级属性表 (0-%d级):\n", maxLevel));
        sb.append("等级 | 生命值 | 攻击力 | 护甲 | 击退抗性\n");
        sb.append("-----|--------|--------|------|----------\n");

        int step = 1;
        if (maxLevel > 100) step = maxLevel / 10;
        else if (maxLevel > 50) step = 10;
        else if (maxLevel > 20) step = 5;

        for (int level = 0; level <= maxLevel; level += step) {
            double healthMultiplier = 1 + healthBonus * Math.pow(level, healthExp);
            double finalHealth = Math.min(healthMultiplier, 200.0);
            double attackMultiplier = 1 + attackBonus * Math.pow(level, attackExp);
            double finalAttack = Math.min(attackMultiplier, 200.0);
            double armorMultiplier = 1 + armorBonus * Math.pow(level, armorExp);
            double finalArmor = Math.min(armorMultiplier, 2.0);
            double knockbackResistance = knockbackBonus * Math.pow(level, knockbackExp);
            double finalKnockback = Math.min(knockbackResistance, 1.0);
            sb.append(String.format("%3d  | %4.0f倍 | %4.0f倍 | %3.1f倍 | %3.0f%%\n",
                level, finalHealth, finalAttack, finalArmor, finalKnockback * 100));
        }
        return sb.toString();
    }
}
