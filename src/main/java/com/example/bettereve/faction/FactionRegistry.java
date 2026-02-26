package com.example.bettereve.faction;

import com.example.bettereve.Bettereve;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FactionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionRegistry.class);
    private static final Map<String, Faction> factions = new HashMap<>();

    // 默认阵营配置（用于地图首次加载时）
    private static final String DEFAULT_FACTIONS = "red=红队,red,true;blue=蓝队,blue,true";
    private static final String DEFAULT_RELATIONS = "red:blue=hostile";
    private static final boolean DEFAULT_HOSTILE = true;

    static {
        MinecraftForge.EVENT_BUS.register(FactionRegistry.class);
        loadDefaultFactions();
    }

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent.Reloading event) {
        // 配置变更时不再重新加载阵营配置，因为现在是每个地图单独存储
        LOGGER.debug("配置已变更，但阵营系统配置现在是每个地图单独存储，不受全局配置影响");
    }

    // 加载默认阵营配置
    private static void loadDefaultFactions() {
        factions.clear();

        // 加载默认阵营列表
        for (String factionStr : DEFAULT_FACTIONS.split(";")) {
            try {
                String[] parts = factionStr.split("=");
                if (parts.length != 2) {
                    LOGGER.warn("无效的阵营配置格式: {}", factionStr);
                    continue;
                }

                String id = parts[0].trim();
                String[] details = parts[1].split(",");

                String name = id;
                String color = "#FFFFFF";
                boolean enabled = true;

                if (details.length > 0 && !details[0].trim().isEmpty()) {
                    name = details[0].trim();
                }
                if (details.length > 1 && !details[1].trim().isEmpty()) {
                    color = details[1].trim();
                }
                if (details.length > 2) {
                    try {
                        enabled = Boolean.parseBoolean(details[2].trim());
                    } catch (Exception e) {
                        LOGGER.warn("无效的启用状态: {}", details[2]);
                    }
                }

                factions.put(id, new Faction(id, name, color, enabled));
                LOGGER.debug("加载默认阵营: {} ({})", name, id);
            } catch (Exception e) {
                LOGGER.warn("解析默认阵营配置失败: {}", factionStr, e);
            }
        }

        LOGGER.debug("默认阵营系统加载完成: {} 个阵营", factions.size());
    }

    // 获取所有阵营
    public static Collection<Faction> getAllFactions() {
        return factions.values();
    }

    // 获取指定ID的阵营
    public static Faction getFaction(String id) {
        if (id == null || id.isEmpty()) {
            return factions.get("neutral");
        }
        return factions.get(id);
    }

    // 检查是否为默认中立阵营
    public static boolean isNeutralFaction(String id) {
        return "neutral".equals(id);
    }

    // 检查两个阵营是否敌对
    public static boolean isHostile(String factionId1, String factionId2) {
        // 相同阵营永远不对敌对
        if (Objects.equals(factionId1, factionId2)) {
            return false;
        }

        // 使用默认规则
        return DEFAULT_HOSTILE;
    }

    // 创建新阵营
    public static Faction createFaction(String id, String name, String color, boolean enabled) {
        if (factions.containsKey(id)) {
            LOGGER.warn("阵营已存在: {}", id);
            return null;
        }

        Faction faction = new Faction(id, name, color, enabled);
        factions.put(id, faction);
        LOGGER.debug("创建新阵营: {} ({})", name, id);
        return faction;
    }

    // 删除阵营
    public static void deleteFaction(String id) {
        if (!factions.containsKey(id)) {
            LOGGER.warn("阵营不存在: {}", id);
            return;
        }

        factions.remove(id);
        LOGGER.debug("删除阵营: {}", id);
    }

    // 修改阵营
    public static Faction modifyFaction(String id, String name, String color, boolean enabled) {
        if (!factions.containsKey(id)) {
            LOGGER.warn("阵营不存在: {}", id);
            return null;
        }

        Faction faction = new Faction(id, name, color, enabled);
        factions.put(id, faction);
        LOGGER.debug("修改阵营: {} ({})", name, id);
        return faction;
    }
}