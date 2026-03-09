package com.example.bettereve.entity;

import com.example.bettereve.Bettereve;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BaseEntityType {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Bettereve.MODID);

    public static final RegistryObject<EntityType<BaseEntity>> BASE_ENTITY = ENTITY_TYPES.register("base_entity", () ->
            EntityType.Builder.of(BaseEntity::new, MobCategory.MONSTER)
                    .sized(3.0F, 10.0F)
                    .clientTrackingRange(80)
                    .updateInterval(3)
                    .build("base_entity")
    );
}