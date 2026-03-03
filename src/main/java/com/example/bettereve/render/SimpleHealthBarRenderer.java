package com.example.bettereve.render;

import com.example.bettereve.faction.ClientFactionCache;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.level.LevelSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SimpleHealthBarRenderer - 简单血条渲染器
 * 实现包含等级、血量文字和血条的完整血条系统
 * 设计原则：预渲染与正式渲染严格分离，预渲染阶段纯加减法计算
 */
public class SimpleHealthBarRenderer {
    
    // 血条核心尺寸参数（配置阶段计算，写死中心点为 (0,0)）
    private static final float BAR_TOTAL_WIDTH = 60.0f;    // 血条总宽度（可配置）
    private static final float BAR_TOTAL_HEIGHT = 6.0f;    // 血条总高度（可配置）
    
    // 配置阶段计算的中间常量（仅一次计算，写死为固定值）
    private static final float HALF_WIDTH = BAR_TOTAL_WIDTH / 2.0f;  // 半宽（固定值）
    private static final float HALF_HEIGHT = BAR_TOTAL_HEIGHT / 2.0f; // 半高（固定值）
    
    // 基于中心点 (0,0) 的边缘坐标（纯加减法计算）
    private static final float PRE_BAR_LEFT = 0 - HALF_WIDTH;     // 血条左边缘X（0 - 半宽）
    private static final float PRE_BAR_RIGHT = 0 + HALF_WIDTH;    // 血条右边缘X（0 + 半宽）
    private static final float PRE_BAR_BOTTOM = 0 - HALF_HEIGHT;  // 血条下边缘Y（0 - 半高）
    private static final float PRE_BAR_TOP = 0 + HALF_HEIGHT;     // 血条上边缘Y（0 + 半高）
    
    private static final float BAR_Y_OFFSET = 0.2f;       // 实体头顶偏移（格）
    
    // 颜色定义（ARGB格式）
    private static final int BACKGROUND_COLOR = 0xB4000000; // 半透明黑色背景
    private static final int TEXT_COLOR = 0xFF000000;       // 白色文字
    
    // 阵营颜色映射
    private static final int RED_FACTION_COLOR = 0xFFFF3232;   // 红色阵营
    private static final int BLUE_FACTION_COLOR = 0xFF3264FF;  // 蓝色阵营
    private static final int GREEN_FACTION_COLOR = 0xFF32C832; // 绿色中立
    
    // 每帧渲染过的实体集合（去重）
    private static final Set<UUID> renderedThisFrame = new HashSet<>();
    
    // 血量插值缓存
    private static final Map<UUID, Float> healthCache = new HashMap<>();
    
    
    /**
     * 每帧开始时清理渲染记录
     */
    public static void onFrameStart() {
        renderedThisFrame.clear();
    }
    
    /**
     * 检查是否应该显示血条
     * @param entity 要检查的实体
     * @return 是否应该显示血条
     */
    public static boolean shouldShowHealthBar(Entity entity) {
        if (entity == null || !(entity instanceof LivingEntity)) {
            return false;
        }
        
        // 只排除玩家，其他生物都显示血条
        return !(entity instanceof Player);
    }
    
