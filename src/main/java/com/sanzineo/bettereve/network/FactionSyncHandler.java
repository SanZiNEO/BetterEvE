package com.sanzineo.bettereve.network;

import com.sanzineo.bettereve.faction.FactionHelper;
import com.sanzineo.bettereve.entitylv.EntityLevelSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阵营同步事件处理器
 * 监听游戏事件并触发智能同步
 */
@Mod.EventBusSubscriber
public class FactionSyncHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/FactionSyncHandler");
    
    // 同步计数器（用于控制同步频率）
    private static int syncTickCounter = 0;
    private static final int SYNC_TICK_INTERVAL = 20; // 每20 tick（1秒）处理一次批量同步
    
    /**
     * 当实体加入世界时触发同步
     * 确保新生成的实体立即同步到客户端
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return; // 只在服务器端处理
        }
        
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        
        // 同步阵营信息
        String factionId = FactionHelper.getEntityFactionId(entity);
        if (factionId != null && !factionId.isEmpty()) {
            NetworkManager.queueEntityForSync(entity);
        }
        
        // 同步血条可见性信息
        boolean showHealthBar = FactionHelper.getEntityShowHealthBar(entity);
        NetworkManager.syncEntityHealthBarVisibilityToAll(entity, showHealthBar);

        // 同步血条文字显示开关（Lv/血量数字可分别控制）
        NetworkManager.syncEntityHealthBarTextVisibilityToAll(
                entity,
                FactionHelper.getEntityShowLevelText(entity),
                FactionHelper.getEntityShowHealthText(entity));

        // 同步等级信息（实体加入世界后补发一次，替代原 SyncTimer 的全量遍历）
        NetworkManager.syncEntityLevelToAll(entity, EntityLevelSystem.getEntityLevel(entity));
    }
    
    /**
     * 服务器tick事件，用于处理批量同步队列
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // 只在服务器端处理
        if (event.getServer() == null || event.getServer().isSingleplayer()) {
            return;
        }
        
        // 控制同步频率
        syncTickCounter++;
        if (syncTickCounter >= SYNC_TICK_INTERVAL) {
            NetworkManager.processSyncQueue();
            syncTickCounter = 0;
        }
    }
    
    /**
     * 玩家登录时同步附近实体的阵营信息
     */
    @SubscribeEvent
    public static void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        
        Player player = event.getEntity();
        
        // 同步玩家附近的所有实体（半径32格）：血条/文字开关对所有活体同步，阵营/等级只对非中立同步
        player.level().getEntities(player, player.getBoundingBox().inflate(32.0), entity -> entity instanceof LivingEntity)
            .forEach(entity -> {
                NetworkManager.syncEntityHealthBarVisibility(entity, FactionHelper.getEntityShowHealthBar(entity), (net.minecraft.server.level.ServerPlayer) player);
                NetworkManager.syncEntityHealthBarTextVisibility(
                        entity,
                        FactionHelper.getEntityShowLevelText(entity),
                        FactionHelper.getEntityShowHealthText(entity),
                        (net.minecraft.server.level.ServerPlayer) player);
                String factionId = FactionHelper.getEntityFactionId(entity);
                if (factionId != null && !factionId.isEmpty()) {
                    NetworkManager.syncEntityFaction(entity, (net.minecraft.server.level.ServerPlayer) player);
                    NetworkManager.syncEntityLevel(entity, EntityLevelSystem.getEntityLevel(entity), (net.minecraft.server.level.ServerPlayer) player);
                }
            });
    }
    
    /**
     * 玩家改变维度时同步新维度中的实体
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        
        Player player = event.getEntity();
        
        // 同步新维度中的实体：血条/文字开关对所有活体同步，阵营/等级只对非中立同步
        player.level().getEntities(player, player.getBoundingBox().inflate(32.0), entity -> entity instanceof LivingEntity)
            .forEach(entity -> {
                NetworkManager.syncEntityHealthBarVisibility(entity, FactionHelper.getEntityShowHealthBar(entity), (net.minecraft.server.level.ServerPlayer) player);
                NetworkManager.syncEntityHealthBarTextVisibility(
                        entity,
                        FactionHelper.getEntityShowLevelText(entity),
                        FactionHelper.getEntityShowHealthText(entity),
                        (net.minecraft.server.level.ServerPlayer) player);
                String factionId = FactionHelper.getEntityFactionId(entity);
                if (factionId != null && !factionId.isEmpty()) {
                    NetworkManager.syncEntityFaction(entity, (net.minecraft.server.level.ServerPlayer) player);
                    NetworkManager.syncEntityLevel(entity, EntityLevelSystem.getEntityLevel(entity), (net.minecraft.server.level.ServerPlayer) player);
                }
            });
    }
    
    /**
     * 手动触发实体同步（供其他代码调用）
     * @param entity 要同步的实体
     */
    public static void syncEntity(Entity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        
        NetworkManager.queueEntityForSync(entity);
    }
    
    /**
     * 立即同步实体（强制发送）
     * @param entity 要同步的实体
     */
    public static void syncEntityImmediately(Entity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        
        NetworkManager.syncEntityFactionToAll(entity);
    }
    
    /**
     * 获取同步统计信息
     */
    public static String getStatistics() {
        return String.format("SyncHandler: tickCounter=%d, queueSize=%d", 
            syncTickCounter, NetworkManager.getSyncQueueSize());
    }
}