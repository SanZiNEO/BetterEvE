package com.example.bettereve.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import org.apache.commons.lang3.tuple.Pair;

// BetterEvE 全局配置类：按模块组织所有配置项
public class BetterEvEConfig {
    // 配置规范实例（供Forge注册）
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final BetterEvEConfig INSTANCE;

    // ===================== 1. 渲染系统配置 =====================
    public final ForgeConfigSpec.ConfigValue<Boolean> render_showHealthPercent;
    public final ForgeConfigSpec.DoubleValue render_healthTextOffsetY;
    public final ForgeConfigSpec.ConfigValue<Boolean> render_showHealthBar;
    public final ForgeConfigSpec.DoubleValue render_healthBarWidth;
    public final ForgeConfigSpec.DoubleValue render_healthBarHeight;
    public final ForgeConfigSpec.DoubleValue render_healthBarOffsetY;

    // ===================== 2. 寻敌系统配置 =====================
    public final ForgeConfigSpec.DoubleValue targeting_defaultFollowRange;
    public final ForgeConfigSpec.ConfigValue<String> targeting_entityFollowRange;
    public final ForgeConfigSpec.ConfigValue<String> targeting_factionFollowRange;

    // ===================== 3. 计数器系统配置 =====================
    public final ForgeConfigSpec.ConfigValue<Boolean> counter_hudEnabled;
    public final ForgeConfigSpec.DoubleValue counter_hudPosX;
    public final ForgeConfigSpec.DoubleValue counter_hudPosY;
    public final ForgeConfigSpec.ConfigValue<Boolean> counter_tabPanelEnabled;
    public final ForgeConfigSpec.ConfigValue<String> counter_tabPanelKey;
    public final ForgeConfigSpec.ConfigValue<Boolean> counter_countEntityType;

    // ===================== 4. 基地系统配置 =====================
    public final ForgeConfigSpec.IntValue base_defaultHealth;
    public final ForgeConfigSpec.IntValue base_victoryAnnounceTime;
    public final ForgeConfigSpec.IntValue base_clearRadius;
    public final ForgeConfigSpec.ConfigValue<Boolean> base_onlyHostileAttack;

    // ===================== 5. 等级系统配置 =====================
    public final ForgeConfigSpec.DoubleValue level_healthBonus;
    public final ForgeConfigSpec.DoubleValue level_attackBonus;
    public final ForgeConfigSpec.IntValue level_maxLevel;

    // ===================== 6. 全局开关配置 =====================
    public final ForgeConfigSpec.ConfigValue<Boolean> global_hudEnabled;
    public final ForgeConfigSpec.ConfigValue<Boolean> global_levelEnabled;

