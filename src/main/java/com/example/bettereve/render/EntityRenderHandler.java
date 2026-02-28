package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityRenderHandler {
    @SubscribeEvent
    public void onRenderNameTag(RenderNameTagEvent event) {
        // 暂时取消所有名称标签渲染，等待血条系统重新设计
        event.setCanceled(true);
    }
}