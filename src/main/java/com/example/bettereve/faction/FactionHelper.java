package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FactionHelper {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionHelper");
    private static final String FACTION_NBT_KEY = "BetterEvE:FactionID";
    private static final String SHOW_HEALTH_BAR_NBT_KEY = "BetterEvE:ShowHealthBar";

    // 获取实体的阵营ID（支持 BaseEntity 类型的特殊处理）
    public static String getEntityFactionId(Entity entity) {
        if (entity == null) {
            return "neutral";
        }

        // 特殊处理 BaseEntity 类型
        if (entity instanceof com.example.bettereve.entity.BaseEntity) {
            com.example.bettereve.entity.BaseEntity baseEntity = (com.example.bettereve.entity.BaseEntity) entity;
            String factionId = baseEntity.getFactionId();
            return factionId.isEmpty() ? "neutral" : factionId;
        }

        // 其他实体使用 NBT 实现
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.contains(FACTION_NBT_KEY)) {
            return nbt.getString(FACTION_NBT_KEY);
        }

        return "neutral"; // 默认中立阵营
    }

    // 设置实体的阵营ID（支持 BaseEntity 类型的特殊处理）
    public static void setEntityFactionId(Entity entity, String factionId) {
        if (entity == null) {
            return;
        }

        if (factionId == null) {
            factionId = "neutral";
        }

        // 特殊处理 BaseEntity 类型
        if (entity instanceof com.example.bettereve.entity.BaseEntity) {
            com.example.bettereve.entity.BaseEntity baseEntity = (com.example.bettereve.entity.BaseEntity) entity;
            baseEntity.setFactionId(factionId);
        } else {
            // 其他实体使用 NBT 实现
            CompoundTag nbt = entity.getPersistentData();
            nbt.putString(FACTION_NBT_KEY, factionId);
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