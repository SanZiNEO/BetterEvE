package com.example.bettereve.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface MobAccessor {
    /**
     * 获取对 protected 方法 populateDefaultEquipmentSlots 的访问
     */
    @Invoker("populateDefaultEquipmentSlots")
    void invokePopulateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty);

    /**
     * 获取对 protected 方法 populateDefaultEquipmentEnchantments 的访问
     */
    @Invoker("populateDefaultEquipmentEnchantments")
    void invokePopulateDefaultEquipmentEnchantments(RandomSource random, DifficultyInstance difficulty);

    /**
     * 获取对 private 字段 navigation 的访问（替换导航器用，替代反射实现）
     */
    @Accessor("navigation")
    void setNavigation(PathNavigation navigation);
}
