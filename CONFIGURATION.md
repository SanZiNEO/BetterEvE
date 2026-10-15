# BetterEvE 配置说明

## 简介

BetterEvE 的全部配置已迁移到**剧本系统**：一个剧本 JSON 文件定义一个完整的游戏模式。旧的全局配置文件（`config/bettereve-common.toml`）与配置 GUI 已移除，模组完全由剧本驱动。

## 剧本位置

| 用途 | 路径 | 说明 |
|---|---|---|
| 预设剧本 | `scenarios/*.json`（项目根目录） | 随仓库分发，只读种子；**外部工具不要写这里** |
| 运行时剧本 | `config/bettereve/scenarios/*.json` | 工作副本：游戏加载、外部编辑器（douququ 面板）都读/写这里 |

同步规则（`JsonScenarioStorage.syncPresetToRuntime`）：

- 运行时副本**缺失**（首次加载该剧本）→ 从预设复制一份
- 预设文件**比运行时副本更新**（mtime 更晚）→ 覆盖运行时副本（仓库里改了预设，重新加载即生效）
- 其余情况**一律以运行时副本为准** —— 直接写运行时目录的改动（面板调参）不会被预设覆盖

## schema 稳定性约定（外部编辑器协作）

外部工具（douququ 面板）直接读写运行时剧本 JSON，约定如下：

- **加字段**：随时可以，两边都不用改。游戏用默认 Gson 解析，**未知键一律忽略**（面板自己的 `douququ` 块靠这条活着）；面板存原始 JSON，不认识的键原样保留。
  - 反例警告：不要开 `FAIL_ON_UNKNOWN_PROPERTIES`、不要换 Jackson（默认严格模式）—— 会让带外部键的剧本加载直接失败。
- **删字段 / 改语义**：先在本文件标「已废弃但仍解析」并保留代码解析（参照 `waypoints[].lane` / `order`），不要直接删。
  直接删会让面板对应表单**静默失配**（表单还在，写了不生效）。
- **写目录**：面板只写运行时目录，不写预设目录。
- **回写**：游戏目前不回写剧本文件（`ScenarioIO.writeScenario` 无调用点）；将来若加回写路径，必须基于原始 JSON 做合并，不能 Gson 序列化整份重写，否则面板的未知键（如 `douququ`）会丢。

## 剧本结构

```json
{
  "id": "default",
  "name": "默认剧本",
  "description": "红蓝两队标准对战",
  "keepAliveZone": { "x1": -60, "z1": -20, "x2": 60, "z2": 20 },
  "levelTables": { ... },
  "levelTableOverrides": { ... },
  "factions": [ ... ],
  "relations": [ ... ],
  "waypoints": [ ... ],
  "routes": [ ... ],
  "crystals": [ ... ],
  "phases": [ ... ],
  "options": { ... }
}
```

### factions — 阵营

- `id` / `name` / `color`（`#RRGGBB`）/ `showOnScoreboard`（是否显示在计分板）
- `levelTable` — 引用顶层 `levelTables` 里的表名（**不写 = 该阵营没有等级系统**）

### levelTables — 命名等级表

顶层字典：表名 → 等级参数。阵营、召唤物、投掷物、实体类型覆盖都按名字引用同一批表。

- `maxLevel` 等级上限（默认 500）
- `healthBonus` / `attackBonus` / `armorBonus` / `knockbackBonus` — 每级加成
- `healthExponent` / `attackExponent` / `armorExponent` / `knockbackExponent` — 成长指数
- `maxDamage` — 投掷物伤害倍率上限（默认 200）：箭伤 = 箭速 × 箭基础伤害 × 倍率，倍率超过此值即截断

```json
"levelTables": {
  "standard": {
    "maxLevel": 1000,
    "healthBonus": 10.0, "healthExponent": 2.0,
    "attackBonus": 8.0, "attackExponent": 2.0,
    "armorBonus": 2.0, "armorExponent": 0.7,
    "knockbackBonus": 1.2, "knockbackExponent": 1.0
  }
}
```

