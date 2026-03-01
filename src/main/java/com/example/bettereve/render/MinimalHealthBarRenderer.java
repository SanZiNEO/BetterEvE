package com.example.bettereve.render;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.level.LevelSystem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * MinimalHealthBarRenderer - 极简血条渲染器
 * 实现小而信息丰富的血条显示，包含阵营颜色、等级和血量信息
 * 设计原则：极简、高效、信息丰富
 */
public class MinimalHealthBarRenderer {
    
    // 血条尺寸参数（像素）
    private static final float BAR_WIDTH = 50.0f;      // 血条宽度
    private static final float BAR_HEIGHT = 8.0f;      // 血条高度
    private static final float BAR_Y_OFFSET = 0.5f;    // 实体头顶偏移（格）
    private static final float LEVEL_X_OFFSET = -15.0f; // 等级文字X偏移
    
    // 颜色定义（ARGB格式）
    private static final int BACKGROUND_COLOR = 0xB4000000; // 半透明黑色背景
    private static final int TEXT_COLOR = 0xFFFFFFFF;       // 白色文字
    
    // 阵营颜色映射
    private static final int RED_FACTION_COLOR = 0xFFFF3232;   // 红色阵营
    private static final int BLUE_FACTION_COLOR = 0xFF3264FF;  // 蓝色阵营
    private static final int GREEN_FACTION_COLOR = 0xFF32C832; // 绿色中立
    
    // 渲染距离限制（格）
    private static final float MAX_RENDER_DISTANCE = 32.0f;
    
    /**
     * 检查是否应该显示血条
     * @param entity 要检查的实体
     * @return 是否应该显示血条
     */
    public static boolean shouldShowHealthBar(Entity entity) {
        // 1. 基础检查
        if (entity == null || !(entity instanceof LivingEntity)) {
            return false;
        }
        
        return true; // 显示所有LivingEntity的血条，不受其他条件限制
    }
    
    /**
     * 渲染极简血条
     * @param entity 要渲染的实体
     * @param poseStack 姿势堆栈
     * @param bufferSource 缓冲区源
     * @param partialTick 部分tick时间
     */
    public static void renderHealthBar(Entity entity, PoseStack poseStack, 
                                       MultiBufferSource bufferSource, float partialTick) {
        if (!shouldShowHealthBar(entity)) {
            return;
        }
        
        LivingEntity livingEntity = (LivingEntity) entity;
        
        // 保存当前矩阵状态
        poseStack.pushPose();
        
        try {
            // 1. 应用变换：将血条定位到实体头顶
            applyEntityTransform(entity, poseStack, partialTick);
            
            // 2. 获取血条数据
            float healthPercent = getHealthPercent(livingEntity);
            int factionColor = getFactionColor(entity);
            int level = getEntityLevel(entity);
            boolean shouldShowLevel = shouldShowLevel(level);
            
            // 3. 渲染血条背景
            renderBarBackground(poseStack, bufferSource);
            
            // 4. 渲染血量填充（阵营颜色）
            renderHealthFill(poseStack, bufferSource, healthPercent, factionColor);
            
            // 5. 渲染等级文字（如果需要）
            if (shouldShowLevel) {
                renderLevelText(poseStack, bufferSource, level);
            }
            
            // 6. 渲染血量数值
            renderHealthText(poseStack, bufferSource, livingEntity);
            
        } finally {
            // 恢复矩阵状态
            poseStack.popPose();
        }
    }
    
    /**
     * 应用实体变换，将血条定位到实体头顶
     */
    private static void applyEntityTransform(Entity entity, PoseStack poseStack, float partialTick) {
        // 计算实体插值位置（部分Tick插值）
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick + entity.getBbHeight() + BAR_Y_OFFSET;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        
        // 应用相机变换
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        
        // 使血条始终面向相机
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        
        // 应用缩放（使血条大小合适）
        float scale = 0.02666667f; // Minecraft标准名称标签缩放
        poseStack.scale(-scale, -scale, scale);
    }
    