    /**
     * 渲染血条 - HUD渲染主入口方法
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
        
        // 去重检查（同一帧不重复渲染）
        UUID entityId = entity.getUUID();
        if (renderedThisFrame.contains(entityId)) {
            return;
        }
        renderedThisFrame.add(entityId);
        
        LivingEntity livingEntity = (LivingEntity) entity;
        
        // 计算实体插值位置（保持平滑动画）
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        
        // 将实体坐标转换为屏幕坐标
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
            return;
        }
        
        // 添加实体高度补偿
        double entityY = y + entity.getEyeHeight() + 0.8;
        
        // 转换为屏幕坐标
        boolean isOnScreen = toScreenCoordinates(x, entityY, z, poseStack, mc, partialTick);
        if (!isOnScreen) {
            return;
        }
        
        try {
            float healthPercent = getHealthPercent(livingEntity);
            int factionColor = getFactionColor(entity);
            int level = getEntityLevel(entity);
            boolean shouldShowLevel = shouldShowLevel(level);
            
            // 三层渲染架构：
            // 1. 最底层：血量填充（使用 bar_texture.png）
            // 2. 中层：血量背景（使用 health_bar_background_texture.png，透明通道显示下层）
            // 3. 最上层：血条顶部遮罩（使用 bar_top_texture.png，亮度掩码控制可见性）
            
            renderHealthFill(poseStack, bufferSource, healthPercent, factionColor);
            renderBarBackground(poseStack, bufferSource);
            renderBarTop(poseStack, bufferSource);
            
            if (shouldShowLevel) {
                renderLevelText(poseStack, bufferSource, level);
            }
            
            renderHealthText(poseStack, bufferSource, livingEntity);
            
        } finally {
            // 确保无论渲染是否成功，都恢复姿势堆栈
            poseStack.popPose();
        }
    }
    
    /**
     * 将实体世界坐标转换为屏幕坐标
     * @param x 实体X坐标
     * @param y 实体Y坐标
     * @param z 实体Z坐标
     * @param poseStack 姿势堆栈
     * @param mc Minecraft实例
     * @param partialTick 部分tick时间
     * @return 是否在屏幕可见范围内
     */
    private static boolean toScreenCoordinates(double x, double y, double z, PoseStack poseStack, Minecraft mc, float partialTick) {
        GameRenderer gameRenderer = mc.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        
        try {
            Vec3 entityPos = new Vec3(x, y, z);
            Vec3 cameraPos = camera.getPosition();
            
            // 计算相对于相机的位置
            Vec3 relPos = entityPos.subtract(cameraPos);
            
            // 计算视图矩阵
            Matrix4f viewMatrix = new Matrix4f();
            viewMatrix.setLookAt(0, 0, 0, 
                               (float)camera.getLookVector().x, 
                               (float)camera.getLookVector().y, 
                               (float)camera.getLookVector().z, 
                               0, 1, 0);
            
            // 计算投影矩阵（使用默认 FOV 值）
            float fov = 70.0f; // Minecraft 默认 FOV
            float aspectRatio = (float)mc.getWindow().getScreenWidth() / (float)mc.getWindow().getScreenHeight();
            float near = 0.05f;
            float far = 1000.0f;
            
            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.setPerspective((float)Math.toRadians(fov), aspectRatio, near, far);
            
            // 转换到齐次坐标
            Matrix4f modelMatrix = new Matrix4f().translate((float)relPos.x, (float)relPos.y, (float)relPos.z);
            Matrix4f mvpMatrix = new Matrix4f(projectionMatrix);
            mvpMatrix.mul(viewMatrix).mul(modelMatrix);
            
            Vector4f clipPos = new Vector4f(0, 0, 0, 1);
            clipPos.mul(mvpMatrix);
            
            if (clipPos.w <= 0) {
                return false; // 在相机后面
            }
            
            // 透视除法
            float ndcX = clipPos.x / clipPos.w;
            float ndcY = clipPos.y / clipPos.w;
            float ndcZ = clipPos.z / clipPos.w;
            
            // 检查可见性
            if (ndcX < -1 || ndcX > 1 || ndcY < -1 || ndcY > 1 || ndcZ < -1 || ndcZ > 1) {
                return false;
            }
            
            // 转换到屏幕坐标
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            int guiScaledHeight = mc.getWindow().getGuiScaledHeight();
            
            float screenX = (ndcX + 1) * 0.5f * guiScaledWidth;
            float screenY = (1 - ndcY) * 0.5f * guiScaledHeight;
            
            // 计算距离和缩放
            double distance = relPos.length();
            double lockedDistance = 5.0;
            
            float finalScale = (float)(0.02666667 * lockedDistance / distance);
            finalScale = Math.max(finalScale, 0.5f);
            
            poseStack.pushPose();
            poseStack.translate(screenX, screenY, 0);
            poseStack.scale(finalScale, finalScale, finalScale);
            
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取实体血量百分比（带插值动画）
     */
    private static float getHealthPercent(LivingEntity entity) {
        UUID entityId = entity.getUUID();
        
        if (!healthCache.containsKey(entityId)) {
            healthCache.put(entityId, entity.getHealth());
        }
        
        float previousHealth = healthCache.get(entityId);
        float currentHealth = entity.getHealth();
        
        if (previousHealth != currentHealth) {
            float interpolatedHealth = previousHealth + (currentHealth - previousHealth) * 0.1f;
            healthCache.put(entityId, interpolatedHealth);
            
            if (Math.abs(interpolatedHealth - currentHealth) < 0.1f) {
                healthCache.put(entityId, currentHealth);
                interpolatedHealth = currentHealth;
            }
            
            currentHealth = interpolatedHealth;
        }
        
        float maxHealth = entity.getMaxHealth();
        return maxHealth <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, currentHealth / maxHealth));
    }
    
    /**
     * 获取阵营颜色
     */
    private static int getFactionColor(Entity entity) {
        String factionId = ClientFactionCache.getCachedFactionId(entity);
        
        if (factionId == null) {
            factionId = FactionHelper.getEntityFactionId(entity);
        }
        
        return switch (factionId) {
            case "red" -> RED_FACTION_COLOR;
            case "blue" -> BLUE_FACTION_COLOR;
            default -> GREEN_FACTION_COLOR;
        };
    }
    