### levelTableOverrides — 按实体类型覆盖等级表

- 键是实体类型 `modid:name`（如 `minecraft:zombie`），值是要用的表名
- 优先级最高，可以给单只 boss 单独一套成长曲线

```json
"levelTableOverrides": { "minecraft:zombie": "weak" }
```

**等级表解析优先级**（`EntityLevelSystem.getEntityLevelConfig`，越具体越优先）：

1. `levelTableOverrides[实体类型]`
2. `options.projectile.levelTable`（投掷物）
3. `options.summon.levelTable`（召唤物）
4. `factions[].levelTable`（实体所属阵营）

某一层写了名字、但 `levelTables` 里没有这张表 → 记一次 warn 日志并回退到下一优先级；
全部落空 = 该实体没有等级系统（不存等级、不加成、不同步）。

### relations — 阵营关系

- `from` / `to`（支持通配符 `*`）/ `type`（`hostile` / `neutral`）
- 判定顺序（`FactionRelationManager.isHostile`）：

  1. 双方都无阵营 → 不敌对（同类不互攻）
  2. **仅一方无阵营 → 敌对**（无阵营生物是「混乱生物」，见谁打谁，包括基地）
  3. 同阵营 → 不敌对
  4. 精确匹配 `from→to`，再反向 `to→from` → 按定义
  5. 通配符 `*→from`，再 `*→to` → 按定义
  6. **未定义 → 不敌对**（不是敌对）

### waypoints — 兵线路径点区域

- `id` / `factionId` / `x1,z1,x2,z2`（XZ 矩形柱体，无限高）
- 只定义区域，**行走顺序由 `routes[]` 决定**
- ⚠️ `lane` / `order` 是旧版遗留字段：代码仍会解析，但已无任何逻辑读取。请改用 `routes[]`

### routes — 路线

- `id` / `waypoints[]`（引用 waypoint 的 id 列表）
- 所有阵营共用同一条 route
- 行走规则：本阵营 waypoint 按数组顺序走，非本阵营 waypoint 按数组**逆序**接在后面
- 生物只绑定一条 route，走完即终点，不走回头路、不串其他 route

**route 绑定优先级**（`PathfindToWaypointGoal.resolveRoute`）：

1. 已有的运行时进度
2. 生物 NBT `bettereve:route_id`
3. 就近选路 —— 比较生成位置到各 route「本阵营第一个 waypoint」的距离

用 `/bettereve spawn ... <routeId>` 可显式指定。

### crystals — 水晶基地

- `id` / `factionId` / 位置 `x,y,z`
- `health` 血量、`attackDamage` 攻击力、`armor` 护甲
- `attackRange` / `attackCooldown` — 攻击范围与冷却
- `sizeKey` / `modelScale` — 尺寸与模型缩放、`showHealthBar` 是否显示血条

### phases — 游戏阶段

- `id` / `description`
- `announce` — 播报（`message` / `duration`）⚠️ 未接线：只会 post 成事件、全项目无订阅者，见文末「已知未接线」
- `commands[]` — 指令（`delay` 延迟秒 / `command`，多指令用 `&&` 分隔，OP 权限执行）
- `triggers[]` — 阶段切换触发器（见下）
- `spawnBases` — 生成水晶（`templateId` + `positions[]`）
- `resetAfter` — 延迟后重置 ⚠️ 未接线（字段被解析，无代码读取）
- ⚠️ `phaseType` 字段会被解析，但**没有任何代码读取**（写了无效）

剧本加载后自动进入 `phases[0]`。切换阶段会清空上一阶段尚未执行的延迟指令。

#### triggers — 触发器

