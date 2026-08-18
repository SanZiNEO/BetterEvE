package com.example.bettereve.faction;

import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 玩家阵营数据 — 通过 PersistentData 跨世界持久化。
 */
public class PlayerFactionData {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerFactionData.class);
    private static final String FACTION_KEY = "BetterEvE:PlayerFaction";

    /**
     * 获取玩家的阵营 ID。
     */
    public static String getPlayerFaction(Player player) {
        String faction = player.getPersistentData().getString(FACTION_KEY);
        if (faction == null || faction.isEmpty()) {
            // 新玩家 — 剧本写了默认阵营就用，没写就是无阵营 ""
            if (ScenarioManager.isLoaded() && ScenarioManager.getActiveScenario().getOptions() != null) {
                String defaultFaction = ScenarioManager.getActiveScenario().getOptions().getPlayersDefaultFaction();
                return defaultFaction == null ? "" : defaultFaction;
            }
            return "";
        }
        return faction;
    }

    /**
     * 设置玩家的阵营。
     */
    public static void setPlayerFaction(Player player, String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            player.getPersistentData().remove(FACTION_KEY);
        } else {
            player.getPersistentData().putString(FACTION_KEY, factionId);
        }
        LOGGER.debug("Set player {} faction to {}", player.getName().getString(), factionId);
    }

    /**
     * 重置玩家阵营（剧本切换时调用）。
     */
    public static void resetToDefault(Player player) {
        String defaultFaction = null;
        if (ScenarioManager.isLoaded() && ScenarioManager.getActiveScenario().getOptions() != null) {
            defaultFaction = ScenarioManager.getActiveScenario().getOptions().getPlayersDefaultFaction();
        }
        setPlayerFaction(player, defaultFaction);
    }
}
