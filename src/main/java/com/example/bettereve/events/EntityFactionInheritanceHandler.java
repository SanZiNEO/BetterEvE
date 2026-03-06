package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.bettereve.Bettereve;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityFactionInheritanceHandler {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionInheritance");
    
    // 临时数据缓存 - 存储最近死亡的实体信息
    private static final ConcurrentHashMap<String, DeathRecord> deathRecords = new ConcurrentHashMap<>();
    private static final int DEATH_RECORD_TTL = 10; // 10 ticks (0.5秒) 过期时间
    private static int tickCounter = 0;

    // 死亡记录数据类
    private static class DeathRecord {
        public double x, y, z;
        public String factionId;
        public long tickTime;
        public String entityType;
        
        public DeathRecord(double x, double y, double z, String factionId, long tickTime, String entityType) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.factionId = factionId;
            this.tickTime = tickTime;
            this.entityType = entityType;
        }
    }

    // 定期清理过期的死亡记录
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            tickCounter++;
            
            // 每 tick 清理一次过期记录
            List<String> toRemove = new ArrayList<>();
            for (String key : deathRecords.keySet()) {
                DeathRecord record = deathRecords.get(key);
                if (tickCounter - record.tickTime > DEATH_RECORD_TTL) {
                    toRemove.add(key);
                }
            }
            
            for (String key : toRemove) {
                deathRecords.remove(key);
            }
        }
    }

    // 监听生物死亡事件 - 记录死亡信息，用于处理死亡后召唤
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 只在服务器端处理
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 记录死亡生物的信息
        String entityId = event.getEntity().getUUID().toString();
        DeathRecord record = new DeathRecord(
            event.getEntity().getX(),
            event.getEntity().getY(),
            event.getEntity().getZ(),
            FactionHelper.getEntityFactionId(event.getEntity()),
            tickCounter,
            event.getEntity().getType().toString()
        );
        
        deathRecords.put(entityId, record);
    }

    // 监听子生物生成事件（专门用于繁殖产生的子生物）
    @SubscribeEvent
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        Mob parentA = event.getParentA();
        Mob parentB = event.getParentB();
        Mob child = event.getChild();


        // 获取父生物的阵营ID（优先使用第一个父生物的阵营）
        String factionId = FactionHelper.getEntityFactionId(parentA);
        
        // 如果第一个父生物是中立，则尝试使用第二个父生物的阵营
        if (factionId.equals("neutral")) {
            factionId = FactionHelper.getEntityFactionId(parentB);
        }
        
        // 设置子生物的阵营与父生物相同
        FactionHelper.setEntityFactionId(child, factionId);
        
        
        // 同步阵营信息到客户端
        com.example.bettereve.network.FactionSyncHandler.syncEntityImmediately(child);
    }

    // 监听实体加入世界事件（覆盖更广泛的生物生成场景）
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只在服务器端处理
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();

        // 检查实体是否已经有非中立的阵营ID（比如命令召唤的生物），如果有则不进行继承
        if (!FactionHelper.getEntityFactionId(entity).equals("neutral")) {
            return;
        }

        // 首先尝试从死亡记录中继承阵营（死亡后召唤）
        String factionId = tryInheritFromDeathRecords(entity);
        
        if (factionId.equals("neutral")) {
            // 如果没有从死亡记录继承到阵营，尝试从附近实体继承
            factionId = tryInheritFromNearbyEntities(entity);
        }
        
        if (!factionId.equals("neutral")) {
            FactionHelper.setEntityFactionId(entity, factionId);
            com.example.bettereve.network.FactionSyncHandler.syncEntityImmediately(entity);
        }
    }

    // 从死亡记录中继承阵营
    private static String tryInheritFromDeathRecords(Entity entity) {
        double searchRadius = 0.5; // 搜索半径缩小到0.5格
        String bestFactionId = "neutral";
        double closestDistance = Double.MAX_VALUE;

        // 遍历所有死亡记录
        for (DeathRecord record : deathRecords.values()) {
            // 检查距离
            double distance = Math.sqrt(
                Math.pow(entity.getX() - record.x, 2) +
                Math.pow(entity.getY() - record.y, 2) +
                Math.pow(entity.getZ() - record.z, 2)
            );

            // 如果在搜索范围内，并且不是中立阵营
            if (distance <= searchRadius && !record.factionId.equals("neutral")) {
                
                // 选择最近的匹配
                if (distance < closestDistance) {
                    closestDistance = distance;
                    bestFactionId = record.factionId;
                }
            }
        }

        return bestFactionId;
    }

    // 从附近实体继承阵营
    private static String tryInheritFromNearbyEntities(Entity entity) {
        double searchRadius = 15.0; // 搜索半径
        String bestFactionId = "neutral";
        double closestDistance = Double.MAX_VALUE;

        // 查找附近的实体
        List<Entity> nearbyEntities = entity.level().getEntitiesOfClass(
            Entity.class,
            entity.getBoundingBox().inflate(searchRadius)
        );

        for (Entity nearbyEntity : nearbyEntities) {
            // 跳过自身
            if (nearbyEntity == entity) {
                continue;
            }

            // 获取附近实体的阵营
            String factionId = FactionHelper.getEntityFactionId(nearbyEntity);
            
            if (!factionId.equals("neutral")) {
                double distance = entity.distanceToSqr(nearbyEntity);
                
                // 选择最近的匹配
                if (distance < closestDistance) {
                    closestDistance = distance;
                    bestFactionId = factionId;
                }
            }
        }

        if (!bestFactionId.equals("neutral")) {
        }

        return bestFactionId;
    }

    // 辅助方法：判断是否是可继承阵营的实体类型
    private static boolean isInheritableEntityType(Entity entity) {
        // 覆盖常见的实体类型
        return entity instanceof Mob || 
               entity instanceof Animal || 
               isSummonableEntityType(entity);
    }

    // 判断是否是可能被召唤的实体类型
    private static boolean isSummonableEntityType(Entity entity) {
        String entityTypeName = entity.getType().toString().toLowerCase();
        return entityTypeName.contains("spider") ||
               entityTypeName.contains("slime") ||
               entityTypeName.contains("magma_cube") ||
               entityTypeName.contains("zombie") ||
               entityTypeName.contains("skeleton");
    }
}