所有触发器通用字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | ✅ | 条件类型（见下表） |
| `targetPhase` | ✅ | 命中后切到的阶段 id；缺失则该触发器不生效 |
| `timeout` | — | 秒；进入本阶段后满 N 秒该触发器仍未触发 → 也切到 `targetPhase`，**每个触发器各自计时** |
| `delay` | — | 秒；**命中后延迟 N 秒再切换**（留给播报 / 死亡动画 / 运镜收尾）；延迟期间本阶段的 timer / timeout / 其他条件**全部冻结**，阶段被手动切走则取消 |
| `winner` | — | 切阶段日志里的胜者（`red` / `blue` / `none`）；不写由模组推断（只有 `faction_wiped` 能自动算，其余记 `none`） |
| `intervalTicks` | — | 轮询类条件的采样间隔（tick）；不写按类型默认（faction 5 / 其余 10 / command 20） |

条件类型总表：

| `type` | 语义 | 关键字段 | 判定 |
|---|---|---|---|
| `timer` | 进入本阶段后 N 秒 | `threshold` = 秒 | 计时 |
| `crystal_destroyed` | 指定水晶被摧毁 | `targetId` = 水晶模板 id | 事件 |
| `faction_wiped` | 阵营在场数从 >N 降到 ≤N（倒地/死亡动画中的生物仍算在场） | `factionId`、`threshold`（默认 0 = 全灭） | 轮询 5 tick |
| `entity_death` | 指定生物死亡（累计 N 只） | `entityType` / `name` / `nbt` / `factionId` / `count`（默认 1） | 事件 |
| `player_death` | 指定玩家死亡 | `player` = 玩家名（不写 = 任意玩家）、`count` | 事件 |
| `entity_state` | 指定生物进入某 NBT 状态 | `entityType` / `name` / `factionId` / `nbt`（**必填**） | 轮询 10 tick |
| `entity_health` | 指定生物/水晶血量低于门槛 | 目标过滤 + `ratio`（0~1，按最大生命）或 `below`（绝对值）；水晶用 `targetId` | 轮询 10 tick |
| `zone_enter` | 匹配生物进入区域（区域内 0 → 有） | `zone`（x1/z1/x2/z2）或 `waypointId` + 目标过滤 | 轮询 10 tick |
| `zone_leave` | 匹配生物全部离开区域（有 → 0） | 同上 | 轮询 10 tick |
| `score` | 阵营累计比分达到门槛 | `factionId`、`kind`（`kills` / `crystals`，默认 kills）、`threshold` | 轮询 10 tick |
| `command_check` | 只读指令返回值 ≥1 | `command`（`execute if/unless …`） | 轮询 20 tick |
| `all_of` | 全部子条件都满足 | `triggers[]`（子条件**不写** `targetPhase`） | 递归 |
| `any_of` | 任一子条件满足 | 同上 | 递归 |
| `entity_death_count` | ⚠️ 已废弃（旧名）：等价 `entity_death` + `factionId` + `count` | `factionId`、`threshold`/`count` | 事件 |

目标过滤（`entity_death` / `entity_state` / `entity_health` / `zone_*` / `player_death` 共用）：

| 字段 | 说明 |
|---|---|
| `entityType` | `modid:name` 或只写实体名（`zombie`）；不写 = 任意实体 |
| `name` | 自定义名（比较前去掉颜色代码）；不写 = 任意名字 |
| `factionId` | 只匹配该阵营；不写 = 任意阵营 |
| `nbt` | NBT 探针：实体序列化 NBT 逐键比对（布尔比对布尔键，其余比对字符串键） |
| `player` | 仅 `player_death`：玩家名 |

示例：

```json
"triggers": [
  { "type": "crystal_destroyed", "targetId": "red_base", "targetPhase": "blue_victory" },
  { "type": "timer", "threshold": 12, "targetPhase": "match_start" },
  { "type": "faction_wiped", "factionId": "blue", "timeout": 240, "targetPhase": "match_2" },
  { "type": "entity_death", "entityType": "jujutsucraft:sukuna_fushiguro",
    "nbt": { "Dataperfect_mode": true }, "winner": "red", "targetPhase": "match_2" },
  { "type": "entity_health", "entityType": "minecraft:ender_dragon", "ratio": 0.3, "targetPhase": "phase_2" },
  { "type": "zone_leave", "factionId": "red",
    "zone": { "x1": -101, "z1": -101, "x2": 101, "z2": 101 }, "winner": "blue", "targetPhase": "out_of_bounds" },
  { "type": "score", "factionId": "red", "kind": "kills", "threshold": 3, "targetPhase": "done" },
  { "type": "all_of",
    "triggers": [ { "type": "faction_wiped", "factionId": "red" },
                  { "type": "faction_wiped", "factionId": "blue" } ],
    "targetPhase": "double_ko" }
]
```

