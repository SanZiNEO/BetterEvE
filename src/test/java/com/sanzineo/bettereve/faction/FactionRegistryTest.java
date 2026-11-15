package com.sanzineo.bettereve.faction;

import com.sanzineo.bettereve.scenario.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阵营注册表测试：剧本驱动的重建、颜色解析、增删改。
 */
class FactionRegistryTest {

    @BeforeEach
    void resetRegistry() {
        FactionRegistry.loadFromScenario(null);
    }

    @Test
    void loadsFactionsFromScenario() {
        FactionRegistry.loadFromScenario(scenarioWith(
                faction("red", "红队", "#FF0000"),
                faction("blue", "蓝队", "#0000FF")));

        assertEquals(2, FactionRegistry.getAllFactions().size());
        Faction red = FactionRegistry.getFaction("red");
        assertNotNull(red);
        assertEquals("红队", red.getName());
        assertEquals("#FF0000", red.getColor());
        assertEquals(0xFF0000, red.getColorInt());
        assertEquals(255, red.getRed());
        assertEquals(0, red.getGreen());
        assertEquals(0, red.getBlue());
        assertTrue(red.isEnabled());

        Faction blue = FactionRegistry.getFaction("blue");
        assertEquals("蓝队", blue.getName());
        assertEquals(0x0000FF, blue.getColorInt());
        assertEquals(255, blue.getBlue());
    }

    @Test
    void unknownOrEmptyIdsResolveToNull() {
        FactionRegistry.loadFromScenario(scenarioWith(faction("red", "红队", "#FF0000")));

        assertNull(FactionRegistry.getFaction(null));
        assertNull(FactionRegistry.getFaction(""));
        assertNull(FactionRegistry.getFaction("missing"));
    }

    @Test
    void createFactionRejectsDuplicatesAndEmptyIds() {
        FactionRegistry.loadFromScenario(scenarioWith(faction("red", "红队", "#FF0000")));

        Faction green = FactionRegistry.createFaction("green", "绿队", "#00FF00", true);
        assertNotNull(green);
        assertEquals("绿队", FactionRegistry.getFaction("green").getName());
        assertEquals(0x00FF00, FactionRegistry.getFaction("green").getColorInt());

        assertNull(FactionRegistry.createFaction("green", "重复", "#FFFFFF", true));
        assertNull(FactionRegistry.createFaction(null, "空", "#FFFFFF", true));
        assertNull(FactionRegistry.createFaction("", "空", "#FFFFFF", true));
        assertEquals(2, FactionRegistry.getAllFactions().size());
    }

    @Test
    void modifyFactionReplacesDefinition() {
        FactionRegistry.loadFromScenario(scenarioWith(faction("red", "红队", "#FF0000")));

        Faction modified = FactionRegistry.modifyFaction("red", "新红队", "#00FF00", false);
        assertNotNull(modified);
        assertEquals("新红队", FactionRegistry.getFaction("red").getName());
        assertEquals(0x00FF00, FactionRegistry.getFaction("red").getColorInt());
        assertFalse(FactionRegistry.getFaction("red").isEnabled());

        assertNull(FactionRegistry.modifyFaction("missing", "无", "#FFFFFF", true));
        assertNull(FactionRegistry.modifyFaction(null, "无", "#FFFFFF", true));
    }

    @Test
    void deleteFactionRemovesEntry() {
        FactionRegistry.loadFromScenario(scenarioWith(
                faction("red", "红队", "#FF0000"),
                faction("blue", "蓝队", "#0000FF")));

        FactionRegistry.deleteFaction("red");
        assertNull(FactionRegistry.getFaction("red"));
        assertEquals(1, FactionRegistry.getAllFactions().size());
        assertEquals("blue", FactionRegistry.getAllFactions().iterator().next().getId());

        FactionRegistry.deleteFaction(null); // 不应抛异常
        assertEquals(1, FactionRegistry.getAllFactions().size());
    }

    @Test
    void reloadReplacesPreviousRegistry() {
        FactionRegistry.loadFromScenario(scenarioWith(faction("red", "红队", "#FF0000")));
        assertNotNull(FactionRegistry.getFaction("red"));

        FactionRegistry.loadFromScenario(scenarioWith(faction("blue", "蓝队", "#0000FF")));
        assertNull(FactionRegistry.getFaction("red"));
        assertNotNull(FactionRegistry.getFaction("blue"));
        assertEquals(1, FactionRegistry.getAllFactions().size());
    }

    @Test
    void invalidColorFallsBackToWhite() {
        FactionRegistry.loadFromScenario(scenarioWith(faction("odd", "奇怪", "red")));
        assertEquals(0xFFFFFF, FactionRegistry.getFaction("odd").getColorInt());

        FactionRegistry.loadFromScenario(scenarioWith(faction("empty", "空色", null)));
        assertEquals(0xFFFFFF, FactionRegistry.getFaction("empty").getColorInt());
    }

    private static Scenario.FactionDef faction(String id, String name, String color) {
        Scenario.FactionDef def = new Scenario.FactionDef();
        def.setId(id);
        def.setName(name);
        def.setColor(color);
        return def;
    }

    private static Scenario scenarioWith(Scenario.FactionDef... defs) {
        Scenario scenario = new Scenario();
        scenario.setId("test");
        scenario.setFactions(List.of(defs));
        return scenario;
    }
}