    // 构造方法：初始化所有配置项（带默认值+范围限制）
    private BetterEvEConfig(Builder builder) {
        // ---------- 1. 渲染系统配置 ----------
        builder.push("render");
        render_showHealthPercent = builder
                .comment("是否显示实体百分比血量（true=显示，false=隐藏）")
                .define("showHealthPercent", true);
        render_healthTextOffsetY = builder
                .comment("血量文本相对名称标签的Y轴偏移（正数向上，负数向下）")
                .defineInRange("healthTextOffsetY", 0.0D, -5.0D, 5.0D);
        render_showHealthBar = builder
                .comment("是否显示血量条（true=显示，false=隐藏）")
                .define("showHealthBar", true);
        render_healthBarWidth = builder
                .comment("血量条宽度（像素）")
                .defineInRange("healthBarWidth", 80.0D, 20.0D, 200.0D);
        render_healthBarHeight = builder
                .comment("血量条高度（像素）")
                .defineInRange("healthBarHeight", 6.0D, 2.0D, 20.0D);
        render_healthBarOffsetY = builder
                .comment("血量条相对实体顶部的Y轴偏移（正数向上，负数向下）")
                .defineInRange("healthBarOffsetY", 0.55D, -2.0D, 2.0D);
        builder.pop();

        // ---------- 2. 寻敌系统配置 ----------
        builder.push("targeting");
        targeting_defaultFollowRange = builder
                .comment("全局默认寻敌范围（格），覆盖原版生物默认值")
                .defineInRange("defaultFollowRange", 64.0D, 1.0D, 128.0D); // 范围1~128格
        targeting_entityFollowRange = builder
                .comment("按实体类型自定义寻敌范围（格式：实体ID=距离，多个用;分隔，例：minecraft:zombie=80;minecraft:skeleton=70）")
                .define("entityFollowRange", "minecraft:zombie=64;minecraft:skeleton=64");
        targeting_factionFollowRange = builder
                .comment("按阵营自定义寻敌范围（格式：阵营ID=距离，多个用;分隔，例：red=70;blue=70）")
                .define("factionFollowRange", "red=64;blue=64");
        builder.pop();

        // ---------- 3. 计数器系统配置 ----------
        builder.push("counter");
        counter_hudEnabled = builder
                .comment("是否启用计数器HUD显示（true=启用，false=关闭）")
                .define("hudEnabled", true);
        counter_hudPosX = builder
                .comment("HUD位置X轴（0=最左，1=最右，相对屏幕）")
                .defineInRange("hudPosX", 0.0D, 0.0D, 1.0D);
        counter_hudPosY = builder
                .comment("HUD位置Y轴（0=最上，1=最下，相对屏幕）")
                .defineInRange("hudPosY", 0.0D, 0.0D, 1.0D);
        counter_tabPanelEnabled = builder
                .comment("是否启用TAB面板统计（true=启用，false=关闭）")
                .define("tabPanelEnabled", false);
        counter_tabPanelKey = builder
                .comment("TAB面板切换按键（支持MC按键名，例：KEY_TAB）")
                .define("tabPanelKey", "KEY_TAB");
        counter_countEntityType = builder
                .comment("是否统计实体类型明细（true=显示每种生物数量，false=仅总数量）")
                .define("countEntityType", true);
        builder.pop();

        // ---------- 4. 基地系统配置 ----------
        builder.push("base");
        base_defaultHealth = builder
                .comment("基地默认血量")
                .defineInRange("defaultHealth", 10000, 100, 100000); // 范围100~10万
        base_victoryAnnounceTime = builder
                .comment("胜利播报时长（秒）")
                .defineInRange("victoryAnnounceTime", 5, 1, 30); // 1~30秒
        base_clearRadius = builder
                .comment("基地清空地图范围（以基地为中心的半径，格）")
                .defineInRange("clearRadius", 50, 10, 200); // 10~200格
        base_onlyHostileAttack = builder
                .comment("是否仅敌对阵营可攻击基地（true=仅敌对能打，false=所有人能打）")
                .define("onlyHostileAttack", true);
        builder.pop();

        // ---------- 5. 等级系统配置 ----------
        builder.push("level");
        level_healthBonus = builder
                .comment("每级生命值加成比例（%）默认10%")
                .defineInRange("healthBonus", 10.0D, 0.0D, 100.0D); // 0~100%
        level_attackBonus = builder
                .comment("每级攻击力加成比例（%）默认10%")
                .defineInRange("attackBonus", 10.0D, 0.0D, 100.0D);
        level_maxLevel = builder
                .comment("等级上限")
                .defineInRange("maxLevel", 500, 1, 1000); // 1~1000级
        builder.pop();

        // ---------- 6. 全局开关配置 ----------
        builder.push("global");
        global_hudEnabled = builder
                .comment("全局HUD显示总开关（覆盖counter.hudEnabled）")
                .define("hudEnabled", true);
        global_levelEnabled = builder
                .comment("全局等级系统总开关（覆盖level所有配置）")
                .define("levelEnabled", true);
        builder.pop();
    }

    // 静态初始化：创建配置规范和实例
    static {
        Pair<BetterEvEConfig, ForgeConfigSpec> pair = new Builder().configure(BetterEvEConfig::new);
        INSTANCE = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }
}