通用约定：

- 触发器按数组顺序检查，**首个命中即切换并跳出**，后面的不再检查
- 同一阶段重复切换到当前阶段会被幂等忽略
- 进阶段重置全部触发器状态：计数器清零、锁定复位、采样基线重记
- 事件类条件（`crystal_destroyed` / `entity_death` / `player_death`）**到达即判**；轮询类条件按各自间隔采样，未到期复用上次结果
- `delay` 语义：条件命中后先"预定切换"（日志打一行 `[触发器] <type> 命中，N 秒后切换到 <phase>`），到点才真正切换；
  延迟期间本阶段的 **timer / timeout / 其他条件全部冻结**（首个命中已"预定"这次切换），阶段被手动切走或重载剧本则取消预定；
  切换日志的 `elapsed` 会把延迟算进去
- 除 `entity_health` 外，命中过的条件在本阶段内**锁定**（不因状态回退而复位）—— 组合条件（`all_of` / `any_of`）靠这条工作
- **水晶不算"生物"**：`faction_wiped` 只数生物（含玩家与召唤物）；水晶相关条件用 `crystal_destroyed` 或 `entity_health` + `targetId`
- 未识别的 `type` 静默忽略（老版本模组读新剧本不会炸，只是不触发）
- ⚠️ **一个阶段只应写一个 `timer` 触发器**：`timerTicks` 是阶段级单一计数器，且自增写在触发器循环体内 —— 写 N 个 timer 会让计数每 tick 加 N，触发时间变成 1/N

`faction_wiped` 语义（多场次剧本用，见 `plans/complete/external/03_MULTI_MATCH_SCENARIO.md`）：

- 统计**在场实体数**（只看已加载的实体）：**玩家按存活算**；**其它生物只要还在世界里（没被移除）就算在场** —— 正在播死亡动画的尸体、倒地等待复活的生物（如突变怪物的僵尸突变体）都算，死亡流程真正走完才从计数里消失（普通生物约 1 秒后）；**不含水晶**，不是死亡计数
- **本阶段召唤过才算**：阵营必须在本阶段内出现过（0 → 有）才"武装"；上一场清场后的空阵营不会立刻触发，要等本阶段的召唤落地
- `threshold` 不写 = 0：在场数**从 >0 降到 0** 那一刻命中；写了 N 就是**从 >N 降到 ≤N** 那一刻命中
- 命中那一刻打结构化日志并算胜者（见下）

`score` 的比分来源（服务端累计，与 HUD 记分板同规则）：

- `kills`：击杀数（召唤物不计分、同阵营不计分）
- `crystals`：摧毁水晶数
- 复位时机：加载剧本时清零；`/bettereve scoreboard clear [阵营]` 也会清零

`command_check` 使用约束：

- 只写**只读**判定指令（`execute if …` / `execute unless …` 这类），返回值 ≥1 视为命中
- 默认每 20 tick 跑一次（可用 `intervalTicks` 调）；不要写会改变世界的指令

#### 阶段切换日志（外部数据台用）

每次阶段切换打一行固定格式日志（INFO）：

```
[bettereve] phase match_1 -> match_2 winner=red reason=wiped elapsed=87s
[bettereve] phase match_3 -> match_4 winner=none reason=timeout elapsed=240s
```

