package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import com.example.bettereve.faction.PlayerFactionData;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FactionHelper {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionHelper");
    private static final String FACTION_NBT_KEY = "BetterEvE:FactionID";
    private static final String SHOW_HEALTH_BAR_NBT_KEY = "BetterEvE:ShowHealthBar";
    private static final String SHOW_LEVEL_TEXT_NBT_KEY = "BetterEvE:ShowLevelText";
    private static final String SHOW_HEALTH_TEXT_NBT_KEY = "BetterEvE:ShowHealthText";

    // 获取实体的阵营ID（无阵营统一返回 ""，避免 null 进 NBT 崩溃）
    public static String getEntityFactionId(Entity entity) {
        if (entity == null) {
            return "";
        }

        // 特殊处理 CrystalEntity 类型
        if (entity instanceof com.example.bettereve.entity.CrystalEntity) {
            com.example.bettereve.entity.CrystalEntity crystalEntity = (com.example.bettereve.entity.CrystalEntity) entity;
            String factionId = crystalEntity.getFactionId();
            return factionId != null && !factionId.isEmpty() ? factionId : "";
        }

        // 特殊处理 Player 类型 — 走 PlayerFactionData（使用独立的 NBT key）
        if (entity instanceof Player player) {
            String factionId = PlayerFactionData.getPlayerFaction(player);
            return factionId != null ? factionId : "";
        }

        // 其他实体使用 NBT 实现
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.contains(FACTION_NBT_KEY)) {
            String factionId = nbt.getString(FACTION_NBT_KEY);
            return factionId.isEmpty() ? "" : factionId;
        }

        return ""; // 自然生成/未分配 = 无阵营
    }

    // 设置实体的阵营ID（null/空 = 清除阵营）
    public static void setEntityFactionId(Entity entity, String factionId) {
        if (entity == null) {
            return;
        }

        // 特殊处理 CrystalEntity 类型
        if (entity instanceof com.example.bettereve.entity.CrystalEntity) {
            com.example.bettereve.entity.CrystalEntity crystalEntity = (com.example.bettereve.entity.CrystalEntity) entity;
            crystalEntity.setFactionId(factionId == null ? "" : factionId);
        } else {
            // 其他实体使用 NBT 实现
            CompoundTag nbt = entity.getPersistentData();
            if (factionId == null || factionId.isEmpty()) {
                nbt.remove(FACTION_NBT_KEY);
            } else {
                nbt.putString(FACTION_NBT_KEY, factionId);
            }
        }
    }

    // 获取实体是否显示血条（纯NBT实现）
    public static boolean getEntityShowHealthBar(Entity entity) {
        if (entity == null) {
            return true; // 默认显示血条
        }

        CompoundTag nbt = entity.getPersistentData();
        
        if (nbt.contains(SHOW_HEALTH_BAR_NBT_KEY)) {
            return nbt.getBoolean(SHOW_HEALTH_BAR_NBT_KEY);
        }

        return true; // 默认显示血条
    }

    // 设置实体是否显示血条（纯NBT实现）
    public static void setEntityShowHealthBar(Entity entity, boolean showHealthBar) {
        if (entity == null) {
            return;
        }

        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean(SHOW_HEALTH_BAR_NBT_KEY, showHealthBar);
        
        // NBT数据会自动保存，无需额外操作
    }

    // 获取实体是否显示 Lv 等级文字（纯NBT实现，默认 true）
    public static boolean getEntityShowLevelText(Entity entity) {
        if (entity == null) {
            return true;
        }
        CompoundTag nbt = entity.getPersistentData();
        return !nbt.contains(SHOW_LEVEL_TEXT_NBT_KEY) || nbt.getBoolean(SHOW_LEVEL_TEXT_NBT_KEY);
    }

    // 设置实体是否显示 Lv 等级文字（纯NBT实现）
    public static void setEntityShowLevelText(Entity entity, boolean showLevelText) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putBoolean(SHOW_LEVEL_TEXT_NBT_KEY, showLevelText);
    }

    // 获取实体是否显示血量数字（纯NBT实现，默认 true）
    public static boolean getEntityShowHealthText(Entity entity) {
        if (entity == null) {
            return true;
        }
        CompoundTag nbt = entity.getPersistentData();
        return !nbt.contains(SHOW_HEALTH_TEXT_NBT_KEY) || nbt.getBoolean(SHOW_HEALTH_TEXT_NBT_KEY);
    }

    // 设置实体是否显示血量数字（纯NBT实现）
    public static void setEntityShowHealthText(Entity entity, boolean showHealthText) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putBoolean(SHOW_HEALTH_TEXT_NBT_KEY, showHealthText);
    }

    // 获取实体所属的阵营
    public static Faction getEntityFaction(Entity entity) {
        return FactionRegistry.getFaction(getEntityFactionId(entity));
    }
    
    // 通过阵营ID获取阵营对象
    public static Faction getEntityFactionById(String factionId) {
        return FactionRegistry.getFaction(factionId);
    }

    // 检查两个实体是否敌对
    public static boolean isEntitiesHostile(Entity entity1, Entity entity2) {
        if (entity1 == null || entity2 == null) {
            return false;
        }

        String factionId1 = getEntityFactionId(entity1);
        String factionId2 = getEntityFactionId(entity2);

        return FactionRelationManager.isHostile(factionId1, factionId2);
    }

    /**
     * 取实体用于"攻击归属"的有效阵营。
     *
     * 常规情况就是实体自己的阵营；唯一的例外是**攻击实体**（Projectile 家族，或名字像
     * 投掷物的实体）自己还没有阵营、但能解析出主人 —— 说明阵营继承没赶上（模组在
     * spawn 之后才写归属、或写得太晚）。这时按主人的阵营算，否则这次攻击会被当成
     * "无阵营的混乱生物"打到友军身上（例如宿傩的解打中宿傩一侧的人）。
     *
     * 只读不写：判定不改变实体状态，实体该不该有阵营仍由继承逻辑负责。
     * 用它的地方只有伤害层的敌我判定（见 CrystalSystem.onLivingAttack）。
     */
    public static String getEffectiveFactionId(Entity entity) {
        if (entity == null) {
            return "";
        }

        String own = getEntityFactionId(entity);
        if (!own.isEmpty()) {
            return own;
        }

        // 只对攻击实体做归属回退：普通生物/玩家按自己的阵营算，不往上找主人
        if (!(entity instanceof net.minecraft.world.entity.projectile.Projectile)
                && !ProjectileRule.isProjectileLike(entity)) {
            return own;
        }

        Entity owner = SummonerResolver.resolve(entity);
        return owner == null ? own : getEntityFactionId(owner);
    }
}