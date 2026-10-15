# BetterEvE — 更好的阵营战争 Mod

BetterEvE 是一个 Minecraft Forge 1.20.1 阵营 PvP/PvE Mod，专注于构建可配置的阵营对战体验。所有游戏模式由 JSON 配置文件（剧本）驱动，支持自定义阵营、关系、兵线、水晶基地、阶段和触发器。

## 核心特性

### 阵营系统
- 任意数量的阵营，每个阵营有独立 ID、名称和颜色
- 阵营关系可配置（敌对/中立），支持通配符 `*`
- 玩家可加入阵营，阵营数据持久化（跨世界/跨重启）
- 头顶阵营颜色标记

### 全生物参与战斗
- 所有生物（包括被动生物如鸡、牛）都参与阵营战斗
- 有攻击属性（ATTACK_DAMAGE）的生物获得近战攻击 AI（鸡、牛等无该属性的被动生物不参与近战，防崩溃）
- 自定义目标选择器替代原版 AI，只攻击敌对阵营

### 自定义计分板
- 屏幕顶部 HUD 显示每个阵营的积分（水晶摧毁数/实体击杀数）
- 替代原版 MC Scoreboard（解决排序问题和残留 ID）

### 剧本系统（Scenario）
- 一切由 JSON 配置文件驱动，游戏外可编辑
- 定义阵营、关系、兵线路径点、水晶模板、触发器、游戏阶段、保活区域
- 预设剧本在 `scenarios/` 目录，运行时自动加载

### 水晶基地系统
- 可配置血量、攻击力、护甲、攻击范围/冷却、模型大小的水晶实体（CrystalEntity）
- 死亡时自动播报、清场、触发阶段切换

### 阶段系统（Phase）
- 阶段驱动游戏流程：开始 → 推进 → 事件 → 结束 → 重置
- 进入阶段可播报、执行指令、生成水晶
- 触发器（Trigger）检测条件（水晶摧毁/击杀数/计时）自动切换阶段

### 兵线推进（Waypoint + Route）
- XZ 矩形柱体区域定义兵线路径点（只定义区域）
- `routes[]` 集中定义路线，所有阵营共用；生物只走绑定的一条 route
- 本阵营按 route 数组顺序、敌方按数组逆序，走完即终点
- 粒子柱可视化显示区域位置
- 阵营实体自动沿路径推进，战斗优先于赶路

### 召唤物阵营继承
- 召唤物、投掷物自动继承召唤者的阵营，无需模组适配
- 痕迹驱动：`TraceableEntity.getOwner` → `OwnableEntity` → owner UUID 反查 → NBT 召唤者痕迹（跨模组主通道）
- 零反射、不做附近实体扫描（避免混战中误判归属）
- 加入世界时判定一次，未命中则挂起到服务器 tick 末再判（部分模组在 `spawn()` 之后才写召唤者 NBT）；投掷物最多重判 3 轮
- 已有阵营绝不覆盖（剧本 `factionOverrides` 永远优先）；存档 / 区块加载的实体不重复继承（避免箭伤倍率叠乘）
- 可按剧本 `options.summon` 开关、按实体类型覆盖血条显示

### 投掷物同阵营拦截
- 一句话口径：攻击实体与目标**不敌对**（同阵营 / 剧本未定义敌对）时拦，判据统一为 `FactionHelper.isEntitiesHostile`
- `Projectile` 家族拦在命中判定层（`canHitEntity`）：友军直接穿过去，不受伤、不挡箭、不反弹、不消耗
- 非 `Projectile` 的攻击实体（`jujutsucraft:projectile_slash` 这类 Mob）拦在伤害层（`LivingAttackEvent`）：
  它们的命中判定写在模组自己代码里，vanilla 没有可挂的谓词
- 投掷物自身还没继承到阵营时，按发射者的阵营判（归属链回退）；两边都判不出阵营则不拦（保持原版行为）

### 实体等级系统
- 等级参数写在剧本顶层命名表 `levelTables`，阵营用 `factions[].levelTable` 引用，不写则该阵营无等级
- `levelTableOverrides` 可按实体类型（`modid:name`）覆盖，给单只 boss 单独一套成长曲线
- 召唤物 / 投掷物可各配一张专用表（`options.summon.levelTable` / `options.projectile.levelTable`）
- 等级影响血量 / 攻击 / 护甲 / 击退，按指数曲线成长

### 血条与 HUD
- 头顶血条：可缩放，可显示 `Lv` 文字与血量数字，支持按生物 NBT 逐个覆盖
- 格斗血条：顶部阵营血条（主血条 + 每个阵营若干条次要血条），填充样式 `plain` / `stripe` / `grid`
- 计分板 HUD：屏幕顶部显示各阵营积分

### 保活区域
- 剧本声明一个矩形区域，区域内生物禁止自然 despawn、chunk 常驻（运行时 ticket，不写盘）
- 用于直播地图无自然生成的场景

### 日光燃烧开关（options.sunBurn）
- **默认就不烧**（没写这一段也是，**没加载剧本也是**）：骷髅 / 僵尸 / 幻翼白天不着火；写 `enabled: true` 才走原版
- 可给例外名单（写实体 id，支持 `modid:*` 前缀），例如只让僵尸不烧、骷髅照烧
- 掐在源头：注入 `Mob.isSunBurnTick()` 的 HEAD —— 原版只有这三类的 `aiStep()` 会调它，一处覆盖全部
- 连带跳过原版那套「光照 + 可见天 + 随机数」判定，**比原版更省**；岩浆 / 火焰 / 闪电照旧
- 取代 `protect-mobs-from-daylight` 那类模组（并进来之后可以卸掉）

## 技术说明

- 所有反射调用已移除
- 无 `@Overwrite` Mixin（全部用 `@Inject` + Mixin Accessor；唯一一处 `@Redirect` 在 `WitherBossMixin`，用来放行凋灵 `hurt()` 里"同 MobType 免伤"那一处判定）
- Mixin 0.8.5 兼容（Forge 1.20.1 内置版本）
- 使用 Gson 处理 JSON（Forge 捆绑）
- 构建：ForgeGradle 6.x + JDK 17

## 项目结构

```
BetterEvE/
├── scenarios/          # 预设剧本 JSON 文件（游戏外编辑）
│   └── default.json    # 默认红蓝对战剧本
├── src/
│   ├── main/
│   │   ├── java/com/example/bettereve/
│   │   │   ├── ai/          # AI 目标选择器 + 寻路
│   │   │   ├── commands/    # 指令处理
│   │   │   ├── crystal/     # 水晶基地系统
│   │   │   ├── entity/      # 自定义实体
│   │   │   ├── entitylv/    # 实体等级系统
│   │   │   ├── events/      # 事件处理器
│   │   │   ├── faction/     # 阵营系统 + 关系管理
│   │   │   ├── mixin/       # Mixin 注入
│   │   │   ├── network/     # 网络数据包
│   │   │   ├── phase/       # 阶段 + 触发器系统
│   │   │   ├── render/      # 血条 + 计分板 + 播报渲染
│   │   │   ├── scenario/    # 剧本 JSON 加载
│   │   │   ├── scoreboard/  # 计分板管理
│   │   │   ├── targeting/   # 目标选择器管理
│   │   │   └── waypoint/    # 兵线路径点系统
│   │   └── resources/
│   │       └── bettereve.mixins.json
│   └── test/
├── build.gradle
├── CONFIGURATION.md    # 剧本字段与指令的完整参考（含「已知未接线」清单）
├── MOD_OVERVIEW.md     # 本文件
└── TESTING_GUIDE.md    # 逐步测试清单
```
