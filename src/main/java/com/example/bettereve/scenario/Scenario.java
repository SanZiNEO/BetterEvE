package com.example.bettereve.scenario;

import java.util.List;
import java.util.Map;

/**
 * 剧本数据模型 — 一个 JSON 文件定义一个完整的游戏模式。
 */
public class Scenario {
    private String id;
    private String name;
    private String description;
    private List<FactionDef> factions;
    private List<RelationDef> relations;
    private List<WaypointRegionDef> waypoints;
    private List<RouteDef> routes;
    private List<CrystalTemplateDef> crystals;
    private List<PhaseDef> phases;
    private ScenarioOptions options;
    private KeepAliveZone keepAliveZone;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<FactionDef> getFactions() { return factions; }
    public void setFactions(List<FactionDef> factions) { this.factions = factions; }
    public List<RelationDef> getRelations() { return relations; }
    public void setRelations(List<RelationDef> relations) { this.relations = relations; }
    public List<WaypointRegionDef> getWaypoints() { return waypoints; }
    public void setWaypoints(List<WaypointRegionDef> waypoints) { this.waypoints = waypoints; }
    public List<RouteDef> getRoutes() { return routes; }
    public void setRoutes(List<RouteDef> routes) { this.routes = routes; }
    public List<CrystalTemplateDef> getCrystals() { return crystals; }
    public void setCrystals(List<CrystalTemplateDef> crystals) { this.crystals = crystals; }
    public List<PhaseDef> getPhases() { return phases; }
    public void setPhases(List<PhaseDef> phases) { this.phases = phases; }
    public ScenarioOptions getOptions() { return options; }
    public KeepAliveZone getKeepAliveZone() { return keepAliveZone; }
    public void setKeepAliveZone(KeepAliveZone keepAliveZone) { this.keepAliveZone = keepAliveZone; }
    public void setOptions(ScenarioOptions options) { this.options = options; }

    public static class FactionDef {
        private String id;
        private String name;
        private String color;
        private boolean showOnScoreboard = true;
        private EntityLevelConfig entitylvConfig; // 没写 = 没有等级系统
        
        public FactionDef() {}
        public FactionDef(String id, String name, String color) {
            this.id = id; this.name = name; this.color = color;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public int getColorInt() {
            if (color == null || !color.startsWith("#")) return 0xFFFFFF;
            try { return Integer.parseInt(color.substring(1), 16); }
            catch (NumberFormatException e) { return 0xFFFFFF; }
        }
        public boolean isShowOnScoreboard() { return showOnScoreboard; }
        public void setShowOnScoreboard(boolean showOnScoreboard) { this.showOnScoreboard = showOnScoreboard; }
        public EntityLevelConfig getEntityLevelConfig() { return entitylvConfig; }
        public void setEntityLevelConfig(EntityLevelConfig config) { this.entitylvConfig = config; }
    }

    public static class EntityLevelConfig {
        private int maxLevel = 500;
        private double healthBonus = 10.0;
        private double attackBonus = 10.0;
        private double armorBonus = 7.0;
        private double knockbackBonus = 1.2;
        private double healthExponent = 1.4;
        private double attackExponent = 1.42;
        private double armorExponent = 0.9;
        private double knockbackExponent = 0.95;
        public int getMaxLevel() { return maxLevel; }
        public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }
        public double getHealthBonus() { return healthBonus; }
        public void setHealthBonus(double healthBonus) { this.healthBonus = healthBonus; }
        public double getAttackBonus() { return attackBonus; }
        public void setAttackBonus(double attackBonus) { this.attackBonus = attackBonus; }
        public double getArmorBonus() { return armorBonus; }
        public void setArmorBonus(double armorBonus) { this.armorBonus = armorBonus; }
        public double getKnockbackBonus() { return knockbackBonus; }
        public void setKnockbackBonus(double knockbackBonus) { this.knockbackBonus = knockbackBonus; }
        public double getHealthExponent() { return healthExponent; }
        public void setHealthExponent(double healthExponent) { this.healthExponent = healthExponent; }
        public double getAttackExponent() { return attackExponent; }
        public void setAttackExponent(double attackExponent) { this.attackExponent = attackExponent; }
        public double getArmorExponent() { return armorExponent; }
        public void setArmorExponent(double armorExponent) { this.armorExponent = armorExponent; }
        public double getKnockbackExponent() { return knockbackExponent; }
        public void setKnockbackExponent(double knockbackExponent) { this.knockbackExponent = knockbackExponent; }
    }

