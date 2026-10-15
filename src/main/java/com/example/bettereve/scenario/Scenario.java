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
    /** 命名等级表：表名 → 成长参数。阵营 / 召唤物 / 投掷物 / 实体类型覆盖都按名字引用 */
    private Map<String, EntityLevelConfig> levelTables;
    /** 按实体类型（modid:name）覆盖等级表名，优先级高于一切分段配置 */
    private Map<String, String> levelTableOverrides;

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
    public Map<String, EntityLevelConfig> getLevelTables() { return levelTables; }
    public void setLevelTables(Map<String, EntityLevelConfig> levelTables) { this.levelTables = levelTables; }
    public Map<String, String> getLevelTableOverrides() { return levelTableOverrides; }
    public void setLevelTableOverrides(Map<String, String> levelTableOverrides) { this.levelTableOverrides = levelTableOverrides; }

    public static class FactionDef {
        private String id;
        private String name;
        private String color;
        private boolean showOnScoreboard = true;
        private String levelTable; // 引用 levelTables 里的表名；不写 = 该阵营没有等级系统
        
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
        public String getLevelTable() { return levelTable; }
        public void setLevelTable(String levelTable) { this.levelTable = levelTable; }
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
        private double maxDamage = 200.0; // 投掷物伤害倍率上限（箭伤 = 箭速 × 基础伤害 × 本倍率，超过即截断）
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
        public double getMaxDamage() { return maxDamage; }
        public void setMaxDamage(double maxDamage) { this.maxDamage = maxDamage; }
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
        // ⚠️ lane / order 是 routes[] 之前的旧路线模型：仍会被解析进 WaypointRegion，
        // 但已无任何逻辑读取（WaypointManager 里那几个按 lane 查询的方法外部零调用）。
        // 行走顺序请用剧本的 routes[]。见 CONFIGURATION.md「已知未接线」。
        private String lane;
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
        // ⚠️ 会被解析，但无任何代码读取（阶段流程实际由 triggers 驱动）。见 CONFIGURATION.md「已知未接线」。
        private String phaseType = "point";
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


    /**
     * 阶段切换触发器。生效取值与字段语义见 CONFIGURATION.md「triggers — 触发器」：
     * - crystal_destroyed（targetId）/ timer（threshold 秒）/ faction_wiped（factionId + threshold）
     * - entity_death / player_death / entity_state / entity_health / zone_enter / zone_leave
     * - score / command_check / all_of / any_of
     *
     * 通用字段：targetPhase（必填）/ timeout（秒，可选）/ delay（秒，可选，命中后延迟再切）/
     * winner（可选，日志胜者覆盖）
     */
    public static class TriggerDef {
        private String type;
        private String targetPhase;
        private int timeout;          // 秒；0 = 无超时
        private int delay;            // 秒；命中后延迟 N 秒再切（0 = 立即），留给播报/死亡动画/运镜
        private String winner;        // 切阶段日志的胜者覆盖（red/blue/none）；不写由模组推断

        // ---- 目标定位（entity_death / player_death / entity_state / entity_health / zone_*）----
        private String entityType;    // modid:name 或只写实体名；不写 = 任意实体
        private String name;          // 自定义名（比较前去颜色代码）；不写 = 任意名字
        private String factionId;     // 阵营过滤；faction_wiped 必写，entity_death 也用它
        private Map<String, Object> nbt;  // NBT 探针：布尔比对布尔键，其余比对字符串键
        private String player;        // player_death：玩家名；不写 = 任意玩家
        private String targetId;      // crystal_destroyed：水晶模板 id；entity_health 也用它指向水晶

        // ---- 数量 / 阈值 ----
        private int threshold;        // timer=秒；faction_wiped=存活数上限（默认 0）；score=分数门槛
        private int count;            // entity_death / player_death：本阶段累计命中多少次才切（默认 1）

        // ---- 血量（entity_health）----
        private Double ratio;         // 0~1，按目标当前最大生命算；与 below 二选一
        private Double below;         // 绝对血量；targetId 写了则按水晶模板 id 找水晶

        // ---- 区域（zone_enter / zone_leave）----
        private KeepAliveZone zone;   // 矩形区域（与 keepAliveZone 同格式，Y 轴无限高）
        private String waypointId;    // 或引用剧本 waypoints[] 的 id

        // ---- 计分（score）----
        private String kind;          // kills / crystals，默认 kills

        // ---- 逃生门（command_check）----
        private String command;       // 只读指令（execute if/unless 类）；返回值 ≥1 即命中

        // ---- 组合（all_of / any_of）----
        private List<TriggerDef> triggers;

        // ---- 轮询间隔覆盖（tick）；不写按类型默认（faction 5 / 其余 10 / command 20）----
        private Integer intervalTicks;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTargetPhase() { return targetPhase; }
        public void setTargetPhase(String targetPhase) { this.targetPhase = targetPhase; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        public int getDelay() { return delay; }
        public void setDelay(int delay) { this.delay = delay; }
        public String getWinner() { return winner; }
        public void setWinner(String winner) { this.winner = winner; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFactionId() { return factionId; }
        public void setFactionId(String factionId) { this.factionId = factionId; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public Map<String, Object> getNbt() { return nbt; }
        public void setNbt(Map<String, Object> nbt) { this.nbt = nbt; }
        public String getPlayer() { return player; }
        public void setPlayer(String player) { this.player = player; }
        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public Double getRatio() { return ratio; }
        public void setRatio(Double ratio) { this.ratio = ratio; }
        public Double getBelow() { return below; }
        public void setBelow(Double below) { this.below = below; }
        public KeepAliveZone getZone() { return zone; }
        public void setZone(KeepAliveZone zone) { this.zone = zone; }
        public String getWaypointId() { return waypointId; }
        public void setWaypointId(String waypointId) { this.waypointId = waypointId; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<TriggerDef> getTriggers() { return triggers; }
        public void setTriggers(List<TriggerDef> triggers) { this.triggers = triggers; }
        public Integer getIntervalTicks() { return intervalTicks; }
        public void setIntervalTicks(Integer intervalTicks) { this.intervalTicks = intervalTicks; }
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
        /** 可选：只对该条指令生效的召唤阵型覆盖（目前只有 bettereve spawn 读取）；不写 = 走剧本级 options.spawnLayout */
        private SpawnLayout layout;
        public int getDelay() { return delay; }
        public void setDelay(int delay) { this.delay = delay; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public SpawnLayout getLayout() { return layout; }
        public void setLayout(SpawnLayout layout) { this.layout = layout; }
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
        /** 召唤物是否默认继承宿主等级 */
        public static final boolean DEFAULT_SUMMON_INHERIT_LEVEL = true;
        /** 投掷物机制总开关默认值 */
        public static final boolean DEFAULT_PROJECTILE_ENABLED = true;
        /** 投掷物是否默认继承宿主阵营 */
        public static final boolean DEFAULT_PROJECTILE_INHERIT_FACTION = true;
        /** 投掷物是否默认继承宿主等级 */
        public static final boolean DEFAULT_PROJECTILE_INHERIT_LEVEL = true;
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
        private boolean overheadHealthBar = true;    // 实体头顶血条总开关（与格斗血条相互独立）
        private float healthBarScale = 1.0f;         // 头顶血条缩放倍率（0.1~3.0）
        private boolean healthBarLevelText = true;   // 头顶血条是否显示 Lv 文字（生物 NBT 优先）
        private boolean healthBarHealthText = true;  // 头顶血条是否显示血量数字（生物 NBT 优先）
        private boolean versusHealthBar = false;     // 格斗血条（顶部阵营血条）总开关
        private int versusHealthBarSubCount = 2;     // 每个阵营最多显示几条次要血条
        private String versusHealthBarStyle = "plain"; // 格斗血条填充样式：plain / stripe / grid
        private boolean versusHealthBarHealthText = true; // 主血条名字旁是否显示「当前/上限」血量

        // ==================== 召唤物机制（原模组配置迁入剧本） ====================
        private SummonConfig summon;
        // ==================== 投掷物机制（原投掷物继承 handler 内置行为迁入剧本） ====================
        private ProjectileConfig projectile;
        // ==================== 指令召唤阵型（原模组配置迁入剧本） ====================
        private SpawnLayout spawnLayout;
        // ==================== 剧本裁决规则（代码提供能力，剧本写具体实例） ====================
        private Rules rules;
        // ==================== 日光燃烧开关（防骷髅 / 僵尸 / 幻翼被日光点燃） ====================
        private SunBurnConfig sunBurn;

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
        public boolean isOverheadHealthBar() { return overheadHealthBar; }
        public void setOverheadHealthBar(boolean overheadHealthBar) { this.overheadHealthBar = overheadHealthBar; }
        public float getHealthBarScale() { return healthBarScale; }
        public void setHealthBarScale(float healthBarScale) { this.healthBarScale = healthBarScale; }
        public boolean isHealthBarLevelText() { return healthBarLevelText; }
        public void setHealthBarLevelText(boolean healthBarLevelText) { this.healthBarLevelText = healthBarLevelText; }
        public boolean isHealthBarHealthText() { return healthBarHealthText; }
        public void setHealthBarHealthText(boolean healthBarHealthText) { this.healthBarHealthText = healthBarHealthText; }
        public boolean isVersusHealthBar() { return versusHealthBar; }
        public void setVersusHealthBar(boolean versusHealthBar) { this.versusHealthBar = versusHealthBar; }
        public int getVersusHealthBarSubCount() { return versusHealthBarSubCount; }
        public void setVersusHealthBarSubCount(int versusHealthBarSubCount) { this.versusHealthBarSubCount = versusHealthBarSubCount; }
        public String getVersusHealthBarStyle() { return versusHealthBarStyle; }
        public void setVersusHealthBarStyle(String versusHealthBarStyle) { this.versusHealthBarStyle = versusHealthBarStyle; }
        public boolean isVersusHealthBarHealthText() { return versusHealthBarHealthText; }
        public void setVersusHealthBarHealthText(boolean versusHealthBarHealthText) { this.versusHealthBarHealthText = versusHealthBarHealthText; }
        public SummonConfig getSummon() { return summon; }
        public void setSummon(SummonConfig summon) { this.summon = summon; }
        public ProjectileConfig getProjectile() { return projectile; }
        public void setProjectile(ProjectileConfig projectile) { this.projectile = projectile; }
        public SpawnLayout getSpawnLayout() { return spawnLayout; }
        public void setSpawnLayout(SpawnLayout spawnLayout) { this.spawnLayout = spawnLayout; }
        public Rules getRules() { return rules; }
        public void setRules(Rules rules) { this.rules = rules; }
        public SunBurnConfig getSunBurn() { return sunBurn; }
        public void setSunBurn(SunBurnConfig sunBurn) { this.sunBurn = sunBurn; }
    }

    /**
     * 日光燃烧开关。剧本未写 {@code sunBurn} 段时 = 原版行为（照常被日光点燃）。
     *
     * <p>实现见 {@code mixin/MobSunBurnMixin}：注入 {@code Mob.isSunBurnTick()} 的 HEAD，
     * 命中本段配置且不在 {@code except} 名单里时直接返回 false —— 比原版更省
     * （跳过光照 / 可见天 / 随机数那套判定），而不是多花。
     *
     * <p>只压「日光点燃」这一条路：岩浆 / 火焰 / 闪电 / {@code /ignite} 照旧。
     */
    public static class SunBurnConfig {
        /**
         * 默认 **false** = 没写这一段就不被日光点燃（**防护默认开着**）；写 {@code true} 才走原版（会烧）。
         * 这样老剧本 / 预设剧本 / 新导出的剧本都不用改就一致。
         */
        public static final boolean DEFAULT_ENABLED = false;

        private Boolean enabled;
        /** 例外：这些实体**照旧**被日光点燃。写实体 id；末尾带 {@code *} 表示按前缀匹配（如 {@code somemod:*}） */
        private List<String> except;

        public boolean isEnabled() {
            return enabled == null ? DEFAULT_ENABLED : enabled;
        }

        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public List<String> getExcept() { return except; }

        public void setExcept(List<String> except) { this.except = except; }
    }

    /**
     * 召唤物机制配置。剧本未写 summon 段时，全部使用代码内默认值。
     */
    public static class SummonConfig {
        /** 召唤物机制总开关默认值 */
        public static final boolean DEFAULT_ENABLED = true;

        private Boolean enabled;                  // 总开关，默认 true
        private Boolean inheritFaction;           // 继承宿主阵营，默认 true
        private Boolean inheritLevel;             // 继承宿主等级，默认 true
        private Boolean hideHealthBar;            // 召唤物不显示血条，默认 true
        private Boolean countsForScore;           // 召唤物计分，默认 false（不计分）
        private String levelTable;                // 召唤物专用等级表名；不写 = 走阵营表
        private Map<String, Boolean> healthBarOverrides; // 按实体类型覆盖血条，优先级最高
        private Map<String, String> factionOverrides;    // 按实体类型强制阵营，优先级最高

        public boolean isEnabled() {
            return enabled == null ? DEFAULT_ENABLED : enabled;
        }
        public boolean isInheritFaction() {
            return inheritFaction == null ? ScenarioOptions.DEFAULT_SUMMON_INHERIT_FACTION : inheritFaction;
        }
        public boolean isInheritLevel() {
            return inheritLevel == null ? ScenarioOptions.DEFAULT_SUMMON_INHERIT_LEVEL : inheritLevel;
        }
        public boolean isHideHealthBar() {
            return hideHealthBar == null ? ScenarioOptions.DEFAULT_SUMMON_HIDE_HEALTH_BAR : hideHealthBar;
        }
        public boolean isCountsForScore() {
            return countsForScore == null ? ScenarioOptions.DEFAULT_SUMMON_COUNTS_FOR_SCORE : countsForScore;
        }
        public Map<String, Boolean> getHealthBarOverrides() { return healthBarOverrides; }

        public Map<String, String> getFactionOverrides() { return factionOverrides; }

        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public void setInheritFaction(Boolean inheritFaction) { this.inheritFaction = inheritFaction; }
        public void setInheritLevel(Boolean inheritLevel) { this.inheritLevel = inheritLevel; }
        public void setHideHealthBar(Boolean hideHealthBar) { this.hideHealthBar = hideHealthBar; }
        public void setCountsForScore(Boolean countsForScore) { this.countsForScore = countsForScore; }
        public void setHealthBarOverrides(Map<String, Boolean> healthBarOverrides) { this.healthBarOverrides = healthBarOverrides; }
        public void setFactionOverrides(Map<String, String> factionOverrides) { this.factionOverrides = factionOverrides; }
        public String getLevelTable() { return levelTable; }
        public void setLevelTable(String levelTable) { this.levelTable = levelTable; }
    }

    /**
     * 投掷物机制配置。剧本未写 projectile 段时，全部使用代码内默认值。
     */
    public static class ProjectileConfig {
        private Boolean enabled;         // 总开关，默认 true
        private Boolean inheritFaction;  // 继承宿主阵营，默认 true
        private Boolean inheritLevel;    // 继承宿主等级，默认 true
        private String levelTable;       // 投掷物专用等级表名；不写 = 走阵营表

        public boolean isEnabled() {
            return enabled == null ? ScenarioOptions.DEFAULT_PROJECTILE_ENABLED : enabled;
        }
        public boolean isInheritFaction() {
            return inheritFaction == null ? ScenarioOptions.DEFAULT_PROJECTILE_INHERIT_FACTION : inheritFaction;
        }
        public boolean isInheritLevel() {
            return inheritLevel == null ? ScenarioOptions.DEFAULT_PROJECTILE_INHERIT_LEVEL : inheritLevel;
        }
        public String getLevelTable() { return levelTable; }

        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public void setInheritFaction(Boolean inheritFaction) { this.inheritFaction = inheritFaction; }
        public void setInheritLevel(Boolean inheritLevel) { this.inheritLevel = inheritLevel; }
        public void setLevelTable(String levelTable) { this.levelTable = levelTable; }
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

    /**
     * 剧本裁决规则：把"剧本特例"从代码里抽出来。
     *
     * 代码只提供规则种类与执行器（见 ScenarioRuleHandler），
     * 具体针对哪个实体、满足什么状态、杀几次，全部写在剧本的 options.rules 段里。
     */
    public static class Rules {
        private List<LethalHit> lethalHits;   // 命中裁决：指定攻击者打到指定受害者 → 这一击强制致死
        private List<StateKill> stateKills;   // 状态裁决：指定实体进入指定状态 → 连续击杀并持续监控
        private List<AttributeOverride> attributeOverrides; // 属性覆盖：指定实体类型的属性按倍率改写

        public List<LethalHit> getLethalHits() { return lethalHits; }
        public void setLethalHits(List<LethalHit> lethalHits) { this.lethalHits = lethalHits; }
        public List<StateKill> getStateKills() { return stateKills; }
        public void setStateKills(List<StateKill> stateKills) { this.stateKills = stateKills; }
        public List<AttributeOverride> getAttributeOverrides() { return attributeOverrides; }
        public void setAttributeOverrides(List<AttributeOverride> attributeOverrides) { this.attributeOverrides = attributeOverrides; }
    }

    /**
     * 命中裁决：attacker 命中 victim 时，把这一击的伤害改写成致死量。
     *
     * attackerFlag 用来区分"同一实体的不同形态/招式"——例如咒术回战的茈，
     * 只有残血大招那一发才带 NBT explode=true，普通茈没有。
     */
    public static class LethalHit {
        /** 默认致死伤害量：远高于任何血量与减伤，确保一击必死 */
        public static final double DEFAULT_DAMAGE = 1000000.0;

        private String attacker;      // 攻击者实体类型，如 jujutsucraft:purple（可只写实体名）
        private String attackerFlag;  // 可选：攻击者 persistentData 里必须为 true 的布尔键
        private String victim;        // 受害者实体类型
        private Double damage;        // 致死伤害量，默认 DEFAULT_DAMAGE

        public String getAttacker() { return attacker; }
        public String getAttackerFlag() { return attackerFlag; }
        public String getVictim() { return victim; }
        public double getDamage() { return damage == null ? DEFAULT_DAMAGE : damage; }

        public void setAttacker(String attacker) { this.attacker = attacker; }
        public void setAttackerFlag(String attackerFlag) { this.attackerFlag = attackerFlag; }
        public void setVictim(String victim) { this.victim = victim; }
        public void setDamage(Double damage) { this.damage = damage; }
    }

    /**
     * 状态裁决：type 实体只要满足 nbt 探针（实体序列化 NBT 里的键值），
     * 就连续击杀 kills 次；只要它还活着，之后的每一轮轮询都会再杀一次。
     *
     * 探针值支持布尔与字符串：布尔比对 NBT 布尔键，其它比对字符串键。
     */
    public static class StateKill {
        public static final int DEFAULT_KILLS = 3;
        public static final int DEFAULT_INTERVAL_TICKS = 10;

        private String type;              // 实体类型，如 jujutsucraft:sukuna_fushiguro
        private Map<String, Object> nbt;  // 状态探针：序列化 NBT 里必须匹配的键值
        private Integer kills;            // 每次触发的连杀次数
        private Integer intervalTicks;    // 轮询间隔（tick）

        public String getType() { return type; }
        public Map<String, Object> getNbt() { return nbt; }
        public int getKills() { return kills == null ? DEFAULT_KILLS : Math.max(1, kills); }
        public int getIntervalTicks() {
            return intervalTicks == null ? DEFAULT_INTERVAL_TICKS : Math.max(1, intervalTicks);
        }

        public void setType(String type) { this.type = type; }
        public void setNbt(Map<String, Object> nbt) { this.nbt = nbt; }
        public void setKills(Integer kills) { this.kills = kills; }
        public void setIntervalTicks(Integer intervalTicks) { this.intervalTicks = intervalTicks; }
    }

    /**
     * 属性覆盖：把指定实体类型的属性按倍率改写（剧本用来给单个实体加厚/削弱）。
     *
     * 只在实体"新加入世界"时应用一次；存档加载的实体跳过，避免倍率被反复叠乘。
     * 未写的项不生效（倍率默认 1.0）。
     */
    public static class AttributeOverride {
        private String type;               // 实体类型，如 jujutsucraft:eight_handled_sword_...
        private Double healthMultiplier;   // 最大生命倍率
        private Double armorMultiplier;    // 护甲倍率
        private Double attackMultiplier;   // 攻击倍率

        public String getType() { return type; }
        public double getHealthMultiplier() { return healthMultiplier == null ? 1.0 : healthMultiplier; }
        public double getArmorMultiplier() { return armorMultiplier == null ? 1.0 : armorMultiplier; }
        public double getAttackMultiplier() { return attackMultiplier == null ? 1.0 : attackMultiplier; }

        /** 三项都没写 = 空规则，直接跳过 */
        public boolean isEmpty() {
            return healthMultiplier == null && armorMultiplier == null && attackMultiplier == null;
        }

        public void setType(String type) { this.type = type; }
        public void setHealthMultiplier(Double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
        public void setArmorMultiplier(Double armorMultiplier) { this.armorMultiplier = armorMultiplier; }
        public void setAttackMultiplier(Double attackMultiplier) { this.attackMultiplier = attackMultiplier; }
    }
}
