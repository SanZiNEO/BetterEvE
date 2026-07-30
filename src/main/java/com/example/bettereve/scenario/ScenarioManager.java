package com.example.bettereve.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.phase.PhaseManager;

/**
 * 剧本管理器 — 加载/切换/运行时查询。
 */
public class ScenarioManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioManager.class);
    private static Scenario activeScenario = null;
    private static String activeScenarioName = null;

    /**
     * 加载并激活指定剧本。
     */
    public static void load(String name) throws ScenarioLoadException {
        Scenario scenario = ScenarioIO.readScenario(name);
        activeScenario = scenario;
        activeScenarioName = name;
        
        // 将加载的剧本同步到 PhaseManager 和 WaypointManager
        PhaseManager.loadFromScenario(scenario);
        WaypointManager.loadFromScenario(scenario);
        
        LOGGER.info("Loaded scenario: {} ({})", name, scenario.getName());
    }

    /**
     * 获取当前活跃的剧本。
     */
    public static Scenario getActiveScenario() {
        return activeScenario;
    }

    /**
     * 获取当前活跃的剧本名称。
     */
    public static String getActiveScenarioName() {
        return activeScenarioName;
    }

    /**
     * 列出可用剧本。
     */
    public static List<String> list() {
        return ScenarioIO.listScenarios();
    }

    /**
     * 重新加载当前剧本。
     */
    public static void reload() {
        if (activeScenarioName != null) {
            try {
                load(activeScenarioName);
            } catch (ScenarioLoadException e) {
                LOGGER.error("Failed to reload scenario {}", activeScenarioName, e);
            }
        }
    }

    /**
     * 判断是否已加载剧本。
     */
    public static boolean isLoaded() {
        return activeScenario != null;
    }
}
