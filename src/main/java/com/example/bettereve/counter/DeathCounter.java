package com.example.bettereve.counter;

import com.example.bettereve.Config;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DeathCounter extends SavedData {
    private static final String DATA_NAME = "BetterEvE_DeathCounter";

    // 存储格式: worldId -> factionId -> entityType -> count
    private final Map<String, Map<String, Map<String, Integer>>> deathCounts = new HashMap<>();

    // 无参数构造函数，用于create参数
    public DeathCounter() {}

    // 带参数的构造函数，用于load参数
    public DeathCounter(CompoundTag nbt) {
        load(nbt);
    }

    private void load(CompoundTag tag) {
        // 加载死亡计数数据
        CompoundTag worldsTag = tag.getCompound("Worlds");
        for (String worldId : worldsTag.getAllKeys()) {
            CompoundTag worldTag = worldsTag.getCompound(worldId);
            Map<String, Map<String, Integer>> factionCounts = new HashMap<>();

            for (String factionId : worldTag.getAllKeys()) {
                CompoundTag factionTag = worldTag.getCompound(factionId);
                Map<String, Integer> entityCounts = new HashMap<>();

                for (String entityType : factionTag.getAllKeys()) {
                    entityCounts.put(entityType, factionTag.getInt(entityType));
                }

                factionCounts.put(factionId, entityCounts);
            }

            deathCounts.put(worldId, factionCounts);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();

        if (level.isClientSide()) {
            return;
        }

        // 获取计数器实例
        DeathCounter counter = getInstance(level);

        // 获取世界ID、阵营ID和实体类型
        String worldId = level.dimension().location().toString();
        String factionId = FactionHelper.getEntityFactionId(entity);
        String entityType = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", entity.getType().getDescriptionId().split("\\.")[1]).toString();

        // 统计死亡数
        counter.incrementDeathCount(worldId, factionId, entityType);

        // 保存数据
        counter.setDirty();
    }

    public static DeathCounter getInstance(Level level) {
        DimensionDataStorage storage = level.getServer().getLevel(level.dimension()).getDataStorage();
        return storage.computeIfAbsent(DeathCounter::new, DeathCounter::new, DATA_NAME);
    }

    public int getTotalDeathsByFaction(String worldId, String factionId) {
        Map<String, Map<String, Integer>> factionCounts = deathCounts.getOrDefault(worldId, new HashMap<>());
        Map<String, Integer> entityCounts = factionCounts.getOrDefault(factionId, new HashMap<>());
        return entityCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getDeathsByEntityType(String worldId, String factionId, String entityType) {
        Map<String, Map<String, Integer>> factionCounts = deathCounts.getOrDefault(worldId, new HashMap<>());
        Map<String, Integer> entityCounts = factionCounts.getOrDefault(factionId, new HashMap<>());
        return entityCounts.getOrDefault(entityType, 0);
    }

    public void incrementDeathCount(String worldId, String factionId, String entityType) {
        Map<String, Map<String, Integer>> factionCounts = deathCounts.computeIfAbsent(worldId, k -> new HashMap<>());
        Map<String, Integer> entityCounts = factionCounts.computeIfAbsent(factionId, k -> new HashMap<>());
        entityCounts.put(entityType, entityCounts.getOrDefault(entityType, 0) + 1);
    }

    public void resetDeathCounts(String worldId, String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            // 重置整个世界的死亡计数
            deathCounts.remove(worldId);
        } else {
            // 仅重置特定阵营的死亡计数
            Map<String, Map<String, Integer>> factionCounts = deathCounts.get(worldId);
            if (factionCounts != null) {
                factionCounts.remove(factionId);
            }
        }
    }

    public Map<String, Integer> getFactionTotalDeaths(String worldId) {
        Map<String, Integer> result = new HashMap<>();
        Map<String, Map<String, Integer>> factionCounts = deathCounts.get(worldId);

        if (factionCounts != null) {
            for (String factionId : factionCounts.keySet()) {
                int total = getTotalDeathsByFaction(worldId, factionId);
                result.put(factionId, total);
            }
        }

        // 确保所有阵营都有条目（包括没有死亡的）
        for (var faction : FactionRegistry.getAllFactions()) {
            if (!result.containsKey(faction.getId())) {
                result.put(faction.getId(), 0);
            }
        }

        return result;
    }

    public Map<String, Map<String, Integer>> getDetailedDeaths(String worldId) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        Map<String, Map<String, Integer>> factionCounts = deathCounts.get(worldId);

        if (factionCounts != null) {
            for (String factionId : factionCounts.keySet()) {
                Map<String, Integer> entityCounts = new HashMap<>(factionCounts.get(factionId));
                result.put(factionId, entityCounts);
            }
        }

        return result;
    }

    // 移除静态的load方法，因为我们已经在构造函数中处理了

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag worldsTag = new CompoundTag();

        for (String worldId : deathCounts.keySet()) {
            CompoundTag worldTag = new CompoundTag();
            Map<String, Map<String, Integer>> factionCounts = deathCounts.get(worldId);

            for (String factionId : factionCounts.keySet()) {
                CompoundTag factionTag = new CompoundTag();
                Map<String, Integer> entityCounts = factionCounts.get(factionId);

                for (String entityType : entityCounts.keySet()) {
                    factionTag.putInt(entityType, entityCounts.get(entityType));
                }

                worldTag.put(factionId, factionTag);
            }

            worldsTag.put(worldId, worldTag);
        }

        tag.put("Worlds", worldsTag);
        return tag;
    }
}