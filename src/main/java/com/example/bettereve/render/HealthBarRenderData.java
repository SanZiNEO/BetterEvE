package com.example.bettereve.render;

import java.util.UUID;

/**
 * HealthBarRenderData - 血条渲染数据类
 * 完整封装血条和文字的所有渲染参数，用于预渲染和队列提交
 * 实现血条（背景 + 填充）和文字（等级 + 血量）的统一封装
 */
public class HealthBarRenderData {
    
    // 实体唯一标识
    private final UUID entityId;
    
    // 血条参数
    private final float healthPercent;
    private final int factionColor;
    private final int currentHealth;
    private final int maxHealth;
    private final float interpolationProgress;
    
    // 位置与缩放
    private final double renderX;
    private final double renderY;
    private final double renderZ;
    private final float scale;
    private final float barYOffset;
    
    // 文字参数
    private final String levelText;
    private final String healthText;
    private final float textRenderScale;
    
    // 预渲染计算出的文字尺寸和位置（纯加减法计算结果）
    private final float levelTextWidth;
    private final float healthTextWidth;
    private final float levelTextX;
    private final float levelTextY;
    private final float healthTextX;
    private final float healthTextY;
    
    public HealthBarRenderData(UUID entityId, float healthPercent, int factionColor, 
                              int currentHealth, int maxHealth, float interpolationProgress,
                              double renderX, double renderY, double renderZ, float scale, 
                              float barYOffset, String levelText, String healthText, 
                              float textRenderScale,
                              float levelTextWidth, float healthTextWidth,
                              float levelTextX, float levelTextY,
                              float healthTextX, float healthTextY) {
        this.entityId = entityId;
        this.healthPercent = healthPercent;
        this.factionColor = factionColor;
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.interpolationProgress = interpolationProgress;
        this.renderX = renderX;
        this.renderY = renderY;
        this.renderZ = renderZ;
        this.scale = scale;
        this.barYOffset = barYOffset;
        this.levelText = levelText;
        this.healthText = healthText;
        this.textRenderScale = textRenderScale;
        this.levelTextWidth = levelTextWidth;
        this.healthTextWidth = healthTextWidth;
        this.levelTextX = levelTextX;
        this.levelTextY = levelTextY;
        this.healthTextX = healthTextX;
        this.healthTextY = healthTextY;
    }
    
    // Getters
    public UUID getEntityId() {
        return entityId;
    }
    
    public float getHealthPercent() {
        return healthPercent;
    }
    
    public int getFactionColor() {
        return factionColor;
    }
    
    public int getCurrentHealth() {
        return currentHealth;
    }
    
    public int getMaxHealth() {
        return maxHealth;
    }
    
    public float getInterpolationProgress() {
        return interpolationProgress;
    }
    
    public double getRenderX() {
        return renderX;
    }
    
    public double getRenderY() {
        return renderY;
    }
    
    public double getRenderZ() {
        return renderZ;
    }
    
    public float getScale() {
        return scale;
    }
    
    public float getBarYOffset() {
        return barYOffset;
    }
    
    public String getLevelText() {
        return levelText;
    }
    
    public String getHealthText() {
        return healthText;
    }
    
    public float getTextRenderScale() {
        return textRenderScale;
    }
    
    public float getLevelTextWidth() {
        return levelTextWidth;
    }
    
    public float getHealthTextWidth() {
        return healthTextWidth;
    }
    
    public float getLevelTextX() {
        return levelTextX;
    }
    
    public float getLevelTextY() {
        return levelTextY;
    }
    
    public float getHealthTextX() {
        return healthTextX;
    }
    
    public float getHealthTextY() {
        return healthTextY;
    }
    
    @Override
    public String toString() {
        return "HealthBarRenderData{" +
                "entityId=" + entityId +
                ", healthPercent=" + healthPercent +
                ", factionColor=" + factionColor +
                ", currentHealth=" + currentHealth +
                ", maxHealth=" + maxHealth +
                ", interpolationProgress=" + interpolationProgress +
                ", renderX=" + renderX +
                ", renderY=" + renderY +
                ", renderZ=" + renderZ +
                ", scale=" + scale +
                ", barYOffset=" + barYOffset +
                ", levelText='" + levelText + '\'' +
                ", healthText='" + healthText + '\'' +
                ", textRenderScale=" + textRenderScale +
                ", levelTextWidth=" + levelTextWidth +
                ", healthTextWidth=" + healthTextWidth +
                ", levelTextX=" + levelTextX +
                ", levelTextY=" + levelTextY +
                ", healthTextX=" + healthTextX +
                ", healthTextY=" + healthTextY +
                '}';
    }
}