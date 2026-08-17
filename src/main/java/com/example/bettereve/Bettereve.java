package com.example.bettereve;

import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.entity.CrystalEntityModel;
import com.example.bettereve.entity.CrystalEntityType;
import com.example.bettereve.mixin.AccessorRangedAttribute;
import com.example.bettereve.network.NetworkManager;
import com.example.bettereve.scenario.ScenarioIO;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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


        // 注册实体类型
        CrystalEntityType.ENTITY_TYPES.register(modEventBus);
        
        // 注册实体属性
        modEventBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(final net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        // 为所有不同尺寸的实体类型注册属性
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.EntityType<CrystalEntity>> entityType : 
             CrystalEntityType.getAllEntityTypes()) {
            event.put(entityType.get(), CrystalEntity.createAttributes().build());
        }
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
        
        // 确保剧本目录存在
        event.enqueueWork(ScenarioIO::getScenarioDir);
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
            // 血条已改为世界空间实体化渲染，不再注册 HUD overlay
        }

    @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            // 为所有不同尺寸的实体类型注册渲染器
            for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.EntityType<CrystalEntity>> entityType : 
                 CrystalEntityType.getAllEntityTypes()) {
                event.registerEntityRenderer(entityType.get(), com.example.bettereve.render.CrystalEntity.CrystalEntityRenderer::new);
            }
        }

    @SubscribeEvent
        public static void onRegisterLayerDefinitions(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(CrystalEntityModel.LAYER_LOCATION, CrystalEntityModel::createBodyLayer);
        }

    }
}