    /**
     * 获取实体血量百分比（带插值动画）
     */
    private static float getHealthPercent(LivingEntity entity) {
        // 保存上一帧血量用于插值
        if (!healthCache.containsKey(entity.getUUID())) {
            healthCache.put(entity.getUUID(), entity.getHealth());
        }
        
        float previousHealth = healthCache.get(entity.getUUID());
        float currentHealth = entity.getHealth();
        
        // 如果血量发生变化，开始插值
        if (previousHealth != currentHealth) {
            float interpolatedHealth = previousHealth + (currentHealth - previousHealth) * 0.1f; // 10% 插值速度
            healthCache.put(entity.getUUID(), interpolatedHealth);
            
            // 接近目标值时直接设置
            if (Math.abs(interpolatedHealth - currentHealth) < 0.1f) {
                healthCache.put(entity.getUUID(), currentHealth);
                interpolatedHealth = currentHealth;
            }
            
            currentHealth = interpolatedHealth;
        }
        
        float maxHealth = entity.getMaxHealth();
        
        if (maxHealth <= 0) {
            return 0.0f;
        }
        
        return Math.max(0.0f, Math.min(1.0f, currentHealth / maxHealth));
    }
    
    // 血量缓存，用于插值动画
    private static final java.util.Map<java.util.UUID, Float> healthCache = new java.util.HashMap<>();
    
    /**
     * 获取阵营颜色
     */
    private static int getFactionColor(Entity entity) {
        // 优先使用客户端缓存获取阵营ID
        String factionId = com.example.bettereve.faction.ClientFactionCache.getCachedFactionId(entity);
        
        // 如果缓存未命中，再使用FactionHelper读取NBT
        if (factionId == null) {
            factionId = FactionHelper.getEntityFactionId(entity);
        }
        
        // 映射到颜色
        return switch (factionId) {
            case "red" -> RED_FACTION_COLOR;
            case "blue" -> BLUE_FACTION_COLOR;
            default -> GREEN_FACTION_COLOR; // 默认绿色中立
        };
    }
    
    /**
     * 获取实体等级
     */
    private static int getEntityLevel(Entity entity) {
        try {
            // 确保实体是LivingEntity
            if (entity instanceof LivingEntity) {
                // 在客户端，优先使用LevelSyncPacket提供的等级缓存
                if (net.minecraft.client.Minecraft.getInstance().level != null && net.minecraft.client.Minecraft.getInstance().level.isClientSide) {
                    int cachedLevel = com.example.bettereve.network.LevelSyncPacket.ClientLevelCache.getEntityLevel(entity.getUUID());
                    if (cachedLevel > 0) {
                        return cachedLevel;
                    }
                }
                
                // 如果客户端缓存中没有，或者在服务器端，直接调用LevelSystem
                return LevelSystem.getEntityLevel((LivingEntity) entity);
            }
            return 0;
        } catch (Exception e) {
            // 如果等级系统不可用，返回0
            return 0;
        }
    }
    
    /**
     * 检查是否应该显示等级
     */
    private static boolean shouldShowLevel(int level) {
        return level > 0; // 0级不显示等级
    }
    
