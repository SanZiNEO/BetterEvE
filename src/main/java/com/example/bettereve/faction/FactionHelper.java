package com.example.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.LazyOptional;

public class FactionHelper {
    private static final String FACTION_NBT_KEY = "BetterEvE:FactionID";

    // 获取实体的阵营ID
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

    // 设置实体的阵营ID
    public static void setEntityFactionId(Entity entity, String factionId) {
        if (entity == null) {
            return;
        }

        if (factionId == null) {
            factionId = "neutral";
        }

        CompoundTag nbt = entity.getPersistentData();
        nbt.putString(FACTION_NBT_KEY, factionId);
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