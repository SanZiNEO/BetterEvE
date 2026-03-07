package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FactionHelper {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionHelper");
    private static final String FACTION_NBT_KEY = "BetterEvE:FactionID";
    private static final String SHOW_HEALTH_BAR_NBT_KEY = "BetterEvE:ShowHealthBar";

    // 获取实体的阵营ID（纯NBT实现，保持跨模组兼容性）
    public static String getEntityFactionId(Entity entity) {
        if (entity == null) {
            return "neutral";
        }

        CompoundTag nbt = entity.getPersistentData();
        
        if (nbt.contains(FACTION_NBT_KEY)) {
            return nbt.getString(FACTION_NBT_KEY);
        }

        return "neutral"; // 默认中立阵营
    }

    // 设置实体的阵营ID（纯NBT实现）
    public static void setEntityFactionId(Entity entity, String factionId) {
        if (entity == null) {
            LOGGER.warn("Attempting to set faction for null entity");
            return;
        }

        if (factionId == null) {
            factionId = "neutral";
        }

        // 验证阵营ID的合法性
        if (!FactionRegistry.getFaction(factionId).getId().equals("neutral") && !FactionRegistry.factionExists(factionId)) {
            LOGGER.warn("Attempting to set non-existent faction: {}", factionId);
            factionId = "neutral";
        }

        CompoundTag nbt = entity.getPersistentData();
        String oldFactionId = nbt.getString(FACTION_NBT_KEY);
        nbt.putString(FACTION_NBT_KEY, factionId);
        
        LOGGER.debug("Set entity {} faction from {} to {}", entity.getName(), oldFactionId, factionId);
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
            LOGGER.warn("Attempting to set health bar visibility for null entity");
            return;
        }

        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean(SHOW_HEALTH_BAR_NBT_KEY, showHealthBar);
        
        LOGGER.debug("Set entity {} health bar visibility to {}", entity.getName(), showHealthBar);
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

        return FactionRegistry.isHostile(factionId1, factionId2);
    }
}
