package com.sanzineo.bettereve.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import com.sanzineo.bettereve.waypoint.WaypointManager;
import com.sanzineo.bettereve.phase.CommandExecutor;
import com.sanzineo.bettereve.phase.PhaseManager;
import net.minecraft.server.level.ServerPlayer;
import com.sanzineo.bettereve.faction.FactionRegistry;
import com.sanzineo.bettereve.faction.FactionRelationManager;

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
        load(name, null);
    }

    /**
     * 加载并激活指定剧本，并记录执行剧本指令的玩家。
     * 剧本指令由 {@link CommandExecutor} 执行，执行者取玩家 source，
     * 剧本里的 `~` 才和玩家自己敲指令一样相对玩家，而不是相对世界原点。
     */
    public static void load(String name, ServerPlayer sourcePlayer) throws ScenarioLoadException {
        if (sourcePlayer != null) {
            CommandExecutor.setSourcePlayer(sourcePlayer);
        }
        Scenario scenario = ScenarioIO.readScenario(name);
        activeScenario = scenario;
        activeScenarioName = name;
        
        // 阵营注册表、阵营关系、阶段、路径点全部由剧本驱动
        FactionRegistry.loadFromScenario(scenario);
        FactionRelationManager.loadFromScenario(scenario);
        PhaseManager.loadFromScenario(scenario);
        WaypointManager.loadFromScenario(scenario);

        // 保活区域强制加载：区域内 chunk 常驻，生物不卸载、AI 不冻结（运行时 ticket，不写盘）
        KeepAliveZoneManager.bind(KeepAliveZoneManager.getServerLevel(), scenario.getKeepAliveZone());

        // 切换剧本时清空格斗血条的主血条绑定，避免跨剧本残留
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.sanzineo.bettereve.render.VersusHealthBarOverlay.resetBindings());

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