    /**
     * 获取实体等级
     */
    private static int getEntityLevel(Entity entity) {
        try {
            if (entity instanceof LivingEntity) {
                if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
                    int cachedLevel = com.example.bettereve.network.LevelSyncPacket.ClientLevelCache.getEntityLevel(entity.getUUID());
                    if (cachedLevel > 0) {
                        return cachedLevel;
                    }
                }
                
                return LevelSystem.getEntityLevel((LivingEntity) entity);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * 检查是否应该显示等级
     */
    private static boolean shouldShowLevel(int level) {
        return level >= 0;
    }
    
    /**
     * 渲染血条背景（使用 health_bar_background_texture.png，透明通道显示下层）
     */
    private static void renderBarBackground(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.BACKGROUND_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        int r = (BACKGROUND_COLOR >> 16) & 0xFF;
        int g = (BACKGROUND_COLOR >> 8) & 0xFF;
        int b = BACKGROUND_COLOR & 0xFF;
        int a = (BACKGROUND_COLOR >> 24) & 0xFF;
        
        float backgroundZ = 0.0f;
        
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_BOTTOM, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_BOTTOM, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_TOP, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_TOP, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }
    
    /**
     * 渲染血量填充（使用 bar_texture.png）
     */
    private static void renderHealthFill(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         float healthPercent, int factionColor) {
        if (healthPercent <= 0.0f) {
            return;
        }
        
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.FILL_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        // 纯加减法计算填充右边缘（基于总宽度）
        float fillRight = PRE_BAR_LEFT + (BAR_TOTAL_WIDTH * healthPercent);
        
        int r = (factionColor >> 16) & 0xFF;
        int g = (factionColor >> 8) & 0xFF;
        int b = factionColor & 0xFF;
        int a = (factionColor >> 24) & 0xFF;
        
        float fillZ = -0.1f;
        
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_BOTTOM, fillZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, fillRight, PRE_BAR_BOTTOM, fillZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, fillRight, PRE_BAR_TOP, fillZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_TOP, fillZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }
    
    /**
     * 渲染血条顶部遮罩（使用 bar_top_texture.png，反向亮度掩码控制可见性）
     * 黑色像素：显示下层内容，白色像素：完全透明
     */
    private static void renderBarTop(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.TOP_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        // 使用白色作为基础颜色，实现反向亮度掩码效果
        int r = 255; // 红色分量
        int g = 255; // 绿色分量
        int b = 255; // 蓝色分量
        int a = 255; // 初始不透明
        
        float topZ = 0.1f;
        
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_BOTTOM, topZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_BOTTOM, topZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_TOP, topZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_TOP, topZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }

    /**
     * 渲染等级文字 - 预渲染阶段，纯加减法计算文字定位
     */
    private static void renderLevelText(PoseStack poseStack, MultiBufferSource bufferSource, int level) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        String levelText = "Lv." + level;
        
        float textScale = 0.7f;
        float levelTextRawWidth = font.width(levelText);
        float levelTextScaledWidth = levelTextRawWidth * textScale;
        
        float x = 0 - HALF_WIDTH + 2.0f ; // 注释掉位置，查看定位点- (levelTextScaledWidth / 2.0f);
        float y = PRE_BAR_BOTTOM + 0.2f;// 注释掉位置，查看定位点PRE_BAR_BOTTOM;
        
        poseStack.pushPose();
        try {
            poseStack.scale(textScale, textScale, textScale);
            
            font.drawInBatch(levelText, 
                x / textScale, y / textScale, 
                TEXT_COLOR, false, 
                poseStack.last().pose(), bufferSource, 
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
                
        } finally {
            poseStack.popPose();
        }
    }
    
    /**
     * 渲染血量数值文字 - 预渲染阶段，纯加减法计算文字定位
     */
    private static void renderHealthText(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        int currentHealth = (int) Math.ceil(entity.getHealth());
        int maxHealth = (int) Math.ceil(entity.getMaxHealth());
        String healthText = currentHealth + "/" + maxHealth;
        
        float textScale = 0.5f;
        float healthTextRawWidth = font.width(healthText);
        float healthTextScaledWidth = healthTextRawWidth * textScale;
        
        float x = 0 + HALF_WIDTH - healthTextScaledWidth - 2.0f;
        float y = PRE_BAR_BOTTOM + 0.2f ;
        
        poseStack.pushPose();
        try {
            poseStack.scale(textScale, textScale, textScale);
            
            font.drawInBatch(healthText, 
                x / textScale, y / textScale, 
                TEXT_COLOR, false, 
                poseStack.last().pose(), bufferSource, 
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
                
        } finally {
            poseStack.popPose();
        }
    }
}