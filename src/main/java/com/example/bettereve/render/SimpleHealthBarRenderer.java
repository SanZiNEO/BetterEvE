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

public class SimpleHealthBarRenderer {
    
    private static final float BAR_TOTAL_WIDTH = 80.0f;
    private static final float BAR_TOTAL_HEIGHT = 10.0f;
    
    private static final float HALF_WIDTH = BAR_TOTAL_WIDTH / 2.0f;
    private static final float HALF_HEIGHT = BAR_TOTAL_HEIGHT / 2.0f;
    
    private static final float PRE_BAR_LEFT = 0 - HALF_WIDTH;
    private static final float PRE_BAR_RIGHT = 0 + HALF_WIDTH;
    private static final float PRE_BAR_BOTTOM = 0 - HALF_HEIGHT;
    private static final float PRE_BAR_TOP = 0 + HALF_HEIGHT;
    
    // 容器层尺寸（是基础尺寸的 1.2 倍）
    private static final float CONTAINER_BAR_TOTAL_WIDTH = BAR_TOTAL_WIDTH * 1.2f;
    private static final float CONTAINER_BAR_TOTAL_HEIGHT = BAR_TOTAL_HEIGHT * 1.2f;
    
    private static final float CONTAINER_HALF_WIDTH = CONTAINER_BAR_TOTAL_WIDTH / 2.0f;
    private static final float CONTAINER_HALF_HEIGHT = CONTAINER_BAR_TOTAL_HEIGHT / 2.0f;
    
    private static final float PRE_CONTAINER_BAR_LEFT = 0 - CONTAINER_HALF_WIDTH;
    private static final float PRE_CONTAINER_BAR_RIGHT = 0 + CONTAINER_HALF_WIDTH;
    private static final float PRE_CONTAINER_BAR_BOTTOM = 0 - CONTAINER_HALF_HEIGHT;
    private static final float PRE_CONTAINER_BAR_TOP = 0 + CONTAINER_HALF_HEIGHT;
    
    private static final float BAR_Y_OFFSET = 0.2f;
    
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    
    private static final int RED_FACTION_COLOR = 0xFFFF3232;
    private static final int BLUE_FACTION_COLOR = 0xFF3264FF;
    private static final int GREEN_FACTION_COLOR = 0xFF32C832;
    // 背景颜色比对应阵营颜色浅一些（亮度增加 30%）
    private static final int RED_BG_COLOR = 0xFFFF8080;
    private static final int BLUE_BG_COLOR = 0xFF80A0FF;
    private static final int GREEN_BG_COLOR = 0xFF80E080;
    
    private static final Set<UUID> renderedThisFrame = new HashSet<>();
    private static final Map<UUID, Float> healthCache = new HashMap<>();
    
    public static void onFrameStart() {
        renderedThisFrame.clear();
    }
    
    public static boolean shouldShowHealthBar(Entity entity) {
        if (entity == null || !(entity instanceof LivingEntity)) {
            return false;
        }
        
        return !(entity instanceof Player);
    }
    
    public static void renderHealthBar(Entity entity, PoseStack poseStack, 
                                       MultiBufferSource bufferSource, float partialTick) {
        if (!shouldShowHealthBar(entity)) {
            return;
        }
        
        UUID entityId = entity.getUUID();
        if (renderedThisFrame.contains(entityId)) {
            return;
        }
        renderedThisFrame.add(entityId);
        
        LivingEntity livingEntity = (LivingEntity) entity;
        
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
            return;
        }
        
        double entityY = y + entity.getEyeHeight() + 0.8;
        
        boolean isOnScreen = toScreenCoordinates(x, entityY, z, poseStack, mc, partialTick);
        if (!isOnScreen) {
            return;
        }
        
