package com.example.bettereve.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.*;

public class HealthBarRenderType extends RenderStateShard {
    
    public static final ResourceLocation HEALTH_BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("bettereve", "textures/ui/health_bar_texture.png");
    
    // 背景渲染类型（半透明黑色背景）
    public static final RenderType BACKGROUND_TYPE = getBackgroundType();
    
    // 血量填充渲染类型（阵营颜色）
    public static final RenderType FILL_TYPE = getFillType();
    
    private HealthBarRenderType(String string, Runnable r, Runnable r1) {
        super(string, r, r1);
    }
    
    /**
     * 获取背景渲染类型
     */
    private static RenderType getBackgroundType() {
        RenderType.CompositeState renderTypeState = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(new TextureStateShard(HEALTH_BAR_TEXTURE, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST) // 取消深度测试
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false);
                
        return RenderType.create("bettereve_health_bar_background", 
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 
                VertexFormat.Mode.QUADS, 
                256, 
                true, 
                false, 
                renderTypeState);
    }
    
    /**
     * 获取血量填充渲染类型
     */
    private static RenderType getFillType() {
        RenderType.CompositeState renderTypeState = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(new TextureStateShard(HEALTH_BAR_TEXTURE, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST) // 取消深度测试
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false);
                
        return RenderType.create("bettereve_health_bar_fill", 
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 
                VertexFormat.Mode.QUADS, 
                256, 
                true, 
                false, 
                renderTypeState);
    }
}

