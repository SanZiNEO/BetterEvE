package com.sanzineo.bettereve.faction;

import com.sanzineo.bettereve.scenario.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 阵营注册表 — 完全由当前剧本驱动，代码里不再有写死的默认阵营。
 * 剧本写了哪些阵营，这里就是哪些阵营；没写的绝对不存在。
 */
public class FactionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionRegistry.class);
    private static final Map<String, Faction> factions = new HashMap<>();

    /** 剧本加载时调用：用剧本 factions 完全重建注册表 */
    public static void loadFromScenario(Scenario scenario) {
        factions.clear();
        if (scenario == null || scenario.getFactions() == null) {
            return;
        }

        for (Scenario.FactionDef def : scenario.getFactions()) {
            factions.put(def.getId(), new Faction(def.getId(), def.getName(), def.getColor(), true));
        }
        LOGGER.info("Loaded {} factions from scenario {}", factions.size(),
                scenario.getId() == null ? "(unknown)" : scenario.getId());
    }

    public static Collection<Faction> getAllFactions() {
        return factions.values();
    }

    /** 无阵营/null/空串一律返回 null，不兜底 */
    public static Faction getFaction(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return factions.get(id);
    }

    /** 创建新阵营（指令用） */
    public static Faction createFaction(String id, String name, String color, boolean enabled) {
        if (id == null || id.isEmpty() || factions.containsKey(id)) {
            return null;
        }
        Faction faction = new Faction(id, name, color, enabled);
        factions.put(id, faction);
        return faction;
    }

    /** 删除阵营（指令用） */
    public static void deleteFaction(String id) {
        if (id != null) {
            factions.remove(id);
        }
    }

    /** 修改阵营（指令用） */
    public static Faction modifyFaction(String id, String name, String color, boolean enabled) {
        if (id == null || !factions.containsKey(id)) {
            return null;
        }
        Faction faction = new Faction(id, name, color, enabled);
        factions.put(id, faction);
        return faction;
    }
}
