package com.sanzineo.bettereve.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario 内嵌配置类的代码内默认值测试（JSON 未写某段时的行为）。
 */
class ScenarioDefaultsTest {

    @Test
    void emptyScenarioHasNoConfiguredSections() {
        Scenario scenario = new Scenario();
        assertNull(scenario.getId());
        assertNull(scenario.getName());
        assertNull(scenario.getDescription());
        assertNull(scenario.getFactions());
        assertNull(scenario.getRelations());
        assertNull(scenario.getWaypoints());
        assertNull(scenario.getRoutes());
        assertNull(scenario.getCrystals());
        assertNull(scenario.getPhases());
        assertNull(scenario.getOptions());
        assertNull(scenario.getKeepAliveZone());
        assertNull(scenario.getLevelTables());
        assertNull(scenario.getLevelTableOverrides());
    }

    @Test
    void scenarioOptionsUseCodeDefaults() {
        Scenario.ScenarioOptions options = new Scenario.ScenarioOptions();
        assertNull(options.getPlayersDefaultFaction());
        assertEquals(64.0, options.getTargetingRange(), 1e-9);
        assertEquals(10.0, options.getTargetingFocusRange(), 1e-9);
        assertEquals(20, options.getTargetingFocusInterval());
        assertTrue(options.isHudEnabled());
        assertTrue(options.isOverheadHealthBar());
        assertEquals(1.0f, options.getHealthBarScale(), 1e-6f);
        assertTrue(options.isHealthBarLevelText());
        assertTrue(options.isHealthBarHealthText());
        assertFalse(options.isVersusHealthBar());
        assertEquals(2, options.getVersusHealthBarSubCount());
        assertEquals("plain", options.getVersusHealthBarStyle());
        assertTrue(options.isVersusHealthBarHealthText());
        assertNull(options.getSummon());
        assertNull(options.getProjectile());
        assertNull(options.getSpawnLayout());
        assertNull(options.getRules());
        assertNull(options.getSunBurn());
    }

    @Test
    void spawnLayoutDefaultsMatchConstants() {
        Scenario.SpawnLayout layout = new Scenario.SpawnLayout();
        assertEquals(Scenario.Formation.CIRCLE, layout.getFormation());
        assertEquals(Scenario.ScenarioOptions.DEFAULT_SPAWN_RADIUS, layout.getRadius(), 1e-9);
        assertEquals(Scenario.ScenarioOptions.DEFAULT_SPAWN_SPACING, layout.getSpacing(), 1e-9);
        assertEquals(Scenario.ScenarioOptions.DEFAULT_SPAWN_ARC, layout.getArc(), 1e-9);
        assertEquals(4.0, layout.getWidth(), 1e-9);
        assertEquals(4.0, layout.getDepth(), 1e-9);

        layout.setRadius(3.0);
        assertEquals(6.0, layout.getWidth(), 1e-9);
        assertEquals(6.0, layout.getDepth(), 1e-9);

        layout.setWidth(5.0);
        layout.setDepth(7.0);
        assertEquals(5.0, layout.getWidth(), 1e-9);
        assertEquals(7.0, layout.getDepth(), 1e-9);
    }

    @Test
    void summonConfigDefaultsAndOverrides() {
        Scenario.SummonConfig summon = new Scenario.SummonConfig();
        assertTrue(summon.isEnabled());
        assertTrue(summon.isInheritFaction());
        assertTrue(summon.isInheritLevel());
        assertFalse(summon.isHideHealthBar());
        assertFalse(summon.isCountsForScore());
        assertNull(summon.getLevelTable());
        assertNull(summon.getHealthBarOverrides());
        assertNull(summon.getFactionOverrides());

        summon.setEnabled(false);
        summon.setInheritFaction(false);
        summon.setHideHealthBar(true);
        summon.setCountsForScore(true);
        assertFalse(summon.isEnabled());
        assertFalse(summon.isInheritFaction());
        assertTrue(summon.isHideHealthBar());
        assertTrue(summon.isCountsForScore());
    }

