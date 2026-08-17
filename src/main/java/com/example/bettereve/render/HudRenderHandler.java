package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD 血条渲染已废弃。
 * <p>
 * 血条已改为世界空间实体化渲染（见 SimpleHealthBarRenderer + LevelRendererMixin），
 * 不再注册 GUI Overlay，避免与光影模组不兼容以及重复渲染。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HudRenderHandler {
}