        try {
            float healthPercent = getHealthPercent(livingEntity);
            int factionColor = getFactionColor(entity);
            int level = getEntityLevel(entity);
            boolean shouldShowLevel = shouldShowLevel(level);
            
            // 直接渲染三层，不使用模板测试
            renderBarBackground(poseStack, bufferSource, healthPercent, factionColor);
            renderHealthFill(poseStack, bufferSource, healthPercent, factionColor);
            renderBarContainer(poseStack, bufferSource);
            
            if (shouldShowLevel) {
                renderLevelText(poseStack, bufferSource, level);
            }
            
            renderHealthText(poseStack, bufferSource, livingEntity);
            
        } finally {
            poseStack.popPose();
        }
    }
    
    private static boolean toScreenCoordinates(double x, double y, double z, PoseStack poseStack, Minecraft mc, float partialTick) {
        GameRenderer gameRenderer = mc.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        
        try {
            Vec3 entityPos = new Vec3(x, y, z);
            Vec3 cameraPos = camera.getPosition();
            
            Vec3 relPos = entityPos.subtract(cameraPos);
            
            Matrix4f viewMatrix = new Matrix4f();
            viewMatrix.setLookAt(0, 0, 0, 
                               (float)camera.getLookVector().x, 
                               (float)camera.getLookVector().y, 
                               (float)camera.getLookVector().z, 
                               0, 1, 0);
            
            float fov = 70.0f;
            float aspectRatio = (float)mc.getWindow().getScreenWidth() / (float)mc.getWindow().getScreenHeight();
            float near = 0.05f;
            float far = 1000.0f;
            
            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.setPerspective((float)Math.toRadians(fov), aspectRatio, near, far);
            
            Matrix4f modelMatrix = new Matrix4f().translate((float)relPos.x, (float)relPos.y, (float)relPos.z);
            Matrix4f mvpMatrix = new Matrix4f(projectionMatrix);
            mvpMatrix.mul(viewMatrix).mul(modelMatrix);
            
            Vector4f clipPos = new Vector4f(0, 0, 0, 1);
            clipPos.mul(mvpMatrix);
            
            if (clipPos.w <= 0) {
                return false;
            }
            
            float ndcX = clipPos.x / clipPos.w;
            float ndcY = clipPos.y / clipPos.w;
            float ndcZ = clipPos.z / clipPos.w;
            
            if (ndcX < -1 || ndcX > 1 || ndcY < -1 || ndcY > 1 || ndcZ < -1 || ndcZ > 1) {
                return false;
            }
            
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            int guiScaledHeight = mc.getWindow().getGuiScaledHeight();
            
            float screenX = (ndcX + 1) * 0.5f * guiScaledWidth;
            float screenY = (1 - ndcY) * 0.5f * guiScaledHeight;
            
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
    
    private static int getBackgroundFactionColor(Entity entity) {
        String factionId = ClientFactionCache.getCachedFactionId(entity);
        
        if (factionId == null) {
            factionId = FactionHelper.getEntityFactionId(entity);
        }
        
        return switch (factionId) {
            case "red" -> RED_BG_COLOR;
            case "blue" -> BLUE_BG_COLOR;
            default -> GREEN_BG_COLOR;
        };
    }
    
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
    
    private static boolean shouldShowLevel(int level) {
        return level >= 0;
    }
    
    private static void renderHealthFill(PoseStack poseStack, MultiBufferSource bufferSource, 
                                       float healthPercent, int factionColor) {
        if (healthPercent <= 0.0f) {
            return;
        }
        
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.FILL_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
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
    
    private static void renderBarBackground(PoseStack poseStack, MultiBufferSource bufferSource, 
                                           float healthPercent, int factionColor) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.BACKGROUND_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        int bgFactionColor = switch (factionColor) {
            case RED_FACTION_COLOR -> RED_BG_COLOR;
            case BLUE_FACTION_COLOR -> BLUE_BG_COLOR;
            default -> GREEN_BG_COLOR;
        };
        
        int r = (bgFactionColor >> 16) & 0xFF;
        int g = (bgFactionColor >> 8) & 0xFF;
        int b = bgFactionColor & 0xFF;
        int a = (bgFactionColor >> 24) & 0xFF;
        
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
    
    private static void renderBarContainer(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.CONTAINER_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        
        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;
        
        float containerZ = 0.1f;
        
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_LEFT, PRE_CONTAINER_BAR_BOTTOM, containerZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_RIGHT, PRE_CONTAINER_BAR_BOTTOM, containerZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_RIGHT, PRE_CONTAINER_BAR_TOP, containerZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_LEFT, PRE_CONTAINER_BAR_TOP, containerZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }
    
    private static void renderLevelText(PoseStack poseStack, MultiBufferSource bufferSource, int level) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        String levelText = "Lv." + level;
        
        float textScale = 1.0f;
        float levelTextRawHeight = font.lineHeight;  // 原始高度（正确写法）
        float levelTextScaledHeight = levelTextRawHeight * textScale;  // 缩放后的高度
        float levelTextRawWidth = font.width(levelText);
        float levelTextScaledWidth = levelTextRawWidth * textScale;
        
        float x = 0 - HALF_WIDTH * 0.98f ;
        float y = 0 - levelTextScaledHeight * 0.5f ;
        
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
    
    private static void renderHealthText(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        int currentHealth = (int) Math.ceil(entity.getHealth());
        int maxHealth = (int) Math.ceil(entity.getMaxHealth());
        String healthText = currentHealth + "/" + maxHealth;
        
        float textScale = 0.60f;
        float healthTextRawHeight = font.lineHeight;  // 原始高度
        float healthTextScaledHeight = healthTextRawHeight * textScale;  // 缩放后的高度
        float healthTextRawWidth = font.width(healthText);
        float healthTextScaledWidth = healthTextRawWidth * textScale;
        
        float x = 0 + HALF_WIDTH * 0.98f - healthTextScaledWidth * 0.98f ;
        float y = 0 - healthTextScaledHeight*0.5f ;
        
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