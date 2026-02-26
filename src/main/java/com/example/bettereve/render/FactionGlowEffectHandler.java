package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Bettereve.MODID)
public class FactionGlowEffectHandler {

    // 检查间隔（刻）
    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            // 实体加入世界时立即检查
            updateGlowEffect(livingEntity);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();

        // 每 CHECK_INTERVAL 刻检查一次阵营状态
        if (entity.tickCount % CHECK_INTERVAL == 0) {
            updateGlowEffect(entity);
        }
    }

    // 更新发光效果
    private static void updateGlowEffect(LivingEntity entity) {
        // 检查实体是否有阵营（非中立）
        boolean hasFaction = !FactionHelper.getEntityFactionId(entity).equals("neutral");

        // 检查实体当前是否有发光效果
        boolean hasGlowEffect = entity.hasEffect(MobEffects.GLOWING);

        if (hasFaction && !hasGlowEffect) {
            // 添加无限时间的发光效果
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        } else if (!hasFaction && hasGlowEffect) {
            // 移除发光效果（如果不再有阵营）
            entity.removeEffect(MobEffects.GLOWING);
        }
    }
}