    /**
     * 渲染血条背景（半透明黑色）
     */
    private static void renderBarBackground(PoseStack poseStack, MultiBufferSource bufferSource) {
        // 使用自定义渲染类型
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.BACKGROUND_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        // 绘制矩形背景（Z坐标向正方向移动，会被血量填充遮挡）
        float halfWidth = BAR_WIDTH / 2.0f;
        float halfHeight = BAR_HEIGHT / 2.0f;
        
        int r = (BACKGROUND_COLOR >> 16) & 0xFF;
        int g = (BACKGROUND_COLOR >> 8) & 0xFF;
        int b = BACKGROUND_COLOR & 0xFF;
        int a = (BACKGROUND_COLOR >> 24) & 0xFF;
        
        float backgroundZ = 0.1f; // 向正方向移动，会被血量填充遮挡
        
        // 左上角
        vertexConsumer.vertex(matrix, -halfWidth, -halfHeight, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        // 右上角
        vertexConsumer.vertex(matrix, halfWidth, -halfHeight, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        // 右下角
        vertexConsumer.vertex(matrix, halfWidth, halfHeight, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        // 左下角
        vertexConsumer.vertex(matrix, -halfWidth, halfHeight, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }
    
    /**
     * 渲染血量填充（阵营颜色）
     */
    private static void renderHealthFill(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         float healthPercent, int factionColor) {
        if (healthPercent <= 0.0f) {
            return;
        }
        
        // 使用自定义渲染类型
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.FILL_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        // 计算填充宽度（从左向右填充）
        float halfBarWidth = BAR_WIDTH / 2.0f;
        float halfHeight = BAR_HEIGHT / 2.0f;
        
        // 左边界坐标
        float leftX = -halfBarWidth;
        // 右边界坐标（根据血量百分比计算）
        float rightX = -halfBarWidth + (BAR_WIDTH * healthPercent);
        
        int r = (factionColor >> 16) & 0xFF;
        int g = (factionColor >> 8) & 0xFF;
        int b = factionColor & 0xFF;
        int a = (factionColor >> 24) & 0xFF;
        
        // 血量填充Z坐标向负方向移动，确保显示在背景上方
        float fillZ = -0.1f;
        
        // 绘制矩形填充（从左到右）
        vertexConsumer.vertex(matrix, leftX, -halfHeight, fillZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, rightX, -halfHeight, fillZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, rightX, halfHeight, fillZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, leftX, halfHeight, fillZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }

    /**
     * 渲染等级文字（固定白色）
     */
    private static void renderLevelText(PoseStack poseStack, MultiBufferSource bufferSource, int level) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        String levelText = "Lv." + level;
        
        // 计算文字位置（血条左侧）
        float textWidth = font.width(levelText);
        float x = LEVEL_X_OFFSET - textWidth / 2.0f + 2.5f;// 左右移动，加为右减为左
        float y = -BAR_HEIGHT / 2.0f; // 轻微上移
        
        poseStack.pushPose();
        try {
            // 应用文字缩放
            float textScale = 0.5f;
            poseStack.scale(textScale, textScale, textScale);
            
            // 渲染文字（固定白色）
            font.drawInBatch(levelText, 
                x / textScale, y / textScale, 
                TEXT_COLOR, false, 
                poseStack.last().pose(), bufferSource, 
                Font.DisplayMode.NORMAL, 0, 15728880);
                
        } finally {
            poseStack.popPose();
        }
    }
    
    /**
     * 渲染血量数值文字（固定白色）
     */
    private static void renderHealthText(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        // 格式化血量文本：当前血量/最大血量
        int currentHealth = (int) Math.ceil(entity.getHealth());
        int maxHealth = (int) Math.ceil(entity.getMaxHealth());
        String healthText = currentHealth + "/" + maxHealth;
        
        // 计算文字位置（血条右侧）
        float textWidth = font.width(healthText);
        float x = BAR_WIDTH / 2.0f - 21.5f; // 血条左侧像素
        float y = -BAR_HEIGHT / 2.0f ; // 轻微上移
        
        poseStack.pushPose();
        try {
            // 应用文字缩放
            float textScale = 0.5f;
            poseStack.scale(textScale, textScale, textScale);
            
            // 渲染文字（固定白色）
            font.drawInBatch(healthText, 
                x / textScale, y / textScale, 
                TEXT_COLOR, false, 
                poseStack.last().pose(), bufferSource, 
                Font.DisplayMode.NORMAL, 0, 15728880);
                
        } finally {
            poseStack.popPose();
        }
    }
}
