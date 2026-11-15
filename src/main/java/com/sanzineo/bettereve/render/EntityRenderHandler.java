package com.sanzineo.bettereve.render;

import com.sanzineo.bettereve.Bettereve;
import com.sanzineo.bettereve.faction.ClientEntityMapping;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class EntityRenderHandler {
    
    /**
     * 在渲染名称标签时记录实体映射关系
     * 确保ClientEntityMapping中有实体ID到UUID的映射
     */
    @SubscribeEvent
    public void onRenderNameTag(RenderNameTagEvent event) {
        // 记录实体映射关系（用于阵营和等级缓存的键值转换）
        ClientEntityMapping.recordEntityMapping(event.getEntity());
    }
}
