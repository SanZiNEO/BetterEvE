# BetterEvE

[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange.svg)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://adoptium.net/)

BetterEvE 是一个 Minecraft Forge 1.20.1 阵营战争模组。它把原版生物、玩家、召唤物和投掷物组织成可配置的阵营，在 JSON 剧本驱动的地图上进行基地攻防，适合直播、服务器活动和自定义 PvP/PvE 场景。

## 目录

- [特性](#特性)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速开始](#快速开始)
- [使用示例](#使用示例)
- [指令](#指令)
- [配置](#配置)
- [构建](#构建)
- [测试](#测试)
- [项目结构](#项目结构)
- [兼容性](#兼容性)
- [常见问题](#常见问题)
- [支持与联系](#支持与联系)
- [贡献](#贡献)
- [致谢](#致谢)
- [许可证](#许可证)

## 特性

- **阵营系统**：任意数量阵营，独立 ID、名称、颜色；关系支持敌对/中立和通配符 `*`；玩家阵营持久化。
- **全生物参战**：所有生物参与阵营战斗；有 `ATTACK_DAMAGE` 的生物获得近战 AI，被动生物不参与近战以防崩溃；自定义目标选择器只攻击敌对阵营。
- **原版 BOSS 放行**：末影龙、凋灵、监守者可在双方阵营敌对时被非玩家攻击，原版世界行为不变。
- **JSON 剧本**：阵营、关系、兵线、水晶、阶段、触发器、保活区域全部由剧本定义，游戏外可编辑。
- **水晶基地**：可配置血量、攻击、护甲、攻击范围/冷却、模型大小；摧毁后播报、清场、切换阶段。
- **阶段与触发器**：`timer`、`crystal_destroyed`、`faction_wiped`、`entity_death`、`player_death`、`entity_state`、`entity_health`、`zone_enter`、`zone_leave`、`score`、`command_check`、`all_of`、`any_of`。
- **兵线推进**：`waypoints` 定义区域，`routes` 定义顺序；本阵营顺序走、敌方逆序走，战斗优先于赶路。
- **召唤物/投掷物继承**：自动继承召唤者阵营与等级；痕迹驱动、零反射、不做附近扫描；已有阵营不覆盖。
- **等级系统**：命名 `levelTables` + `levelTableOverrides`，按血量/攻击/护甲/击退指数成长。
- **HUD**：头顶血条（缩放、等级文字、血量数字）、格斗血条（plain/stripe/grid）、阵营计分板。
- **保活区域**：区域内生物不 despawn，chunk 常驻；支持单生物 `KeepAlive` 标记。
- **日光燃烧开关**：默认不烧，可配置例外；注入 `Mob.isSunBurnTick()` 源头，比原版更省。

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x（开发环境 47.4.16） |
| Java | 17 |
| 侧 | 客户端 + 服务端 |

## 安装

1. 安装 Minecraft 1.20.1 和对应版本的 Forge。
2. 将 `bettereve-<version>.jar` 放入 `mods/`。
3. 启动游戏或服务器。
4. 将剧本 JSON 放入 `config/bettereve/scenarios/`。

> BetterEvE 不会自动创建默认剧本。首次使用请确认 `config/bettereve/scenarios/` 下有可加载的 JSON。

### 从源码构建

需要 JDK 17：

```bash
git clone https://github.com/SanZiNEO/BetterEvE.git
cd BetterEvE
./gradlew build
```

产物在 `build/libs/`。

## 快速开始

```text
/bettereve scenario list
/bettereve scenario load default
/bettereve faction join red
/bettereve phase switch match_start
```

加载 `default` 剧本后，红蓝双方各有水晶基地；生物沿兵线推进，摧毁对方水晶或清空对方阵营即可触发胜利阶段。

## 使用示例

一个最小剧本只需要阵营、关系和阶段：

```json
{
  "id": "demo",
  "name": "最小示例",
  "factions": [
    { "id": "red", "name": "红队", "color": "#FF0000" },
    { "id": "blue", "name": "蓝队", "color": "#0000FF" }
  ],
  "relations": [
    { "from": "red", "to": "blue", "type": "hostile" },
    { "from": "blue", "to": "red", "type": "hostile" }
  ],
  "phases": [
    {
      "id": "start",
      "commands": [ { "delay": 0, "command": "say 比赛开始" } ],
      "triggers": [ { "type": "timer", "threshold": 60, "targetPhase": "end" } ]
    },
    {
      "id": "end",
      "commands": [ { "delay": 0, "command": "say 比赛结束" } ]
    }
  ]
}
```

把文件保存为 `config/bettereve/scenarios/demo.json`，然后：

```text
/bettereve scenario load demo
/bettereve faction join red
/bettereve phase switch start
```

60 秒后自动进入 `end` 阶段。更完整的剧本字段见[配置](#配置)。

## 指令

### 阵营

| 指令 | 权限 | 说明 |
|---|---|---|
| `/bettereve faction list` | — | 列出全部阵营 |
| `/bettereve faction create <id> <name> <color>` | — | 创建阵营 |
| `/bettereve faction delete <id>` | — | 删除阵营 |
| `/bettereve faction join <id>` | — | 自己加入阵营 |
| `/bettereve faction my` | — | 查看自己的阵营 |
| `/bettereve faction clear` | — | 清除自己的阵营 |
| `/bettereve faction setplayer <player> <id>` | OP 2 | 指定玩家阵营 |
| `/bettereve faction clear <player>` | OP 2 | 清除玩家阵营 |

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
| `/bettereve waypoint visualize` | 切换路径点粒子柱 |
| `/bettereve crystal create <factionId> <x> <y> <z> <health> <attackDamage> <armor> <attackRange> <attackCycle> <size> <modelScale> [showHealthBar] [crystalId]` | 现场创建水晶 |
| `/bettereve crystal spawn <crystalId>` | 按剧本模板生成水晶 |
| `/bettereve spawn <entityType> <factionId> <level> <x> <y> <z> <count> [keepAlive] [routeId] [showHealthBar] [nbt]` | 按阵型批量生成生物；支持 `~` 和 `^` 坐标 |

### 计分与血条

| 指令 | 说明 |
|---|---|
| `/bettereve scoreboard clear` | 清空全部计分 |
| `/bettereve scoreboard clear crystal` | 清空水晶计分 |
| `/bettereve scoreboard clear <factionName>` | 清空某阵营计分 |
| `/bettereve healthbar reset` | 重置头顶血条缩放 |
| `/bettereve healthbar get` | 查看当前缩放 |

## 配置

BetterEvE 的全部配置由剧本系统驱动：一个 JSON 文件定义一个完整游戏模式。旧的 `config/bettereve-common.toml` 与配置 GUI 已移除。

### 剧本位置

| 用途 | 路径 | 说明 |
|---|---|---|
| 预设剧本 | `scenarios/*.json` | 随仓库分发，只读种子；外部工具不要写 |
| 运行时剧本 | `config/bettereve/scenarios/*.json` | 工作副本：游戏加载、外部编辑器读/写 |

同步规则：

- 运行时副本缺失 → 从预设复制
- 预设比运行时副本新（mtime 更晚）→ 覆盖运行时副本
- 其余情况以运行时副本为准

### schema 稳定性

- **加字段**：随时可以；游戏用默认 Gson 解析，未知键忽略
- **删字段/改语义**：先保留代码解析并标注废弃，不要直接删
- **写目录**：外部工具只写运行时目录
- **回写**：游戏目前不回写剧本；将来若加，必须基于原始 JSON 合并

<details>
<summary><strong>展开完整剧本字段参考</strong></summary>

### 剧本结构

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

#### factions

- `id` / `name` / `color`（`#RRGGBB`）/ `showOnScoreboard`
- `levelTable` — 引用顶层 `levelTables` 的表名；不写 = 无等级系统

#### levelTables

顶层字典：表名 → 等级参数。

- `maxLevel` 等级上限（默认 500）
- `healthBonus` / `attackBonus` / `armorBonus` / `knockbackBonus` — 每级加成
- `healthExponent` / `attackExponent` / `armorExponent` / `knockbackExponent` — 成长指数
- `maxDamage` — 投掷物伤害倍率上限（默认 200）

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

#### levelTableOverrides

- 键是实体类型 `modid:name`，值是要用的表名
- 优先级最高，可给单只 boss 单独曲线

```json
"levelTableOverrides": { "minecraft:zombie": "weak" }
```

解析优先级：`levelTableOverrides` → `options.projectile.levelTable` → `options.summon.levelTable` → `factions[].levelTable`。表名不存在时 warn 并回退；全部落空 = 无等级。

#### relations

- `from` / `to`（支持 `*`）/ `type`（`hostile` / `neutral`）

判定顺序：

1. 双方都无阵营 → 不敌对
2. 仅一方无阵营 → 敌对（混乱生物）
3. 同阵营 → 不敌对
4. 精确匹配 `from→to`，再反向
5. 通配符 `*→from`，再 `*→to`
6. 未定义 → 不敌对

#### waypoints

- `id` / `factionId` / `x1,z1,x2,z2`（XZ 矩形柱体，无限高）
- 只定义区域，行走顺序由 `routes[]` 决定
- `lane` / `order` 是旧字段：仍解析，但无逻辑读取

#### routes

- `id` / `waypoints[]`（引用 waypoint id）
- 所有阵营共用；本阵营按顺序、非本阵营按逆序
- 生物只绑定一条 route，走完即终点

绑定优先级：运行时进度 → NBT `bettereve:route_id` → 就近选路。可用 `/bettereve spawn ... <routeId>` 显式指定。

#### crystals

- `id` / `factionId` / `x,y,z`
- `health` / `attackDamage` / `armor`
- `attackRange` / `attackCooldown`
- `sizeKey` / `modelScale` / `showHealthBar`

#### phases

- `id` / `description`
- `commands[]` — `delay` 延迟秒 / `command`，多指令用 `&&` 分隔，OP 执行
- `triggers[]` — 阶段切换触发器
- `spawnBases` — `templateId` + `positions[]`
- `announce` — 未接线，只会 post 事件
- `resetAfter` / `phaseType` — 未接线

剧本加载后自动进入 `phases[0]`。切换阶段会清空上一阶段未执行的延迟指令。

##### triggers 通用字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | ✅ | 条件类型 |
| `targetPhase` | ✅ | 命中后切到的阶段；缺失则不生效 |
| `timeout` | — | 进入本阶段 N 秒仍未触发 → 也切 |
| `delay` | — | 命中后延迟 N 秒再切；期间 timer/timeout/其他条件冻结 |
| `winner` | — | 日志胜者：`red` / `blue` / `none` |
| `intervalTicks` | — | 轮询采样间隔 |

##### 条件类型

| `type` | 语义 | 关键字段 |
|---|---|---|
| `timer` | 进入本阶段后 N 秒 | `threshold` |
| `crystal_destroyed` | 指定水晶被摧毁 | `targetId` |
| `faction_wiped` | 在场数从 >N 降到 ≤N | `factionId`、`threshold` |
| `entity_death` | 指定生物死亡累计 N 只 | `entityType` / `name` / `nbt` / `factionId` / `count` |
| `player_death` | 指定玩家死亡 | `player` / `count` |
| `entity_state` | 生物进入某 NBT 状态 | `entityType` / `name` / `factionId` / `nbt` |
| `entity_health` | 生物/水晶血量低于门槛 | 目标过滤 + `ratio` 或 `below`；水晶用 `targetId` |
| `zone_enter` | 生物进入区域 | `zone` 或 `waypointId` + 目标过滤 |
| `zone_leave` | 生物全部离开区域 | 同上 |
| `score` | 阵营比分达到门槛 | `factionId`、`kind`（`kills`/`crystals`）、`threshold` |
| `command_check` | 只读指令返回值 ≥1 | `command` |
| `all_of` / `any_of` | 全部/任一子条件满足 | `triggers[]` |

目标过滤字段：`entityType`、`name`、`factionId`、`nbt`、`player`。

示例：

```json
"triggers": [
  { "type": "crystal_destroyed", "targetId": "red_base", "targetPhase": "blue_victory" },
  { "type": "timer", "threshold": 12, "targetPhase": "match_start" },
  { "type": "faction_wiped", "factionId": "blue", "timeout": 240, "targetPhase": "match_2" },
  { "type": "score", "factionId": "red", "kind": "kills", "threshold": 3, "targetPhase": "done" },
  { "type": "all_of",
    "triggers": [ { "type": "faction_wiped", "factionId": "red" },
                  { "type": "faction_wiped", "factionId": "blue" } ],
    "targetPhase": "double_ko" }
]
```

约定：

- 按数组顺序检查，首个命中即切换并跳出
- 进阶段重置全部触发器状态
- 事件类到达即判；轮询类按间隔采样
- 除 `entity_health` 外，命中过的条件本阶段内锁定
- 水晶不算生物；`faction_wiped` 只数生物
- 未识别 `type` 静默忽略
- 一个阶段只写一个 `timer`，否则计数成倍加速

##### faction_wiped 语义

- 统计在场实体：玩家按存活；其他生物只要还在世界里就算
- 本阶段召唤过才算（0 → 有）
- `threshold` 不写 = 0：从 >0 降到 0 命中；写 N：从 >N 降到 ≤N 命中
- 不含水晶

##### 阶段切换日志

```text
[bettereve] phase match_1 -> match_2 winner=red reason=wiped elapsed=87s
[bettereve] phase match_3 -> match_4 winner=none reason=timeout elapsed=240s
```

`reason`：`wiped` / `timeout` / `timer` / `manual` / `load` / 其他触发器类型原样记。格式固定，外部工具可正则抓取。

#### options

| 字段 | 默认 | 说明 |
|---|---|---|
| `playersDefaultFaction` | 无 | 新玩家默认阵营 |
| `targetingRange` | 64.0 | 常态巡敌距离 |
| `targetingFocusRange` | 10.0 | 近距离重扫距离 |
| `targetingFocusInterval` | 20 | 重扫间隔（tick） |
| `hudEnabled` | true | HUD 总开关 |
| `overheadHealthBar` | true | 头顶血条总开关 |
| `healthBarScale` | 1.0 | 头顶血条缩放（0.1~3.0） |
| `healthBarLevelText` | true | 显示 `Lv` 文字 |
| `healthBarHealthText` | true | 显示血量数字 |
| `versusHealthBar` | false | 格斗血条开关 |
| `versusHealthBarSubCount` | 2 | 每阵营次要血条数 |
| `versusHealthBarStyle` | `plain` | `plain` / `stripe` / `grid` |
| `versusHealthBarHealthText` | true | 主血条显示当前/上限 |
| `summon` | 见下 | 召唤物机制 |
| `projectile` | 见下 | 投掷物机制 |
| `spawnLayout` | 见下 | 指令召唤阵型 |
| `sunBurn` | 见下 | 日光燃烧 |
| `rules` | 见下 | 裁决规则 |

##### options.summon

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关 |
| `inheritFaction` | true | 继承阵营 |
| `inheritLevel` | true | 继承等级 |
| `hideHealthBar` | false | 隐藏血条 |
| `countsForScore` | false | 击杀计分 |
| `healthBarOverrides` | 无 | 按实体类型覆盖血条 |
| `factionOverrides` | 无 | 按实体类型强制阵营 |
| `levelTable` | 无 | 专用等级表 |

```json
"summon": {
  "inheritFaction": true,
  "inheritLevel": true,
  "countsForScore": false,
  "healthBarOverrides": { "jujutsucraft:projectile_slash": false },
  "factionOverrides": { "jujutsucraft:purple": "gojo" }
}
```

##### options.projectile

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关；关掉后阵营与等级都不继承 |
| `inheritFaction` | true | 继承阵营 |
| `inheritLevel` | true | 继承等级 |
| `levelTable` | 无 | 专用等级表 |

```json
"projectile": { "inheritFaction": true, "inheritLevel": false }
```

`Projectile` 家族一律算投掷物；名字像投掷物的实体（`bullet_*` / `*_slash` / `needle` / `spear` 等）也按投掷物对待。加入世界判一次，失败挂起到 tick 末再判，最多 3 轮。已有阵营不覆盖；存档加载不重复继承。

##### options.spawnLayout

| 字段 | 默认 | 说明 |
|---|---|---|
| `formation` | `circle` | `circle` / `square` / `diamond` / `rectangle` / `triangle` / `line_x` / `line_z` / `grid` / `point` |
| `radius` | 2.0 | 半径 |
| `spacing` | 2.0 | 间距 |
| `arc` | 360.0 | 张角（度） |
| `width` / `depth` | `radius × 2` | 矩形宽/深 |

##### options.sunBurn

防骷髅/僵尸/幻翼被日光点燃。没写 = 不烧；没加载剧本也不烧。

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `false` | 写 `true` 才走原版 |
| `except` | 无 | 例外名单，末尾 `*` 前缀匹配 |

```json
"options": { "sunBurn": { "enabled": false, "except": ["minecraft:zombie"] } }
```

注入 `Mob.isSunBurnTick()` HEAD；岩浆/火焰/闪电照旧；已在燃烧的生物最多 8 秒烧完。

##### options.rules

| 子段 | 字段 | 说明 |
|---|---|---|
| `lethalHits[]` | `attacker` / `victim` / `attackerFlag` / `damage` | 把这一击改写成致死量 |
| `stateKills[]` | `type` / `nbt` / `kills` / `intervalTicks` | 按 NBT 状态连杀 |
| `attributeOverrides[]` | `type` / `healthMultiplier` / `armorMultiplier` / `attackMultiplier` | 新加入世界时应用一次 |

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

#### keepAliveZone

- `x1,z1,x2,z2`：区域内生物不 despawn，chunk 常驻
- 单生物：`/bettereve spawn ... <count> true`
- 标记只保证不被清除；远处生物继续 tick 仍需区域 chunk 常驻

#### 生物 NBT 键

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `BetterEvE:ShowHealthBar` | 布尔 | true | 显示血条 |
| `BetterEvE:ShowLevelText` | 布尔 | true | 显示 `Lv.x` |
| `BetterEvE:ShowHealthText` | 布尔 | true | 显示血量数字 |
| `bettereve:route_id` | 字符串 | 无 | 绑定路线 id |
| `BetterEvE:KeepAlive` | 布尔 | false | 单生物防清除 |

#### 已知未接线

| 位置 | 内容 | 原因 |
|---|---|---|
| `phases[].phaseType` | `point` / `loop` / `branch` | 无代码读取 |
| `triggers[].type` | `base_destroyed` / `custom` | 旧名/未接线 |
| `waypoints[].lane` / `order` | — | 被 `routes[]` 取代 |
| `phases[].announce` | `message` / `duration` | 无订阅者；播报写 `commands` |
| `phases[].resetAfter` | — | 无代码读取 |
| `/bettereve scenario info` | — | 未注册到指令树 |

</details>

## 构建

需要 JDK 17。

```bash
./gradlew build
```

产物在 `build/libs/`。

## 测试

测试前确认：

1. 执行 `gradlew.bat runClient` 启动游戏
2. 创建/进入世界（作弊开启）
3. 按 `T` 打开聊天栏

### 快速验证

```text
/bettereve scenario list
/bettereve scenario load default
/bettereve faction list
/bettereve faction join red
/bettereve phase list
/bettereve phase switch match_start
```

预期：剧本加载成功，红蓝阵营可见，阶段切换后生成水晶。

<details>
<summary><strong>展开完整测试清单</strong></summary>

### 1. 剧本系统

```text
/bettereve scenario list
/bettereve scenario load default
```

预期：列出 default / red_blue_versus / simple_red_blue；加载后控制台打印阵营/关系/路径点/路线数量。若列表为空，提示没有找到剧本。

### 2. 阵营系统

```text
/bettereve faction list
/bettereve faction join red
/bettereve faction my
```

预期：列出 red、blue；加入后显示当前阵营。没有内置 neutral 阵营。

### 3. 玩家攻击

- PvE：`/summon minecraft:zombie`，攻击它，能造成伤害
- PvP：另一玩家加入 blue 后互相攻击，能造成伤害
- 同阵营：互相攻击无法造成伤害
- 无阵营：`/bettereve faction clear` 后可被攻击；无阵营玩家会打到所有人

### 4. 生物阵营行为

```text
/bettereve spawn minecraft:zombie red 1 10 60 10 1
/bettereve spawn minecraft:chicken blue 1 15 60 15 1
```

参数：`<实体类型> <阵营> <等级> <x> <y> <z> <数量>`

预期：僵尸主动攻击鸡；鸡没有 `ATTACK_DAMAGE`，不参与近战。

### 5. 水晶基地

```text
/bettereve crystal create red 0 64 0 5000 15 5 15.0 2.0 5x10 1.0
/bettereve crystal spawn red_base
/bettereve crystal spawn blue_base
```

尺寸键 `宽x高`，范围 1x1 ~ 10x15。预期：水晶顶部显示血条；敌对生物自动攻击；同阵营玩家攻击无效。

### 6. 阶段系统

```text
/bettereve scenario load default
/bettereve phase list
/bettereve phase switch match_start
```

预期：标题显示比赛开始，生成 4 个水晶。

```text
/bettereve phase switch blue_victory
```

预期：标题显示红方基地被摧毁，倒计时后 12 秒自动切回 match_start。

### 7. 兵线

```text
/bettereve scenario load default
/bettereve waypoint visualize
/bettereve spawn minecraft:zombie red 1 -55 60 -5 1
```

预期：路径点四角出现粒子柱；僵尸沿 `route_a` 推进，本阵营顺序、敌方逆序。

### 8. 计分板与格斗血条

观察屏幕顶部：显示各阵营积分。剧本 `options.versusHealthBar: true` 后出现阵营格斗血条。

### 9. 水晶摧毁联动

加载 default 并 `phase switch match_start`，摧毁 `blue_base`：进入 red_victory，12 秒后回到 match_start 并重新生成 4 个水晶。

### 10. 召唤物阵营继承

```text
/bettereve scenario load default
/bettereve spawn minecraft:skeleton red 1 0 60 0 1
```

预期：骷髅射出的箭继承红队阵营，不会误伤红队。debug 日志打印判定依据。无归属证据的召唤物不继承。

### 11. 等级系统

`default` 没有 `levelTable`。用 `simple_red_blue`：

```text
/bettereve scenario load simple_red_blue
/bettereve spawn minecraft:zombie red 500 0 60 0 1
/bettereve spawn minecraft:zombie blue 1 10 60 0 1
```

预期：高等级僵尸血量/攻击明显更高，头顶显示 `Lv.500`。

### 12. 保活区域

default 的 `keepAliveZone` 是 x∈[-60,60]、z∈[-20,20]。区域内生成僵尸后跑远（>128 格），预期不消失。

```text
/bettereve spawn minecraft:zombie red 1 0 -59 0 1
/bettereve spawn minecraft:zombie red 1 5 -59 0 1 true
```

传送到约 60 格外等 60 秒：对照组被清除，标记组仍在。

### 13. 末影龙

```text
/summon minecraft:ender_dragon
```

让非玩家实体攻击末影龙，预期能造成伤害。龙坐地喷火时血量应掉；`DYING` 阶段不掉血。

### 14. 监守者

```text
/summon minecraft:warden
```

两只不同阵营的监守者可以互打；两只都无阵营时与原版一致（不互殴）。

### 15. 多场次剧本

新建 `config/bettereve/scenarios/zz_wipe_probe.json`，验证 `faction_wiped` / `timeout` / 切阶段日志。

- 只杀蓝队、保留红队 → 约 1~1.5 秒内 `phase p1 -> p2 winner=red reason=wiped`
- 什么都不杀等 30 秒 → `reason=timeout`
- 空场不触发
- 同 tick 红蓝全灭 → 只切一次，winner=`none`
- 玩家加入阵营也算存活实体

### 16. 阶段触发器全条件

用探针剧本逐个进阶段，验证全部 `type` 的日志输出。

服务器测试注意：无玩家时 chunk 不做实体 tick，先用 `/forceload add -32 -32 32 32` 强制加载竞技场。

### 17. 日光燃烧开关

```text
/bettereve scenario load default
/time set day
/summon minecraft:zombie
```

预期：一分钟不烧。`enabled: true` 后白天恢复燃烧。`except: ["minecraft:zombie"]` → 僵尸照烧、骷髅不烧。岩浆/火焰/闪电照旧。

### 18. BOSS 伤害

```text
/summon minecraft:wither ~ ~ ~6
/data get entity @e[type=minecraft:wither,limit=1] Health
/bettereve spawn minecraft:zombie red 1 ~ ~ ~3 5
```

等 10 秒后再读血量：阵营敌对 → 掉血；不装剧本/双方无阵营 → 仍 0 伤害。

确认 mixin：开游戏后 `grep "Mixing" run/logs/debug.log`，应看到 `WitherBossMixin` / `EnderDragonMixin`。

### 19. 投掷物同阵营穿透

```text
/bettereve scenario load default
/bettereve spawn minecraft:skeleton red 1 0 60 0 1
/bettereve spawn minecraft:zombie red 1 0 60 6 1
```

预期：红骷髅的箭忽略红队僵尸，不受伤、不挡箭、不反弹、不消耗。无阵营的箭不拦。

</details>

## 项目结构

```text
BetterEvE/
├── scenarios/              # 预设剧本 JSON
│   ├── default.json
│   ├── red_blue_versus.json
│   └── simple_red_blue.json
├── src/
│   ├── main/
│   │   ├── java/com/example/bettereve/
│   │   │   ├── ai/          # 目标选择器 + 寻路
│   │   │   ├── capture/     # 模型捕获
│   │   │   ├── commands/    # 指令
│   │   │   ├── crystal/     # 水晶基地
│   │   │   ├── entity/      # 自定义实体
│   │   │   ├── entitylv/    # 等级系统
│   │   │   ├── events/      # 事件处理
│   │   │   ├── faction/     # 阵营 + 关系
│   │   │   ├── mixin/       # Mixin
│   │   │   ├── network/     # 网络包
│   │   │   ├── phase/       # 阶段 + 触发器
│   │   │   ├── render/      # 血条/HUD
│   │   │   ├── scenario/    # 剧本加载
│   │   │   ├── scoreboard/  # 计分板
│   │   │   ├── targeting/   # 目标管理
│   │   │   └── waypoint/    # 兵线
│   │   └── resources/
│   │       └── bettereve.mixins.json
│   └── test/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── LICENSE
└── README.md
```

## 兼容性

- Minecraft 1.20.1 + Forge 47.x
- Mixin 0.8.5（Forge 内置）
- 无 `@Overwrite`；全部 `@Inject` + Accessor；唯一 `@Redirect` 在 `WitherBossMixin`
- 所有反射调用已移除
- Gson（Forge 捆绑）
- ForgeGradle 6.x + JDK 17

## 常见问题

| 问题 | 原因 | 解决 |
|---|---|---|
| 玩家无法攻击 | 玩家无阵营 | `/bettereve faction join <阵营>` |
| 指令不完整 | 缺少自动补全 | 按 Tab 看参数 |
| 剧本未加载 | 未执行 scenario load | `/bettereve scenario load default` |
| `scenario list` 为空 | 剧本文件不在 | 确认 `config/bettereve/scenarios/` 下有 json |
| 生物不动 | 剧本没有 `routes` | 用含 `routes` 的剧本 + `waypoint visualize` |
| 生物不互打 | 关系未定义 | `relations` 补 `hostile`；未定义 = 不敌对 |
| 阶段不切换 | 触发器类型未接线 | 用 `crystal_destroyed` / `timer` / `faction_wiped` |
| 水晶不生成 | 模板 id 写错 | `/bettereve crystal spawn <模板id>` 与 `crystals[].id` 一致 |

## 支持与联系

遇到问题请开 [GitHub Issue](https://github.com/SanZiNEO/BetterEvE/issues)，这是本项目唯一的支持渠道。请附上：

- 游戏版本、Forge 版本、BetterEvE 版本
- 使用的剧本 JSON
- `logs/latest.log` 或崩溃报告

## 贡献

欢迎 Issue 和 Pull Request。较大的改动请先开 Issue 讨论。

提交 PR 前确认：

- `./gradlew build` 通过
- 新增剧本字段同步更新 README
- 不引入重复的第二套约定

## 致谢

感谢 Minecraft Forge 团队和社区提供的开发工具与文档，感谢 Mojang 提供的 Minecraft，以及所有测试、反馈和提出建议的玩家。

## 许可证

本项目使用 [GNU Lesser General Public License v3.0](LICENSE)（LGPL-3.0-only）。

你可以自由使用、修改和分发本模组，包括商业用途；修改后的模组必须以 LGPL-3.0 发布，并提供对应源码。

---

BetterEvE 不是 Minecraft 官方产品，与 Mojang 或 Microsoft 无关。
