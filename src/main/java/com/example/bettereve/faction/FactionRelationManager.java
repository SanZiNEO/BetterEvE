package com.example.bettereve.faction;

import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 阵营关系管理器 — 从当前活跃 Scenario 加载阵营关系，提供敌对判定。
 * 
 * 判定规则：
 * 1. 同阵营 → 不敌对
 * 2. neutral 阵营 → 不与任何人敌对
 * 3. 精确匹配 relation 定义 → 按定义
 * 4. 通配符 * 匹配 → 按定义
 * 5. 未定义 → 默认敌对
 */
public class FactionRelationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionRelationManager.class);
    
    // 阵营关系缓存: from -> to -> isHostile
    private static final Map<String, Map<String, Boolean>> relationCache = new HashMap<>();
    private static boolean defaultHostile = true;
    private static boolean loaded = false;

    /**
     * 从剧本加载阵营关系。
     */
    public static void loadFromScenario(Scenario scenario) {
        relationCache.clear();
        loaded = false;
        
        if (scenario == null || scenario.getRelations() == null) {
            LOGGER.warn("No relations defined in scenario, using defaults");
            return;
        }
        
        for (Scenario.RelationDef rel : scenario.getRelations()) {
            String from = rel.getFrom();
            String to = rel.getTo();
            boolean hostile = "hostile".equals(rel.getType());
            
            relationCache.computeIfAbsent(from, k -> new HashMap<>()).put(to, hostile);
        }
        
        loaded = true;
        LOGGER.info("Loaded {} relations from scenario {}", relationCache.size(), scenario.getId());
    }

    /**
     * 判断两个阵营是否敌对。
     */
    public static boolean isHostile(String factionId1, String factionId2) {
        // 相同阵营永远不对敌对
        if (Objects.equals(factionId1, factionId2)) {
            return false;
        }
        
        // neutral 阵营不与任何人敌对
        if ("neutral".equals(factionId1) || "neutral".equals(factionId2)) {
            return false;
        }
        
        // 如果尚未从剧本加载，自动尝试加载
        if (!loaded) {
            Scenario scenario = ScenarioManager.getActiveScenario();
            if (scenario != null) {
                loadFromScenario(scenario);
            }
        }
        
        // 先在缓存中查找精确匹配
        Boolean result = lookupRelation(factionId1, factionId2);
        if (result != null) return result;
        
        result = lookupRelation(factionId2, factionId1);
        if (result != null) return result;
        
        // 查找通配符匹配
        result = lookupRelation("*", factionId1);
        if (result != null) return result;
        
        result = lookupRelation("*", factionId2);
        if (result != null) return result;
        
        // 未定义 → 默认敌对
        return defaultHostile;
    }
    
    /**
     * 判断两个实体是否敌对。
     */
    public static boolean isEntitiesHostile(Entity entity1, Entity entity2) {
        if (entity1 == null || entity2 == null) {
            return false;
        }
        
        String factionId1 = FactionHelper.getEntityFactionId(entity1);
        String factionId2 = FactionHelper.getEntityFactionId(entity2);
        
        return isHostile(factionId1, factionId2);
    }
    
    private static Boolean lookupRelation(String from, String to) {
        Map<String, Boolean> fromRelations = relationCache.get(from);
        if (fromRelations != null && fromRelations.containsKey(to)) {
            return fromRelations.get(to);
        }
        return null;
    }

    /**
     * 强制重新加载。
     */
    public static void reload() {
        loaded = false;
        relationCache.clear();
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario != null) {
            loadFromScenario(scenario);
        }
    }
}