    @Test
    void projectileConfigDefaultsAndOverrides() {
        Scenario.ProjectileConfig projectile = new Scenario.ProjectileConfig();
        assertTrue(projectile.isEnabled());
        assertTrue(projectile.isInheritFaction());
        assertTrue(projectile.isInheritLevel());
        assertNull(projectile.getLevelTable());

        projectile.setEnabled(false);
        projectile.setInheritFaction(false);
        projectile.setInheritLevel(false);
        assertFalse(projectile.isEnabled());
        assertFalse(projectile.isInheritFaction());
        assertFalse(projectile.isInheritLevel());
    }

    @Test
    void sunBurnConfigDefaultsToProtected() {
        Scenario.SunBurnConfig sunBurn = new Scenario.SunBurnConfig();
        assertFalse(sunBurn.isEnabled());
        assertNull(sunBurn.getExcept());

        sunBurn.setEnabled(true);
        assertTrue(sunBurn.isEnabled());
        sunBurn.setEnabled(null);
        assertFalse(sunBurn.isEnabled());
    }

    @Test
    void lethalHitAndStateKillDefaults() {
        Scenario.LethalHit hit = new Scenario.LethalHit();
        assertEquals(Scenario.LethalHit.DEFAULT_DAMAGE, hit.getDamage(), 1e-9);
        hit.setDamage(50.0);
        assertEquals(50.0, hit.getDamage(), 1e-9);
        hit.setDamage(null);
        assertEquals(Scenario.LethalHit.DEFAULT_DAMAGE, hit.getDamage(), 1e-9);

        Scenario.StateKill stateKill = new Scenario.StateKill();
        assertEquals(3, stateKill.getKills());
        assertEquals(10, stateKill.getIntervalTicks());
        stateKill.setKills(0);
        assertEquals(1, stateKill.getKills());
        stateKill.setKills(5);
        assertEquals(5, stateKill.getKills());
        stateKill.setIntervalTicks(0);
        assertEquals(1, stateKill.getIntervalTicks());
        stateKill.setIntervalTicks(-3);
        assertEquals(1, stateKill.getIntervalTicks());
    }

    @Test
    void attributeOverrideDefaultsAndEmptiness() {
        Scenario.AttributeOverride override = new Scenario.AttributeOverride();
        assertEquals(1.0, override.getHealthMultiplier(), 1e-9);
        assertEquals(1.0, override.getArmorMultiplier(), 1e-9);
        assertEquals(1.0, override.getAttackMultiplier(), 1e-9);
        assertTrue(override.isEmpty());

        override.setArmorMultiplier(0.5);
        assertEquals(0.5, override.getArmorMultiplier(), 1e-9);
        assertFalse(override.isEmpty());
    }

    @Test
    void factionDefColorParsingFallbacks() {
        Scenario.FactionDef def = new Scenario.FactionDef("red", "红队", "#FF0000");
        assertEquals(0xFF0000, def.getColorInt());
        assertTrue(def.isShowOnScoreboard());

        def.setColor("#00FF00");
        assertEquals(0x00FF00, def.getColorInt());

        def.setColor("00FF00");
        assertEquals(0xFFFFFF, def.getColorInt());

        def.setColor("#GG0000");
        assertEquals(0xFFFFFF, def.getColorInt());

        def.setColor(null);
        assertEquals(0xFFFFFF, def.getColorInt());
    }

    @Test
    void keepAliveZoneNormalizesReversedBounds() {
        Scenario.KeepAliveZone zone = new Scenario.KeepAliveZone();
        zone.setX1(10);
        zone.setZ1(20);
        zone.setX2(-10);
        zone.setZ2(0);

        assertTrue(zone.contains(0, 10));
        assertTrue(zone.contains(-10, 0));
        assertTrue(zone.contains(10, 20));
        assertFalse(zone.contains(10.5, 10));
        assertFalse(zone.contains(0, -0.5));
    }

    @Test
    void formationEnumHasAllSerializedNames() {
        assertEquals(9, Scenario.Formation.values().length);
        assertEquals(Scenario.Formation.CIRCLE, Scenario.ScenarioOptions.DEFAULT_SPAWN_FORMATION);

        Scenario.SpawnLayout layout = new Scenario.SpawnLayout();
        layout.setFormation(Scenario.Formation.DIAMOND);
        assertEquals(Scenario.Formation.DIAMOND, layout.getFormation());
        layout.setFormation(null);
        assertEquals(Scenario.Formation.CIRCLE, layout.getFormation());
    }
}
