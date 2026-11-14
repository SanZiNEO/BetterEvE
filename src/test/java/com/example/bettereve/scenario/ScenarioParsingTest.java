package com.example.bettereve.scenario;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧本 JSON 解析测试：解析仓库内 scenarios/*.json，校验 Scenario 模型的 Gson 映射。
 * 不启动游戏、不写文件；解析配置与 JsonScenarioStorage 一致（默认忽略未知键）。
 */
class ScenarioParsingTest {

    /** 与 JsonScenarioStorage 内部的 Gson 配置保持一致 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Scenario readPreset(String fileName) throws IOException {
        return GSON.fromJson(Files.readString(Path.of("scenarios", fileName)), Scenario.class);
    }

    @Test
    void parsesDefaultScenario() throws IOException {
        Scenario scenario = readPreset("default.json");
        assertNotNull(scenario);

        assertEquals("default", scenario.getId());
        assertEquals("默认剧本", scenario.getName());
        assertNotNull(scenario.getDescription());

        // 阵营
        assertEquals(2, scenario.getFactions().size());
        Scenario.FactionDef red = scenario.getFactions().get(0);
        assertEquals("red", red.getId());
        assertEquals("红队", red.getName());
        assertEquals("#FF0000", red.getColor());
        assertEquals(0xFF0000, red.getColorInt());
        assertTrue(red.isShowOnScoreboard());
        assertEquals("blue", scenario.getFactions().get(1).getId());
        assertEquals(0x0000FF, scenario.getFactions().get(1).getColorInt());

        // 关系
        assertEquals(2, scenario.getRelations().size());
        assertEquals("red", scenario.getRelations().get(0).getFrom());
        assertEquals("blue", scenario.getRelations().get(0).getTo());
        assertEquals("hostile", scenario.getRelations().get(0).getType());

        // 路径点按 JSON 顺序解析
        assertEquals(4, scenario.getWaypoints().size());
        assertEquals(List.of("red_waypoint_1", "red_waypoint_2", "blue_waypoint_1", "blue_waypoint_2"),
                scenario.getWaypoints().stream().map(Scenario.WaypointRegionDef::getId).toList());
        assertEquals("red", scenario.getWaypoints().get(0).getFactionId());
        assertEquals(-50.0, scenario.getWaypoints().get(0).getX1(), 1e-9);
        assertEquals(10.0, scenario.getWaypoints().get(0).getZ2(), 1e-9);

        // 路线顺序
        assertEquals(1, scenario.getRoutes().size());
        Scenario.RouteDef route = scenario.getRoutes().get(0);
        assertEquals("route_a", route.getId());
        assertEquals(List.of("red_waypoint_1", "red_waypoint_2", "blue_waypoint_1", "blue_waypoint_2"),
                route.getWaypoints());

        // 水晶
        assertEquals(4, scenario.getCrystals().size());
        Scenario.CrystalTemplateDef redBase = scenario.getCrystals().get(0);
        assertEquals("red_base", redBase.getId());
        assertEquals("red", redBase.getFactionId());
        assertEquals(-40.0, redBase.getX(), 1e-9);
        assertEquals(-60.0, redBase.getY(), 1e-9);
        assertEquals(8000, redBase.getHealth());
        assertEquals(20, redBase.getAttackDamage());
        assertEquals(8, redBase.getArmor());
        assertEquals(15.0, redBase.getAttackRange(), 1e-9);
        assertEquals(2.0, redBase.getAttackCooldown(), 1e-9);
        assertEquals("5x10", redBase.getSizeKey());
        assertEquals(1.0, redBase.getModelScale(), 1e-9);
        assertTrue(redBase.isShowHealthBar());

        // 阶段
        assertEquals(List.of("match_start", "blue_victory", "red_victory"),
                scenario.getPhases().stream().map(Scenario.PhaseDef::getId).toList());
        Scenario.PhaseDef matchStart = scenario.getPhases().get(0);
        assertEquals("loop", matchStart.getPhaseType());
        assertEquals(6, matchStart.getCommands().size());
        assertEquals(2, matchStart.getTriggers().size());
        assertEquals("crystal_destroyed", matchStart.getTriggers().get(0).getType());
        assertEquals("red_base", matchStart.getTriggers().get(0).getTargetId());
        assertEquals("blue_victory", matchStart.getTriggers().get(0).getTargetPhase());
        assertEquals("timer", scenario.getPhases().get(1).getTriggers().get(0).getType());
        assertEquals(12, scenario.getPhases().get(1).getTriggers().get(0).getThreshold());

        // 选项
        Scenario.ScenarioOptions options = scenario.getOptions();
        assertNotNull(options);
        assertEquals(64.0, options.getTargetingRange(), 1e-9);
        assertEquals(10.0, options.getTargetingFocusRange(), 1e-9);
        assertEquals(20, options.getTargetingFocusInterval());
        assertTrue(options.isHudEnabled());
        assertTrue(options.isOverheadHealthBar());
        assertTrue(options.getSummon().isInheritFaction());
        assertTrue(options.getSummon().isInheritLevel());
        assertFalse(options.getSummon().isHideHealthBar());
        assertTrue(options.getProjectile().isEnabled());
        // JSON 未写 spawnLayout → 保持 null，由 SpawnLayout 自己给默认值
        assertNull(options.getSpawnLayout());

        // 保活区域（含边界）
        Scenario.KeepAliveZone zone = scenario.getKeepAliveZone();
        assertNotNull(zone);
        assertTrue(zone.contains(-60, -20));
        assertTrue(zone.contains(60, 20));
        assertFalse(zone.contains(60.1, 20));
        assertFalse(zone.contains(0, 20.1));

        // 未配置的段保持 null
        assertNull(scenario.getLevelTables());
        assertNull(scenario.getLevelTableOverrides());
    }

    @Test
    void parsesSimpleRedBlueScenario() throws IOException {
        Scenario scenario = readPreset("simple_red_blue.json");
        assertNotNull(scenario);

        assertEquals("simple_red_blue", scenario.getId());
        assertEquals("简单红蓝对抗", scenario.getName());

        // 等级表
        assertEquals(1, scenario.getLevelTables().size());
        Scenario.EntityLevelConfig standard = scenario.getLevelTables().get("standard");
        assertNotNull(standard);
        assertEquals(1000, standard.getMaxLevel());
        assertEquals(10.0, standard.getHealthBonus(), 1e-9);
        assertEquals(2.0, standard.getHealthExponent(), 1e-9);
        assertEquals(8.0, standard.getAttackBonus(), 1e-9);
        assertEquals(2.0, standard.getAttackExponent(), 1e-9);
        assertEquals(2.0, standard.getArmorBonus(), 1e-9);
        assertEquals(0.7, standard.getArmorExponent(), 1e-9);
        assertEquals(1.2, standard.getKnockbackBonus(), 1e-9);
        assertEquals(1.0, standard.getKnockbackExponent(), 1e-9);
        assertEquals(200.0, standard.getMaxDamage(), 1e-9); // 未写 = 默认值

        // 阵营引用等级表
        assertEquals(2, scenario.getFactions().size());
        assertEquals("standard", scenario.getFactions().get(0).getLevelTable());
        assertEquals("standard", scenario.getFactions().get(1).getLevelTable());

        // 选项
        Scenario.ScenarioOptions options = scenario.getOptions();
        assertEquals(320.0, options.getTargetingRange(), 1e-9);
        assertEquals(10.0, options.getTargetingFocusRange(), 1e-9);
        assertTrue(options.isOverheadHealthBar());
        assertTrue(options.isHealthBarLevelText());
        assertTrue(options.isHealthBarHealthText());
        assertFalse(options.isVersusHealthBar());
        assertEquals(2, options.getVersusHealthBarSubCount());
        assertEquals("plain", options.getVersusHealthBarStyle());
        assertEquals(1.0f, options.getHealthBarScale(), 1e-6f);

        // 该剧本没有路线 / 路径点 / 水晶 / 阶段
        assertNull(scenario.getWaypoints());
        assertNull(scenario.getRoutes());
        assertNull(scenario.getCrystals());
        assertNull(scenario.getPhases());

        Scenario.KeepAliveZone zone = scenario.getKeepAliveZone();
        assertEquals(-101.0, zone.getX1(), 1e-9);
        assertEquals(101.0, zone.getZ2(), 1e-9);
    }

    @Test
    void deserializesSpawnLayoutAndFormationSerializedNames() {
        Scenario.SpawnLayout layout = GSON.fromJson(
                "{\"formation\":\"line_z\",\"radius\":3.5,\"arc\":180}", Scenario.SpawnLayout.class);
        assertEquals(Scenario.Formation.LINE_Z, layout.getFormation());
        assertEquals(3.5, layout.getRadius(), 1e-9);
        assertEquals(180.0, layout.getArc(), 1e-9);
        assertEquals(2.0, layout.getSpacing(), 1e-9);  // 未写 = 默认值
        assertEquals(7.0, layout.getWidth(), 1e-9);    // 未写 = radius * 2
        assertEquals(7.0, layout.getDepth(), 1e-9);

        Scenario.SpawnLayout fallback = GSON.fromJson("{}", Scenario.SpawnLayout.class);
        assertEquals(Scenario.Formation.CIRCLE, fallback.getFormation());
        assertEquals(2.0, fallback.getRadius(), 1e-9);
        assertEquals(2.0, fallback.getSpacing(), 1e-9);
        assertEquals(360.0, fallback.getArc(), 1e-9);
        assertEquals(4.0, fallback.getWidth(), 1e-9);
        assertEquals(4.0, fallback.getDepth(), 1e-9);
    }

    @Test
    void ignoresUnknownKeysLikeExternalEditorsDo() {
        String json = """
                {
                  "id": "external",
                  "douququ": { "panel": true },
                  "options": { "unknownField": 1, "targetingRange": 12.5 }
                }
                """;
        Scenario scenario = GSON.fromJson(json, Scenario.class);
        assertNotNull(scenario);
        assertEquals("external", scenario.getId());
        assertEquals(12.5, scenario.getOptions().getTargetingRange(), 1e-9);
        assertNull(scenario.getFactions());
        assertNull(scenario.getRoutes());
    }
}
