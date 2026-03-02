package com.example.bettereve;

import com.example.bettereve.config.BetterEvEConfig;
import com.example.bettereve.mixin.AccessorRangedAttribute;
import com.example.bettereve.network.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Bettereve.MODID)
public class Bettereve {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "bettereve";

    public Bettereve() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BetterEvEConfig.COMMON_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 初始化网络系统
        event.enqueueWork(NetworkManager::init);
        
        // 修改生物属性上限
        event.enqueueWork(() -> {
            // 修改最大生命值上限
            if (Attributes.MAX_HEALTH instanceof RangedAttribute) {
                AccessorRangedAttribute healthAccessor = (AccessorRangedAttribute) (Object) Attributes.MAX_HEALTH;
                healthAccessor.setMaxValue(50000000.0); // 设置为 50000000
            }
            
            // 修改攻击力上限
            if (Attributes.ATTACK_DAMAGE instanceof RangedAttribute) {
                AccessorRangedAttribute attackAccessor = (AccessorRangedAttribute) (Object) Attributes.ATTACK_DAMAGE;
                attackAccessor.setMaxValue(50000000.0); // 设置为 50000000
            }
        });
        
        // 初始化配置
        // 检查核心配置是否已加载
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 客户端设置
        }

        static {
            // 注册配置屏幕工厂，使 Mods 页面的配置按钮可以跳转 GUI
            ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, screen) ->
                            new com.example.bettereve.config.BetterEvEConfigScreen(screen)
                    )
            );
        }
    }
}