- `winner`：`red` / `blue` / `none`。只有 `faction_wiped` 能算出具体阵营（恰好一个阵营还有在场实体 → 它；双灭 / 多阵营并存 / 无在场 → `none`）；超时未分胜负记 `none`
- `reason`：`wiped`（`faction_wiped`）/ `timeout` / `timer` / `manual`（`/bettereve phase switch`）/ `load`（剧本首次进入阶段）/ 其余触发器类型**原样记**（`crystal_destroyed`、`entity_death`、`player_death`、`entity_state`、`entity_health`、`zone_enter`、`zone_leave`、`score`、`command_check`）
- `elapsed`：停留在上一阶段的整数秒；首次加载记 `- -> <阶段>`，0s
- 格式固定（空格分隔、键名固定、一行），外部工具按 `phase (\S+) -> (\S+) winner=(\w+) reason=(\w+) elapsed=(\d+)s` 抓取

未列出的 `type`（`base_destroyed` / `custom`）写了无效，见文末「已知未接线」。

### options — 全局选项

| 字段 | 默认 | 说明 |
|---|---|---|
| `playersDefaultFaction` | 无 | 新玩家默认阵营（不写 = 未分配） |
| `targetingRange` | 64.0 | 常态巡敌距离（格） |
| `targetingFocusRange` | 10.0 | 近距离重扫距离（格）：范围内出现非基地敌对目标即切换 |
| `targetingFocusInterval` | 20 | 近距离重扫间隔（tick） |
| `hudEnabled` | true | HUD 总开关 |
| `overheadHealthBar` | true | 实体头顶血条总开关 |
| `healthBarScale` | 1.0 | 头顶血条缩放倍率（0.1~3.0） |
| `healthBarLevelText` | true | 头顶血条显示 `Lv` 文字（生物 NBT 优先） |
| `healthBarHealthText` | true | 头顶血条显示血量数字（生物 NBT 优先） |
| `versusHealthBar` | false | 格斗血条（顶部阵营血条）总开关 |
| `versusHealthBarSubCount` | 2 | 每个阵营最多显示几条次要血条 |
| `versusHealthBarStyle` | `plain` | 格斗血条填充样式：`plain` / `stripe` / `grid` |
| `versusHealthBarHealthText` | true | 主血条名字旁是否显示「当前/上限」血量 |
| `summon` | 见下 | 召唤物机制 |
| `projectile` | 见下 | 投掷物机制 |
| `spawnLayout` | 见下 | 指令召唤阵型 |
| `sunBurn` | 见下 | 日光燃烧开关（防骷髅 / 僵尸 / 幻翼被日光点燃） |

#### options.summon — 召唤物机制

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关 |
| `inheritFaction` | true | 召唤物是否继承宿主阵营 |
| `inheritLevel` | true | 召唤物是否继承宿主等级（与阵营继承独立） |
| `hideHealthBar` | false | 召唤物是否隐藏血条（默认 false = 显示，便于观察继承效果） |
| `countsForScore` | false | 召唤物击杀是否计分 |
| `healthBarOverrides` | 无 | 按实体类型覆盖血条，优先级最高 |
| `factionOverrides` | 无 | 按实体类型强制阵营，优先级最高（先于一切继承逻辑）；治模组内部清空归属的实体（如茈） |
| `levelTable` | 无 | 召唤物专用等级表名（不写 = 走阵营表） |

```json
"summon": {
  "inheritFaction": true,
  "inheritLevel": true,
  "countsForScore": false,
  "healthBarOverrides": { "jujutsucraft:projectile_slash": false },
  "factionOverrides": { "jujutsucraft:purple": "gojo" }
}
```

#### options.projectile — 投掷物机制

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关：关掉后阵营与等级都不继承 |
| `inheritFaction` | true | 投掷物是否继承宿主阵营 |
| `inheritLevel` | true | 投掷物是否继承宿主等级（与阵营继承独立） |
| `levelTable` | 无 | 投掷物专用等级表名（不写 = 走阵营表） |

```json
"projectile": {
  "inheritFaction": true,
  "inheritLevel": false
}
```

`inheritLevel: false` 时箭仍带阵营色（阵营继承照常），只是不再吃等级加成。

