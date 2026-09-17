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
 * 判定规则（与 isHostile 实现一致，勿凭记忆改）：
 * 1. 双方都无阵营 → 不敌对（同类不互攻）
 * 2. 仅一方无阵营 → 敌对（无阵营生物是"混乱生物"，见谁打谁）
 * 3. 同阵营 → 不敌对
 * 4. 精确匹配 relation 定义（from→to，再反向 to→from）→ 按定义
 * 5. 通配符 * 匹配（*→from，再 *→to）→ 按定义
 * 6. 未定义 → 不敌对
 */
public class FactionRelationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionRelationManager.class);
    
    // 阵营关系缓存: from -> to -> isHostile
    private static final Map<String, Map<String, Boolean>> relationCache = new HashMap<>();
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
     * <p>
     * 无阵营生物是"混乱生物"：见谁打谁（含基地），但同类之间互不攻击。
     * 之所以这样设计，是因为模组的召唤机制无法穷举 —— 各模组用自定义 NBT
     * （JujutsuCraft 的 OWNER_UUID/friend_num 等）维系归属，逐个适配是无底洞。
     * 让无阵营生物按"敌视一切"参与战斗，既能让它们正常打架、正常死亡，
     * 又不必知道任何模组的内部约定。
     */
    public static boolean isHostile(String factionId1, String factionId2) {
        boolean empty1 = factionId1 == null || factionId1.isEmpty();
        boolean empty2 = factionId2 == null || factionId2.isEmpty();

        // 双方都无阵营 → 同类不互攻
        if (empty1 && empty2) {
            return false;
        }
        // 仅一方无阵营 → 混乱生物敌视一切
        if (empty1 || empty2) {
            return true;
        }

        // 相同阵营永远不对敌对
        if (Objects.equals(factionId1, factionId2)) {
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
        
        // 剧本没定义的关系 → 默认不敌对
        return false;
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
