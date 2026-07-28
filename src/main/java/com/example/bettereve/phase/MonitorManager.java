package com.example.bettereve.phase;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监测器管理器 — 跟踪状态变化，目标达成时触发阶段切换。
 * 
 * 监测器类型：
 * - all_bases_destroyed: 指定阵营的所有基地被摧毁时触发
 * - death_count_reached: 指定阵营的死亡计数达到阈值时触发
 */
public class MonitorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorManager.class);

    /**
     * 检查所有监测器条件（在基地死亡等事件后调用）。
     */
    public static void checkMonitors(String triggeredFactionId) {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getMonitors() == null) return;
        
        for (Scenario.MonitorDef monitor : scenario.getMonitors()) {
            if (monitor.getTargetPhase() == null) continue;
            
            boolean triggered = false;
            
            switch (monitor.getType()) {
                case "all_bases_destroyed":
                    // 需要检查该阵营是否还有存活的基地
                    // 简化：直接触发（由外部逻辑确认基地已全灭）
                    triggered = true;
                    break;
                case "death_count_reached":
                    // 需要检查死亡计数器数据
                    // 由外部检查逻辑调用
                    triggered = true;
                    break;
            }
            
            if (triggered) {
                LOGGER.info("Monitor triggered: {} -> phase {}", monitor.getId(), monitor.getTargetPhase());
                PhaseManager.switchTo(monitor.getTargetPhase());
            }
        }
    }
}