**谁算"投掷物"**：`Projectile` 家族（原版箭 / 火球 / 雪球 + 模组 `AbstractArrow` 之类）一律算；
此外名字像投掷物的实体（关键词表见 `ProjectileRule.isProjectileLike`，如 `bullet_*` / `*_slash` /
`needle` / `spear` 等）也按投掷物对待。**生物形态的**（`jujutsucraft:projectile_slash` 这类
"名字像投掷物、实际是 Mob"）由召唤物继承链负责归属与血条（见 `options.summon`），
但**等级表仍按投掷物优先**：等级表解析顺序里 `options.projectile.levelTable` 排在
`options.summon.levelTable` 前面。

**归属判定时机**：加入世界（`EntityJoinLevelEvent`）判一次，判不出来就挂起，
**服务器 tick 末再判**，最多重判 3 轮（实测咒术回战在 `addFreshEntity` 之后才写 `OWNER_UUID`；
重判轮数有上限，因为个别模组会在命中时改写归属）。三轮都判不出来 → 投掷物保持无阵营
（无阵营 = 混乱生物，见 `relations` 的说明），需要时用 `options.summon.factionOverrides` 直接钉阵营。

**不会覆盖已有阵营**：投掷物带阵营（剧本 `factionOverrides` / 指令指定）时继承逻辑整个跳过 ——
所以剧本覆盖永远优先于继承。

**存档加载的投掷物不重新继承**：区块 / 存档重载时跳过，避免箭伤倍率与穿透等级被重复叠乘。
等级本身也只对"还没有等级"的投掷物施加一次。

**什么情况拦、什么情况不拦（一句话口径）**：攻击实体（投掷物 + 投掷物语义实体）与目标
**不敌对**（同阵营、或剧本没定义敌对关系）时拦 —— `Projectile` 家族在命中判定层被整根忽略
（穿过去，不受伤、不挡箭、不反弹），非 `Projectile` 的攻击实体（Mob 形态）在伤害层取消伤害；
判据统一用 `FactionHelper.isEntitiesHostile`，投掷物自身还没继承到阵营时按发射者的阵营算；
投掷物自身也无阵营可判时不拦（保持原版行为）。

#### options.spawnLayout — 指令召唤阵型

| 字段 | 默认 | 说明 |
|---|---|---|
| `formation` | `circle` | `circle` / `square` / `diamond` / `rectangle` / `triangle` / `line_x` / `line_z` / `grid` / `point` |
| `radius` | 2.0 | 半径（圆 / 方 / 菱 / 三角） |
| `spacing` | 2.0 | 间距（直线 / 网格） |
| `arc` | 360.0 | 张角（度）：360 整圆、180 半圆、≤0 退化为直线 |
| `width` | `radius × 2` | 矩形宽（X 方向） |
| `depth` | `radius × 2` | 矩形深（Z 方向） |

#### options.sunBurn — 日光燃烧开关

防骷髅 / 僵尸 / 幻翼被**日光点燃**（不碰岩浆、火焰、闪电）。**没写这一段 = 不烧**（防护默认开着）——
**没加载剧本时也不烧**；所以老剧本、预设剧本、新导出的剧本都不用改。想让某个剧本照原版烧，写 `enabled: true`。

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | **`false`** | 不写 = 谁都不被日光点燃（防护默认开着）；写 `true` 才走原版（会烧） |
| `except` | 无 | 例外名单：这些实体**照旧**被日光点燃。写实体 id；末尾带 `*` 表示按前缀匹配（如 `somemod:*`） |

```json
"options": {
  "sunBurn": {
    "enabled": false,
    "except": ["minecraft:zombie"]
  }
}
```

实现：`mixin/MobSunBurnMixin` 注入 `Mob.isSunBurnTick()` 的 HEAD，命中就返回 false ——
原版只有 `AbstractSkeleton` / `Zombie` / `Phantom` 的 `aiStep()` 会调它（`AbstractSkeleton:95` / `Zombie:214` / `Phantom:130`），
所以**一处覆盖全部三类**；而且连带跳过原版那套「算光照 + 取 BlockPos + canSeeSky + 摇随机数」的判定，**比原版更省**。
已经在烧的生物最多 8 秒自己烧完（不做事后全场扫描）。