    public static class KeepAliveZone {
        private double x1, z1, x2, z2;
        public double getX1() { return x1; }
        public void setX1(double x1) { this.x1 = x1; }
        public double getZ1() { return z1; }
        public void setZ1(double z1) { this.z1 = z1; }
        public double getX2() { return x2; }
        public void setX2(double x2) { this.x2 = x2; }
        public double getZ2() { return z2; }
        public void setZ2(double z2) { this.z2 = z2; }

        /** 判断 XZ 坐标是否在保活区域内 */
        public boolean contains(double x, double z) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }
    public static class RelationDef {
        private String from;
        private String to;
        private String type;
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class WaypointRegionDef {
        private String id;
        private String factionId;
        private String lane; // 路线标识（a/b/c…）；缺省为 null → 归入默认路线（单一路线，旧剧本兼容）
        private int order;
        private double x1, z1, x2, z2;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFactionId() { return factionId; }
        public void setFactionId(String factionId) { this.factionId = factionId; }
        public String getLane() { return lane; }
        public void setLane(String lane) { this.lane = lane; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public double getX1() { return x1; }
        public void setX1(double x1) { this.x1 = x1; }
        public double getZ1() { return z1; }
        public void setZ1(double z1) { this.z1 = z1; }
        public double getX2() { return x2; }
        public void setX2(double x2) { this.x2 = x2; }
        public double getZ2() { return z2; }
        public void setZ2(double z2) { this.z2 = z2; }
    }

    public static class RouteDef {
        private String id;
        private List<String> waypoints;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getWaypoints() { return waypoints; }
        public void setWaypoints(List<String> waypoints) { this.waypoints = waypoints; }
    }

    public static class CrystalTemplateDef {
        private String id;
        private String factionId;
        private double x, y, z;
        private int health;
        private int attackDamage;
        private int armor;
        private double attackRange;
        private double attackCooldown;
        private String sizeKey;
        private double modelScale;
        private boolean showHealthBar;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFactionId() { return factionId; }
        public void setFactionId(String factionId) { this.factionId = factionId; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
        public int getHealth() { return health; }
        public void setHealth(int health) { this.health = health; }
        public int getAttackDamage() { return attackDamage; }
        public void setAttackDamage(int attackDamage) { this.attackDamage = attackDamage; }
        public int getArmor() { return armor; }
        public void setArmor(int armor) { this.armor = armor; }
        public double getAttackRange() { return attackRange; }
        public void setAttackRange(double attackRange) { this.attackRange = attackRange; }
        public double getAttackCooldown() { return attackCooldown; }
        public void setAttackCooldown(double attackCooldown) { this.attackCooldown = attackCooldown; }
        public String getSizeKey() { return sizeKey; }
        public void setSizeKey(String sizeKey) { this.sizeKey = sizeKey; }
        public double getModelScale() { return modelScale; }
        public void setModelScale(double modelScale) { this.modelScale = modelScale; }
        public boolean isShowHealthBar() { return showHealthBar; }
        public void setShowHealthBar(boolean showHealthBar) { this.showHealthBar = showHealthBar; }
    }
    public static class PhaseDef {
        private String id;
        private String phaseType = "point"; // "loop" | "branch" | "point"
        private String description;
        private AnnounceDef announce;
        private List<CommandDef> commands;
        private List<TriggerDef> triggers;
        private SpawnBasesDef spawnBases;
        private int resetAfter;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPhaseType() { return phaseType; }
        public void setPhaseType(String phaseType) { this.phaseType = phaseType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public AnnounceDef getAnnounce() { return announce; }
        public void setAnnounce(AnnounceDef announce) { this.announce = announce; }
        public List<CommandDef> getCommands() { return commands; }
        public void setCommands(List<CommandDef> commands) { this.commands = commands; }
        public List<TriggerDef> getTriggers() { return triggers; }
        public void setTriggers(List<TriggerDef> triggers) { this.triggers = triggers; }
        public SpawnBasesDef getSpawnBases() { return spawnBases; }
        public void setSpawnBases(SpawnBasesDef spawnBases) { this.spawnBases = spawnBases; }
        public int getResetAfter() { return resetAfter; }
        public void setResetAfter(int resetAfter) { this.resetAfter = resetAfter; }
    }


    public static class TriggerDef {
        private String type;       // "base_destroyed" | "entity_death_count" | "timer" | "custom"
        private String factionId;
        private String targetId;
        private int threshold;
        private String targetPhase;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFactionId() { return factionId; }
        public void setFactionId(String factionId) { this.factionId = factionId; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public String getTargetPhase() { return targetPhase; }
        public void setTargetPhase(String targetPhase) { this.targetPhase = targetPhase; }
    }
    public static class AnnounceDef {
        private String message;
        private int duration;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
    }

    public static class CommandDef {
        private int delay;
        private String command;
        public int getDelay() { return delay; }
        public void setDelay(int delay) { this.delay = delay; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
    }

    public static class SpawnBasesDef {
        private String templateId;
        private List<SpawnPosition> positions;
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public List<SpawnPosition> getPositions() { return positions; }
        public void setPositions(List<SpawnPosition> positions) { this.positions = positions; }
        public static class SpawnPosition {
            private double x, y, z;
            public double getX() { return x; }
            public void setX(double x) { this.x = x; }
            public double getY() { return y; }
            public void setY(double y) { this.y = y; }
            public double getZ() { return z; }
            public void setZ(double z) { this.z = z; }
        }
    }

    public static class ScenarioOptions {
        // ==================== 代码内默认值（模组自带配置，无需额外配置文件） ====================
        /** 召唤物是否默认继承宿主阵营 */
        public static final boolean DEFAULT_SUMMON_INHERIT_FACTION = true;
        /** 召唤物是否默认不显示血条。默认 false = 显示血条（便于观察继承效果）；
         *  想隐藏时在剧本 options.summon.hideHealthBar 写 true。 */
        public static final boolean DEFAULT_SUMMON_HIDE_HEALTH_BAR = false;
        /** 召唤物默认是否计分 */
        public static final boolean DEFAULT_SUMMON_COUNTS_FOR_SCORE = false;
        /** 指令召唤默认阵型 */
        public static final Formation DEFAULT_SPAWN_FORMATION = Formation.CIRCLE;
        /** 阵型默认半径（格） */
        public static final double DEFAULT_SPAWN_RADIUS = 2.0;
        /** 阵型默认间距（格，直线/网格用） */
        public static final double DEFAULT_SPAWN_SPACING = 2.0;
        /** 默认张角（度，360 = 整圆；<=0 退化为直线） */
        public static final double DEFAULT_SPAWN_ARC = 360.0;

        private String playersDefaultFaction; // 没写 = 玩家未分配（无阵营）
        private double targetingRange = 64.0;        // 常态巡敌距离（格），随地图设计配置
        private double targetingFocusRange = 10.0;   // 近距离重扫距离（格）：范围内出现非基地敌对目标即切换
        private int targetingFocusInterval = 20;     // 近距离重扫间隔（tick）
        private boolean hudEnabled = true;           // 全局 HUD 总开关（原 BetterEvEConfig.global_hudEnabled 迁入）

        // ==================== 召唤物机制（原模组配置迁入剧本） ====================
        private SummonConfig summon;
        // ==================== 指令召唤阵型（原模组配置迁入剧本） ====================
        private SpawnLayout spawnLayout;

        public String getPlayersDefaultFaction() { return playersDefaultFaction; }
        public void setPlayersDefaultFaction(String playersDefaultFaction) { this.playersDefaultFaction = playersDefaultFaction; }
        public double getTargetingRange() { return targetingRange; }
        public void setTargetingRange(double targetingRange) { this.targetingRange = targetingRange; }
        public double getTargetingFocusRange() { return targetingFocusRange; }
        public void setTargetingFocusRange(double targetingFocusRange) { this.targetingFocusRange = targetingFocusRange; }
        public int getTargetingFocusInterval() { return targetingFocusInterval; }
        public void setTargetingFocusInterval(int targetingFocusInterval) { this.targetingFocusInterval = targetingFocusInterval; }
        public boolean isHudEnabled() { return hudEnabled; }
        public void setHudEnabled(boolean hudEnabled) { this.hudEnabled = hudEnabled; }
        public SummonConfig getSummon() { return summon; }
        public void setSummon(SummonConfig summon) { this.summon = summon; }
        public SpawnLayout getSpawnLayout() { return spawnLayout; }
        public void setSpawnLayout(SpawnLayout spawnLayout) { this.spawnLayout = spawnLayout; }
    }

    /**
     * 召唤物机制配置。剧本未写 summon 段时，全部使用代码内默认值。
     */
    public static class SummonConfig {
        /** 召唤物机制总开关默认值 */
        public static final boolean DEFAULT_ENABLED = true;

        private Boolean enabled;                  // 总开关，默认 true
        private Boolean inheritFaction;           // 继承宿主阵营，默认 true
        private Boolean hideHealthBar;            // 召唤物不显示血条，默认 true
        private Boolean countsForScore;           // 召唤物计分，默认 false（不计分）
        private Map<String, Boolean> healthBarOverrides; // 按实体类型覆盖血条，优先级最高

        public boolean isEnabled() {
            return enabled == null ? DEFAULT_ENABLED : enabled;
        }
        public boolean isInheritFaction() {
            return inheritFaction == null ? ScenarioOptions.DEFAULT_SUMMON_INHERIT_FACTION : inheritFaction;
        }
        public boolean isHideHealthBar() {
            return hideHealthBar == null ? ScenarioOptions.DEFAULT_SUMMON_HIDE_HEALTH_BAR : hideHealthBar;
        }
        public boolean isCountsForScore() {
            return countsForScore == null ? ScenarioOptions.DEFAULT_SUMMON_COUNTS_FOR_SCORE : countsForScore;
        }
        public Map<String, Boolean> getHealthBarOverrides() { return healthBarOverrides; }

        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public void setInheritFaction(Boolean inheritFaction) { this.inheritFaction = inheritFaction; }
        public void setHideHealthBar(Boolean hideHealthBar) { this.hideHealthBar = hideHealthBar; }
        public void setCountsForScore(Boolean countsForScore) { this.countsForScore = countsForScore; }
        public void setHealthBarOverrides(Map<String, Boolean> healthBarOverrides) { this.healthBarOverrides = healthBarOverrides; }
    }

    /**
     * 指令召唤阵型配置。剧本未写 spawnLayout 段时使用代码内默认值（圆形）。
     */
    public static class SpawnLayout {
        private Formation formation;      // 阵型，默认 circle
        private Double radius;            // 半径（圆/方/菱/三角）
        private Double spacing;           // 间距（直线/网格）
        private Double arc;               // 张角（度）：360 整圆，180 半圆，<=0 退化直线
        private Double width;             // 矩形宽（X 方向）
        private Double depth;             // 矩形深（Z 方向）

        public Formation getFormation() {
            return formation == null ? ScenarioOptions.DEFAULT_SPAWN_FORMATION : formation;
        }
        public double getRadius() {
            return radius == null ? ScenarioOptions.DEFAULT_SPAWN_RADIUS : radius;
        }
        public double getSpacing() {
            return spacing == null ? ScenarioOptions.DEFAULT_SPAWN_SPACING : spacing;
        }
        public double getArc() {
            return arc == null ? ScenarioOptions.DEFAULT_SPAWN_ARC : arc;
        }
        public double getWidth() {
            return width == null ? getRadius() * 2.0 : width;
        }
        public double getDepth() {
            return depth == null ? getRadius() * 2.0 : depth;
        }

        public void setFormation(Formation formation) { this.formation = formation; }
        public void setRadius(Double radius) { this.radius = radius; }
        public void setSpacing(Double spacing) { this.spacing = spacing; }
        public void setArc(Double arc) { this.arc = arc; }
        public void setWidth(Double width) { this.width = width; }
        public void setDepth(Double depth) { this.depth = depth; }
    }

    /**
     * 阵型枚举。JSON 中用 snake_case 字符串：circle / square / diamond / rectangle /
     * triangle / line_x / line_z / grid / point
     */
    public enum Formation {
        /** 圆形（默认）：以召唤点为圆心，arc 控制张角 */
        @com.google.gson.annotations.SerializedName("circle") CIRCLE,
        /** 正方形边框（以召唤点为中心） */
        @com.google.gson.annotations.SerializedName("square") SQUARE,
        /** 菱形边框（以召唤点为中心，45° 旋转的正方形） */
        @com.google.gson.annotations.SerializedName("diamond") DIAMOND,
        /** 矩形边框（width × depth，以召唤点为中心） */
        @com.google.gson.annotations.SerializedName("rectangle") RECTANGLE,
        /** 三角形三顶点起排 */
        @com.google.gson.annotations.SerializedName("triangle") TRIANGLE,
        /** 朝 X 方向的直线 */
        @com.google.gson.annotations.SerializedName("line_x") LINE_X,
        /** 朝 Z 方向的直线 */
        @com.google.gson.annotations.SerializedName("line_z") LINE_Z,
        /** 方形网格填充（多生物密集排列） */
        @com.google.gson.annotations.SerializedName("grid") GRID,
        /** 全部重叠在召唤点（count=1 的等价行为） */
        @com.google.gson.annotations.SerializedName("point") POINT
    }
}
