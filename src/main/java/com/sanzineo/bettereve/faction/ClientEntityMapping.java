package com.sanzineo.bettereve.faction;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientEntityMapping - 客户端实体映射器
 * 用于在客户端维护实体ID到UUID的映射，以支持阵营和等级系统的缓存
 * 保持轻量化和高性能
 */
@OnlyIn(Dist.CLIENT)
public class ClientEntityMapping {
    
    // 实体ID到UUID的映射（使用ConcurrentHashMap保证线程安全）
    private static final Map<Integer, UUID> entityIdToUuid = new ConcurrentHashMap<>();
    
    // 实体UUID到ID的映射（反向映射）
    private static final Map<UUID, Integer> entityUuidToId = new ConcurrentHashMap<>();
    
    /**
     * 记录实体的映射关系
     * @param entity 实体
     */
    public static void recordEntityMapping(Entity entity) {
        if (entity == null) {
            return;
        }
        
        int entityId = entity.getId();
        UUID entityUUID = entity.getUUID();
        
        entityIdToUuid.put(entityId, entityUUID);
        entityUuidToId.put(entityUUID, entityId);
    }
    
    /**
     * 通过实体ID获取UUID
     * @param entityId 实体ID
     * @return UUID，如果未找到则返回null
     */
    public static UUID getUuidByEntityId(int entityId) {
        return entityIdToUuid.get(entityId);
    }
    
    /**
     * 通过实体UUID获取ID
     * @param entityUUID 实体UUID
     * @return 实体ID，如果未找到则返回-1
     */
    public static int getEntityIdByUuid(UUID entityUUID) {
        return entityUuidToId.getOrDefault(entityUUID, -1);
    }
    
    /**
     * 清除实体的映射关系
     * @param entity 实体
     */
    public static void clearEntityMapping(Entity entity) {
        if (entity == null) {
            return;
        }
        
        int entityId = entity.getId();
        UUID entityUUID = entity.getUUID();
        
        entityIdToUuid.remove(entityId);
        entityUuidToId.remove(entityUUID);
    }
    
    /**
     * 清除所有映射关系
     */
    public static void clearAllMapping() {
        entityIdToUuid.clear();
        entityUuidToId.clear();
    }
    
    /**
     * 获取当前映射数量
     */
    public static int getMappingCount() {
        return entityIdToUuid.size();
    }
    
    /**
     * 检查实体ID是否已记录
     */
    public static boolean hasEntityIdMapping(int entityId) {
        return entityIdToUuid.containsKey(entityId);
    }
    
    /**
     * 检查实体UUID是否已记录
     */
    public static boolean hasEntityUuidMapping(UUID entityUUID) {
        return entityUuidToId.containsKey(entityUUID);
    }
}