#### options.rules — 剧本裁决规则

把「剧本特例」从代码里抽出来：代码只提供规则种类与执行器（`ScenarioRuleHandler`），具体针对哪个实体、什么状态、杀几次全部写剧本。没写 `rules` 段时全部零开销。

| 子段 | 字段 | 说明 |
|---|---|---|
| `lethalHits[]` | `attacker` / `victim` | 实体类型（`modid:name` 或只写实体名）：attacker 打到 victim 时把这一击改写成致死量 |
| | `attackerFlag` | 可选：攻击者 persistentData 里必须为 `true` 的布尔键（区分同一实体的不同形态/招式） |
| | `damage` | 致死伤害量，默认 `1000000` |
| `stateKills[]` | `type` | 实体类型 |
| | `nbt` | 状态探针：实体序列化 NBT 里必须匹配的键值（布尔比对布尔键，其余比对字符串键） |
| | `kills` | 每次触发的连杀次数，默认 3 |
| | `intervalTicks` | 轮询间隔（tick），默认 10 |
| `attributeOverrides[]` | `type` | 实体类型 |
| | `healthMultiplier` / `armorMultiplier` / `attackMultiplier` | 属性倍率；只在实体**新加入世界**时应用一次（存档加载的实体跳过），未写的项不动 |

三个子段都是「第一条命中即生效」；`lethalHits` 依次按 攻击者类型 → 受害者类型 → 攻击者标记 过滤。

```json
"rules": {
  "lethalHits": [
    { "attacker": "jujutsucraft:purple", "attackerFlag": "explode",
      "victim": "jujutsucraft:sukuna_fushiguro", "damage": 1000000.0 }
  ],
  "stateKills": [
    { "type": "jujutsucraft:sukuna_fushiguro", "nbt": { "Dataperfect_mode": true },
      "kills": 3, "intervalTicks": 5 }
  ],
  "attributeOverrides": [
    { "type": "jujutsucraft:eight_handled_sword_divergent_sila_divine_general_mahoraga",
      "healthMultiplier": 4.0 }
  ]
}
```

### keepAliveZone — 保活区域

- `x1,z1,x2,z2` 矩形区域，区域内生物禁止自然 despawn（直播地图无自然生成时使用）
- 区域内 chunk 常驻（运行时 ticket，不写盘）
- 单生物防清除（不配场地也能用）：`/bettereve spawn ... <count> true`，只作用于该生物
  - 实现：`setPersistenceRequired()`（原版机制）+ `BetterEvE:KeepAlive` 标记（`AllowDespawn` 里再拦一道）
  - 与区域制互补：本标记只保证"不被清除"；要让远处的生物继续 tick 仍需 `keepAliveZone` 的 chunk 常驻

## 生物 NBT 键

生成生物时可通过 NBT 参数设置：

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `BetterEvE:ShowHealthBar` | 布尔 | true | 该实体是否显示血条 |
| `BetterEvE:ShowLevelText` | 布尔 | true | 是否显示 `Lv.x` 文字 |
| `BetterEvE:ShowHealthText` | 布尔 | true | 是否显示血量数字 |
| `bettereve:route_id` | 字符串 | 无 | 绑定的路线 id（卸载重生后仍走原路线） |
| `BetterEvE:KeepAlive` | 布尔 | false | 单生物防清除标记：带此标记的生物不被原版 despawn 清除（`spawn ... true` 会写入） |

示例：`{BetterEvE:ShowLevelText:0b, BetterEvE:ShowHealthText:1b}`

## 管理指令

### 阵营

| 指令 | 权限 | 说明 |
|---|---|---|
| `/bettereve faction list` | — | 列出全部阵营 |
| `/bettereve faction create <id> <name> <color>` | — | 创建阵营 |
| `/bettereve faction delete <id>` | — | 删除阵营 |
| `/bettereve faction join <id>` | — | 自己加入阵营 |
| `/bettereve faction my` | — | 查看自己的阵营 |
| `/bettereve faction clear` | — | 清除自己的阵营 |
| `/bettereve faction setplayer <player> <id>` | OP 2 | 指定某玩家的阵营 |
| `/bettereve faction clear <player>` | OP 2 | 清除某玩家的阵营 |

