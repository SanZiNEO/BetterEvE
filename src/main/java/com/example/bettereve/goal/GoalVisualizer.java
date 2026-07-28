package com.example.bettereve.goal;

import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

/**
 * Goal 区域粒子柱可视化（仅客户端）。
 * 在 Goal 区域的四个角渲染粒子柱，颜色 = 阵营颜色。
 */
@OnlyIn(Dist.CLIENT)
public class GoalVisualizer {
    private static boolean enabled = false;
    private static final Random RANDOM = new Random();

    /**
     * 启用/禁用可视化。
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    /**
     * 是否启用。
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 渲染所有 Goal 区域的粒子柱。
     * 应在客户端的 render tick 中调用。
     */
    public static void renderParticles() {
        if (!enabled) return;
        
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        
        // 只在玩家附近渲染
        Vec3 playerPos = mc.player != null ? mc.player.position() : Vec3.ZERO;
        
        for (GoalRegion region : GoalManager.getAllGoals()) {
            // 只在距离玩家 64 格内渲染
            double cx = region.getCenterX();
            double cz = region.getCenterZ();
            if (playerPos.distanceToSqr(new Vec3(cx, playerPos.y, cz)) > 64 * 64) continue;
            
            // 获取阵营颜色
            Faction faction = FactionRegistry.getFaction(region.getFactionId());
            int color = faction != null ? faction.getColorInt() : 0xFFFFFF;
            
            // 在四个角渲染粒子
            renderColumn(level, region.getX1(), region.getZ1(), color);
            renderColumn(level, region.getX2(), region.getZ1(), color);
            renderColumn(level, region.getX1(), region.getZ2(), color);
            renderColumn(level, region.getX2(), region.getZ2(), color);
        }
    }

    private static void renderColumn(Level level, double x, double z, int color) {
        // 每 5 tick 发射一个粒子
        if (RANDOM.nextInt(5) != 0) return;
        
        double y = level.getMinBuildHeight() + RANDOM.nextDouble() * (level.getMaxBuildHeight() - level.getMinBuildHeight());
        
        // 使用火焰粒子（颜色无法直接控制，但火焰粒子可见性高）
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
    }
}
