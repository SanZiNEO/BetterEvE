package com.example.bettereve.network;
import com.example.bettereve.Bettereve;
import com.example.bettereve.network.CrystalDeathPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
public class NetworkManager {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/NetworkManager");
    
    // 网络协议版本
    private static final String PROTOCOL_VERSION = "1";
    
    // 网络通道
    private static SimpleChannel CHANNEL;
    
    // 数据包ID计数器
    private static int packetId = 0;
    
    // 同步队列（用于批量同步）
    private static final Map<Integer, String> syncQueue = new ConcurrentHashMap<>();
    private static long lastBatchSyncTime = 0;
    private static final long BATCH_SYNC_INTERVAL_MS = 1000; // 1秒
    
    /**
     * 初始化网络通道
     */
    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Bettereve.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );
        
        // 注册数据包
        registerPackets();
    }
    
    /**
     * 注册所有数据包
     */
    private static void registerPackets() {
        // 注册阵营同步数据包
        CHANNEL.registerMessage(
            packetId++,
            FactionSyncPacket.class,
            FactionSyncPacket::encode,
            FactionSyncPacket::decode,
            FactionSyncPacket::handle
        );
        
        // 注册等级同步数据包
        CHANNEL.registerMessage(
            packetId++,
            LevelSyncPacket.class,
            LevelSyncPacket::encode,
            LevelSyncPacket::decode,
            LevelSyncPacket::handle
        );
        
        // 注册血条缩放同步数据包
        CHANNEL.registerMessage(
            packetId++,
            HealthBarScalePacket.class,
            HealthBarScalePacket::encode,
            HealthBarScalePacket::decode,
            HealthBarScalePacket::handle
        );
        
        // 注册血条可见性同步数据包
        CHANNEL.registerMessage(
            packetId++,
            HealthBarVisibilityPacket.class,
            HealthBarVisibilityPacket::encode,
            HealthBarVisibilityPacket::decode,
            HealthBarVisibilityPacket::handle
        );

        // 注册血条文字显示开关同步数据包
        CHANNEL.registerMessage(
            packetId++,
            HealthBarTextVisibilityPacket.class,
            HealthBarTextVisibilityPacket::encode,
            HealthBarTextVisibilityPacket::decode,
            HealthBarTextVisibilityPacket::handle
        );
        
        // 注册水晶基地死亡同步数据包
        CHANNEL.registerMessage(
            packetId++,
            CrystalDeathPacket.class,
            CrystalDeathPacket::encode,
            CrystalDeathPacket::decode,
            CrystalDeathPacket::handle
        );
        
        // 注册清除死亡计数数据包
        CHANNEL.registerMessage(
            packetId++,
            ClearDeathCountPacket.class,
            ClearDeathCountPacket::encode,
            ClearDeathCountPacket::new,
            ClearDeathCountPacket::handle
        );
        // 注册实体击杀数据包
        CHANNEL.registerMessage(
            packetId++,
            EntityKillPacket.class,
            EntityKillPacket::encode,
            EntityKillPacket::new,
            EntityKillPacket::handle
        );
    }
    /**
     * 同步单个实体的血条可见性到客户端
     * @param entity 要同步的实体
     * @param player 目标玩家（null表示所有玩家）
     */
    public static void syncEntityHealthBarVisibility(Entity entity, boolean showHealthBar, ServerPlayer player) {
        if (entity == null) {
            return;
        }
        
        // 创建数据包
        HealthBarVisibilityPacket packet = new HealthBarVisibilityPacket(entity.getUUID(), showHealthBar);
        
        // 发送数据包
        if (player != null) {
            // 发送给特定玩家
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        } else {
            // 发送给所有玩家
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }
    
    /**
     * 同步单个实体的血条可见性到所有客户端
     */
    public static void syncEntityHealthBarVisibilityToAll(Entity entity, boolean showHealthBar) {
        syncEntityHealthBarVisibility(entity, showHealthBar, null);
    }
    
    /**
     * 批量同步实体的血条可见性
     * @param entityVisibilityMap 实体UUID到可见性的映射
     */
    public static void syncBatchHealthBarVisibility(java.util.Map<java.util.UUID, java.lang.Boolean> entityVisibilityMap) {
        if (entityVisibilityMap == null || entityVisibilityMap.isEmpty()) {
            return;
        }
        
        // 创建数据包
        HealthBarVisibilityPacket packet = new HealthBarVisibilityPacket(entityVisibilityMap);
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 清除客户端血条可见性缓存
     */
    public static void clearClientHealthBarVisibilityCache() {
        // 创建数据包
        HealthBarVisibilityPacket packet = new HealthBarVisibilityPacket();
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * 同步单个实体的 Lv/血量文字显示开关到客户端
     */
    public static void syncEntityHealthBarTextVisibility(Entity entity, boolean showLevelText, boolean showHealthText, ServerPlayer player) {
        if (entity == null) {
            return;
        }
        HealthBarTextVisibilityPacket packet = new HealthBarTextVisibilityPacket(entity.getUUID(), showLevelText, showHealthText);
        if (player != null) {
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        } else {
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    /**
     * 同步单个实体的 Lv/血量文字显示开关到所有客户端
     */
    public static void syncEntityHealthBarTextVisibilityToAll(Entity entity, boolean showLevelText, boolean showHealthText) {
        syncEntityHealthBarTextVisibility(entity, showLevelText, showHealthText, null);
    }
    
    /**
     * 获取网络通道
     */
    public static SimpleChannel getChannel() {
        return CHANNEL;
    }
    
    /**
     * 同步单个实体的阵营信息到客户端
     * @param entity 要同步的实体
     * @param player 目标玩家（null表示所有玩家）
     */
    public static void syncEntityFaction(Entity entity, ServerPlayer player) {
        if (entity == null) {
            return;
        }
        
        // 获取实体阵营ID（从NBT）
        String factionId = com.example.bettereve.faction.FactionHelper.getEntityFactionId(entity);
        int entityId = entity.getId();
        
        // 创建数据包
        FactionSyncPacket packet = new FactionSyncPacket(entityId, factionId);
        
        // 发送数据包
        if (player != null) {
            // 发送给特定玩家
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        } else {
            // 发送给所有玩家
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }
    
    /**
     * 同步单个实体的阵营信息到所有客户端
     */
    public static void syncEntityFactionToAll(Entity entity) {
        syncEntityFaction(entity, null);
    }
    
    /**
     * 批量同步实体阵营信息
     * @param entityFactions 实体ID到阵营ID的映射
     */
    public static void syncBatchFactions(Map<Integer, String> entityFactions) {
        if (entityFactions == null || entityFactions.isEmpty()) {
            return;
        }
        
        // 创建数据包
        FactionSyncPacket packet = new FactionSyncPacket(entityFactions);
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 清除客户端缓存
     */
    public static void clearClientCache() {
        // 创建数据包
        FactionSyncPacket packet = new FactionSyncPacket();
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 将实体添加到同步队列（用于批量同步优化）
     * @param entity 要同步的实体
     */
    public static void queueEntityForSync(Entity entity) {
        if (entity == null) {
            return;
        }
        
        String factionId = com.example.bettereve.faction.FactionHelper.getEntityFactionId(entity);
        syncQueue.put(entity.getId(), factionId);
    }
    
    /**
     * 处理同步队列（批量发送）
     * 应该在游戏循环中定期调用
     */
    public static void processSyncQueue() {
        long now = System.currentTimeMillis();
        
        // 检查是否达到批量同步时间间隔
        if (now - lastBatchSyncTime < BATCH_SYNC_INTERVAL_MS) {
            return;
        }
        
        if (syncQueue.isEmpty()) {
            return;
        }
        
        // 创建批量同步数据包
        Map<Integer, String> batch = new HashMap<>(syncQueue);
        syncBatchFactions(batch);
        
        // 清空队列并更新时间
        syncQueue.clear();
        lastBatchSyncTime = now;
    }
    
    /**
     * 立即处理同步队列（强制发送）
     */
    public static void flushSyncQueue() {
        if (syncQueue.isEmpty()) {
            return;
        }
        
        Map<Integer, String> batch = new HashMap<>(syncQueue);
        syncBatchFactions(batch);
        syncQueue.clear();
        lastBatchSyncTime = System.currentTimeMillis();
    }
    
    /**
     * 获取同步队列大小
     */
    public static int getSyncQueueSize() {
        return syncQueue.size();
    }
    
    /**
     * 同步单个实体的等级信息到客户端
     * @param entity 要同步的实体
     * @param player 目标玩家（null表示所有玩家）
     */
    public static void syncEntityLevel(Entity entity, int level, ServerPlayer player) {
        if (entity == null) {
            return;
        }
        
        // 创建数据包
        LevelSyncPacket packet = new LevelSyncPacket(entity.getUUID(), level);
        
        // 发送数据包
        if (player != null) {
            // 发送给特定玩家
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        } else {
            // 发送给所有玩家
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }
    
    /**
     * 同步单个实体的等级信息到所有客户端
     */
    public static void syncEntityLevelToAll(Entity entity, int level) {
        syncEntityLevel(entity, level, null);
    }
    
    /**
     * 批量同步实体等级信息
     * @param entityLevels 实体UUID到等级的映射
     */
    public static void syncBatchLevels(java.util.Map<java.util.UUID, Integer> entityLevels) {
        if (entityLevels == null || entityLevels.isEmpty()) {
            return;
        }
        
        // 创建数据包
        LevelSyncPacket packet = new LevelSyncPacket(entityLevels);
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 清除客户端等级缓存
     */
    public static void clearClientLevelCache() {
        // 创建数据包
        LevelSyncPacket packet = new LevelSyncPacket();
        
        // 发送给所有玩家
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 获取网络统计信息
     */
    public static String getStatistics() {
        return String.format("Network: queueSize=%d, lastBatchSync=%dms ago", 
            getSyncQueueSize(), System.currentTimeMillis() - lastBatchSyncTime);
    }
    
    /**
     * 发送水晶基地死亡事件到所有客户端
     * @param factionId 死亡的水晶基地所属阵营ID
     */
    public static void sendCrystalDeathToAll(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "";
        }
        CrystalDeathPacket packet = new CrystalDeathPacket(factionId);
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    public static void sendEntityKillToAll(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "";
        }
        EntityKillPacket packet = new EntityKillPacket(factionId);
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    public static void sendCrystalDeathToPlayer(String factionId, ServerPlayer player) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "";
        }
        if (player == null) return;
        CrystalDeathPacket packet = new CrystalDeathPacket(factionId);
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    
    /**
     * 清除所有客户端的水晶基地死亡计数
     */
    public static void clearCrystalDeathCounts() {
        CrystalDeathPacket packet = new CrystalDeathPacket();
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        LOGGER.debug("Sent clear crystal death counts packet to all clients");
    }

    /**
     * 清除客户端计分板数据（HUD 真实数据源 = CrystalDeathCounter）
     *
     * @param factionId null 或空 = 清除全部阵营；否则只清该阵营的死亡分与击败数
     */
    public static void clearCrystalDeathCounter(String factionId) {
        ClearDeathCountPacket packet = (factionId == null || factionId.isEmpty())
                ? new ClearDeathCountPacket()
                : new ClearDeathCountPacket(factionId);
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        LOGGER.debug("Sent clear scoreboard packet to all clients, faction={}",
                factionId == null || factionId.isEmpty() ? "ALL" : factionId);
    }
}