### 剧本与阶段

| 指令 | 说明 |
|---|---|
| `/bettereve scenario list` | 列出可用剧本 |
| `/bettereve scenario load <name>` | 加载剧本 |
| `/bettereve phase list` | 列出阶段 + 当前阶段 |
| `/bettereve phase switch <id>` | 手动切换阶段 |

### 地图与生成

| 指令 | 说明 |
|---|---|
| `/bettereve waypoint visualize` | 切换路径点粒子柱可视化 |
| `/bettereve crystal create <factionId> <x> <y> <z> <health> <attackDamage> <armor> <attackRange> <attackCycle> <size> <modelScale> [showHealthBar] [crystalId]` | 现场创建水晶 |
| `/bettereve crystal spawn <crystalId>` | 按剧本模板生成水晶 |
| `/bettereve spawn <entityType> <factionId> <level> <x> <y> <z> <count> [keepAlive] [routeId] [showHealthBar] [nbt]` | 按阵型批量生成生物；坐标与原生 `/summon` 一致，支持 `~`（相对执行位置）与 `^`（局部坐标）；`keepAlive: true` = 该生物不被原版 despawn 清除（不依赖 `keepAliveZone`）；`level` 0~100000（实际会被剧本等级表的 `maxLevel` 夹住） |

### 计分与血条

| 指令 | 说明 |
|---|---|
| `/bettereve scoreboard clear` | 清空全部计分板 |
| `/bettereve scoreboard clear crystal` | 清空水晶计分 |
| `/bettereve scoreboard clear <factionName>` | 清空某阵营计分 |
| `/bettereve healthbar reset` | 重置头顶血条缩放 |
| `/bettereve healthbar get` | 查看当前缩放 |

## 已知未接线

以下内容在代码里存在，但**当前版本不会生效**，写进剧本不会有任何反应：

| 位置 | 内容 | 原因 |
|---|---|---|
| `phases[].phaseType` | `point` / `loop` / `branch` | 字段被解析，无代码读取 |
| `triggers[].type` | `base_destroyed` | 旧名残留（`crystal_destroyed` 的前身），无调用点 |
| `triggers[].type` | `custom` | 从未接线 |
| `waypoints[].lane` / `waypoints[].order` | — | 旧版路线字段，被 `routes[]` 取代 |
| `phases[].announce` | `message` / `duration` | 会 post 成 `PhaseBroadcastEvent`，但全项目无订阅者；播报请写进 `commands`（`title` / `say`） |
| `phases[].resetAfter` | — | 字段被解析，无代码读取 |
| `/bettereve scenario info` | — | 处理函数 `executeScenarioInfo` 存在，但未注册到指令树 |

## 旧全局配置去向对照

| 旧配置项 | 新位置 |
|---|---|
| `targeting_defaultFollowRange` 寻敌范围 | 剧本 `options.targetingRange`（更细：常态 + 近距离重扫两档） |
| `crystal_defaultHealth` 水晶血量 | 剧本 `crystals[].health`（每水晶独立） |
| 等级参数（healthBonus / attackBonus / maxLevel） | 剧本 `levelTables`（命名表）+ `factions[].levelTable`（阵营引用） |
| `global_hudEnabled` HUD 开关 | 剧本 `options.hudEnabled` |
| 渲染配置（血条显示等） | 剧本 `options.overheadHealthBar` / `healthBarScale` / `healthBarLevelText` / `healthBarHealthText` / `versusHealthBar*` |
| 召唤物机制配置 | 剧本 `options.summon` |
| 指令召唤阵型配置 | 剧本 `options.spawnLayout` |
| 日光燃烧（原由 `protect-mobs-from-daylight` 模组负责） | 剧本 `options.sunBurn`（那个模组可以卸了） |
| `crystal_clearRadius` / `counter_countEntityType` / `global_levelEnabled` | 已移除（无代码读取） |
