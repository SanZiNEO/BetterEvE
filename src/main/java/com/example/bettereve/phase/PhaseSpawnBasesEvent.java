package com.example.bettereve.phase;

import com.example.bettereve.scenario.Scenario;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * 阶段建筑生成事件 — 阶段切换需要生成建筑时触发。
 * 监听者应在指定位置创建 CrystalEntity。
 */
public class PhaseSpawnBasesEvent extends Event {
    private final Scenario.CrystalTemplateDef template;
    private final List<Scenario.SpawnBasesDef.SpawnPosition> positions;

    public PhaseSpawnBasesEvent(Scenario.CrystalTemplateDef template, List<Scenario.SpawnBasesDef.SpawnPosition> positions) {
        this.template = template;
        this.positions = positions;
    }

    public Scenario.CrystalTemplateDef getTemplate() { return template; }
    public List<Scenario.SpawnBasesDef.SpawnPosition> getPositions() { return positions; }
}
