package com.example.bettereve.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 血条文字显示开关同步数据包。
 * <p>
 * 负责在服务器和客户端之间同步每个实体的：
 * - Lv 等级文字是否显示
 * - 血量数字是否显示
 * 与 HealthBarVisibilityPacket（是否显示整个血条）相互独立，可单独控制。
 */
public class HealthBarTextVisibilityPacket {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/HealthBarTextVisibilityPacket");

    private static final int MODE_CLEAR_CACHE = 0;
    private static final int MODE_SINGLE_ENTITY = 1;
    private static final int MODE_BATCH_SYNC = 2;

    private int syncMode;
    private UUID entityUUID;
    private boolean showLevelText;
    private boolean showHealthText;
    private Map<UUID, Byte> entityFlagMap;

    public HealthBarTextVisibilityPacket() {
        this.syncMode = MODE_CLEAR_CACHE;
        this.entityFlagMap = new HashMap<>();
    }

    public HealthBarTextVisibilityPacket(UUID entityUUID, boolean showLevelText, boolean showHealthText) {
        this.syncMode = MODE_SINGLE_ENTITY;
        this.entityUUID = entityUUID;
        this.showLevelText = showLevelText;
        this.showHealthText = showHealthText;
        this.entityFlagMap = new HashMap<>();
    }

    public HealthBarTextVisibilityPacket(Map<UUID, Byte> entityFlagMap) {
        this.syncMode = MODE_BATCH_SYNC;
        this.entityFlagMap = new HashMap<>(entityFlagMap);
    }

    public static void encode(HealthBarTextVisibilityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.syncMode);
        switch (packet.syncMode) {
            case MODE_SINGLE_ENTITY -> {
                buffer.writeUUID(packet.entityUUID);
                buffer.writeBoolean(packet.showLevelText);
                buffer.writeBoolean(packet.showHealthText);
            }
            case MODE_BATCH_SYNC -> {
                buffer.writeVarInt(packet.entityFlagMap.size());
                for (Map.Entry<UUID, Byte> entry : packet.entityFlagMap.entrySet()) {
                    buffer.writeUUID(entry.getKey());
                    buffer.writeByte(entry.getValue());
                }
            }
            default -> {
                // clear cache: no payload
            }
        }
    }

    public static HealthBarTextVisibilityPacket decode(FriendlyByteBuf buffer) {
        int syncMode = buffer.readVarInt();
        switch (syncMode) {
            case MODE_SINGLE_ENTITY -> {
                UUID uuid = buffer.readUUID();
                boolean showLevelText = buffer.readBoolean();
                boolean showHealthText = buffer.readBoolean();
                return new HealthBarTextVisibilityPacket(uuid, showLevelText, showHealthText);
            }
            case MODE_BATCH_SYNC -> {
                int size = buffer.readVarInt();
                Map<UUID, Byte> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(buffer.readUUID(), buffer.readByte());
                }
                return new HealthBarTextVisibilityPacket(map);
            }
            default -> {
                return new HealthBarTextVisibilityPacket();
            }
        }
    }

    public static void handle(HealthBarTextVisibilityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                switch (packet.syncMode) {
                    case MODE_SINGLE_ENTITY -> ClientHealthBarTextVisibilityCache.setEntityTextVisibility(
                            packet.entityUUID, packet.showLevelText, packet.showHealthText);
                    case MODE_BATCH_SYNC -> {
                        for (Map.Entry<UUID, Byte> entry : packet.entityFlagMap.entrySet()) {
                            boolean showLevel = (entry.getValue() & 0x01) != 0;
                            boolean showHealth = (entry.getValue() & 0x02) != 0;
                            ClientHealthBarTextVisibilityCache.setEntityTextVisibility(
                                    entry.getKey(), showLevel, showHealth);
                        }
                    }
                    default -> ClientHealthBarTextVisibilityCache.clearCache();
                }
            } catch (Exception e) {
                LOGGER.error("Error handling HealthBarTextVisibilityPacket: {}", e.getMessage());
            }
        });
        context.setPacketHandled(true);
    }

    public static class ClientHealthBarTextVisibilityCache {
        private static final Map<UUID, Boolean> levelTextVisibility = new HashMap<>();
        private static final Map<UUID, Boolean> healthTextVisibility = new HashMap<>();

        public static void setEntityTextVisibility(UUID uuid, boolean showLevelText, boolean showHealthText) {
            levelTextVisibility.put(uuid, showLevelText);
            healthTextVisibility.put(uuid, showHealthText);
        }

        public static boolean getEntityLevelTextVisibility(UUID uuid) {
            return levelTextVisibility.getOrDefault(uuid, true);
        }

        public static boolean getEntityHealthTextVisibility(UUID uuid) {
            return healthTextVisibility.getOrDefault(uuid, true);
        }

        public static void clearCache() {
            levelTextVisibility.clear();
            healthTextVisibility.clear();
        }

        public static boolean hasData(UUID uuid) {
            return levelTextVisibility.containsKey(uuid) || healthTextVisibility.containsKey(uuid);
        }
    }
}
