package com.example.bettereve.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.*;

public class HealthBarRenderType extends RenderStateShard {
    
    // 血量填充纹理（中层）
    public static final ResourceLocation BAR_TEXTURE = new ResourceLocation("bettereve", "textures/ui/bar_texture.png");
    // 血量背景纹理（底层）
    public static final ResourceLocation BAR_BACKGROUND_TEXTURE = new ResourceLocation("bettereve", "textures/ui/bar_background_texture.png");
    // 血条容器纹理（上层）
    public static final ResourceLocation BAR_CONTAINER_TEXTURE = new ResourceLocation("bettereve", "textures/ui/bar_container_texture.png");
    
    // 血量填充渲染类型（中层）
    public static final RenderType FILL_TYPE = getFillType();
    
    // 血量背景渲染类型（底层）
    public static final RenderType BACKGROUND_TYPE = getBackgroundType();
    
    // 血条容器渲染类型（上层，带透明通道）
    public static final RenderType CONTAINER_TYPE = getContainerType();
    
    private HealthBarRenderType(String string, Runnable r, Runnable r1) {
        super(string, r, r1);
    }
    
    /**
     * 获取血量填充渲染类型（中层）
     */
    private static RenderType getFillType() {
        RenderType.CompositeState renderTypeState = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(new TextureStateShard(BAR_TEXTURE, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
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
    
    /**
     * 获取血量背景渲染类型（底层）
     */
    private static RenderType getBackgroundType() {
        RenderType.CompositeState renderTypeState = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(new TextureStateShard(BAR_BACKGROUND_TEXTURE, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
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
     * 获取血条容器渲染类型（上层，带透明通道）
     */
    private static RenderType getContainerType() {
        RenderType.CompositeState renderTypeState = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(new TextureStateShard(BAR_CONTAINER_TEXTURE, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false);
                
        return RenderType.create("bettereve_health_bar_container", 
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 
                VertexFormat.Mode.QUADS, 
                256, 
                true, 
                false, 
                renderTypeState);
    }
}

