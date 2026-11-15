package com.sanzineo.bettereve.entity;

import com.sanzineo.bettereve.Bettereve;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class CrystalEntityType {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Bettereve.MODID);
    
    // 存储所有实体类型的映射表：size -> RegistryObject
    private static final Map<String, RegistryObject<EntityType<CrystalEntity>>> ENTITY_MAP = new HashMap<>();
    
    // 原来的单一实体类型（保留兼容性）
    public static final RegistryObject<EntityType<CrystalEntity>> CRYSTAL_ENTITY = ENTITY_TYPES.register("crystal_entity", () ->
            EntityType.Builder.of(CrystalEntity::new, MobCategory.MONSTER)
                    .sized(3.0F, 10.0F)
                    .clientTrackingRange(80)
                    .updateInterval(3)
                    .build("crystal_entity")
    );

    static {
        // 生成 150 个不同尺寸的实体类型（宽度：1-10，高度：1-15）
        for (int width = 1; width <= 10; width++) {
            for (int height = 1; height <= 15; height++) {
                registerSizeVariant(width, height);
            }
        }
    }
    
    /**
     * 注册指定尺寸的实体类型
     */
    private static void registerSizeVariant(int width, int height) {
        String sizeKey = getSizeKey(width, height);
        String registryName = "crystal_entity_" + sizeKey;
        
        RegistryObject<EntityType<CrystalEntity>> entityType = ENTITY_TYPES.register(registryName, () ->
                EntityType.Builder.of(CrystalEntity::new, MobCategory.MONSTER)
                        .sized((float) width, (float) height)  // 预设碰撞箱大小
                        .clientTrackingRange(80)
                        .updateInterval(3)
                        .build(registryName)
        );
        
        ENTITY_MAP.put(sizeKey, entityType);
    }
    
    /**
     * 获取尺寸键值（格式：axb）
     */
    public static String getSizeKey(int width, int height) {
        return width + "x" + height;
    }
    
    /**
     * 根据尺寸键值获取实体类型
     */
    public static EntityType<CrystalEntity> getEntityTypeBySize(String sizeKey) {
        RegistryObject<EntityType<CrystalEntity>> registryObj = ENTITY_MAP.get(sizeKey);
        if (registryObj != null) {
            return registryObj.get();
        }
        // 如果找不到指定的尺寸，返回默认实体类型
        return CRYSTAL_ENTITY.get();
    }
    
    /**
     * 获取所有可用的尺寸键值
     */
    public static String[] getAllSizeKeys() {
        return ENTITY_MAP.keySet().toArray(new String[0]);
    }
    
    /**
     * 检查尺寸键值是否有效
     */
    public static boolean isValidSizeKey(String sizeKey) {
        return ENTITY_MAP.containsKey(sizeKey);
    }
    
    /**
     * 从尺寸键值中解析宽度和高度
     */
    public static int[] parseSizeKey(String sizeKey) {
        String[] parts = sizeKey.split("x");
        if (parts.length == 2) {
            try {
                int width = Integer.parseInt(parts[0]);
                int height = Integer.parseInt(parts[1]);
                return new int[] { width, height };
            } catch (NumberFormatException e) {
                return new int[] { 3, 10 }; // 默认尺寸
            }
        }
        return new int[] { 3, 10 }; // 默认尺寸
    }
    
    /**
     * 获取所有实体类型的RegistryObject列表
     */
    public static java.util.List<RegistryObject<EntityType<CrystalEntity>>> getAllEntityTypes() {
        java.util.List<RegistryObject<EntityType<CrystalEntity>>> list = new java.util.ArrayList<>();
        list.add(CRYSTAL_ENTITY);
        list.addAll(ENTITY_MAP.values());
        return list;
    }
}
