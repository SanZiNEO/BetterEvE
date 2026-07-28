package com.example.bettereve.timer;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.level.LevelSystem;
import com.example.bettereve.network.NetworkManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * 同步定时器
 * 负责定时读取NBT存储的实体信息并同步到客户端
 * 每40tick（约0.66秒）执行一次同步
 */
@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SyncTimer {
    private static final int SYNC_INTERVAL = 40; // 同步间隔（40个游戏刻）
    private static int tickCounter = 0;
    
    // 已经同步过的实体ID集合，避免重复同步
    private static final Set<Integer> synchronizedEntities = new HashSet<>();
    
    /**
     * 服务器tick事件监听器
     * @param event 服务器tick事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 只在服务器端执行
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // 增加计数器
        tickCounter++;
        
        // 检查是否达到同步间隔
        if (tickCounter >= SYNC_INTERVAL) {
            // 执行同步操作
            performSync(event);
            
            // 重置计数器
            tickCounter = 0;
        }
    }
    
    /**
     * 执行同步操作
     */
    private static void performSync(TickEvent.ServerTickEvent event) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        // 遍历所有加载的维度
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            // 遍历维度中的所有实体
            for (Entity entity : level.getAllEntities()) {
                // 只处理LivingEntity类型的实体
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    
                    // 同步实体的等级信息
                    syncEntityLevel(livingEntity);
                    
                    // 同步实体的阵营信息
                    syncEntityFaction(livingEntity);
                    
                    // 添加到已同步集合
                    synchronizedEntities.add(entity.getId());
                }
            }
        }
    }
    
    /**
     * 同步单个实体的等级信息
     */
    private static void syncEntityLevel(LivingEntity entity) {
        try {
            // 从NBT读取等级信息（确保获取最新数据）
            int level = LevelSystem.getEntityLevel(entity);
            
            // 同步到所有客户端
            NetworkManager.syncEntityLevelToAll(entity, level);
        } catch (Exception e) {
            // 忽略同步过程中的异常，避免游戏崩溃
        }
    }
    
    /**
     * 同步单个实体的阵营信息
     */
    private static void syncEntityFaction(LivingEntity entity) {
        try {
            // 从NBT读取阵营信息（确保获取最新数据）
            String factionId = FactionHelper.getEntityFactionId(entity);
            
            // 同步到所有客户端
            NetworkManager.syncEntityFactionToAll(entity);
        } catch (Exception e) {
            // 忽略同步过程中的异常，避免游戏崩溃
        }
    }
    
    /**
     * 检查实体是否已同步
     */
    public static boolean isEntitySynced(int entityId) {
        return synchronizedEntities.contains(entityId);
    }
    
    /**
     * 清除已同步实体集合（用于维度卸载时）
     */
    public static void clearSyncedEntities() {
        synchronizedEntities.clear();
    }
    
    /**
     * 获取同步统计信息
     */
    public static String getSyncStatistics() {
        return String.format("Sync Timer: tickCounter=%d, syncedEntities=%d", 
            tickCounter, synchronizedEntities.size());
    }
}