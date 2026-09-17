# BetterEvE 配置说明

## 简介

BetterEvE 的全部配置已迁移到**剧本系统**：一个剧本 JSON 文件定义一个完整的游戏模式。旧的全局配置文件（`config/bettereve-common.toml`）与配置 GUI 已移除，模组完全由剧本驱动。

## 剧本位置

| 用途 | 路径 | 说明 |
|---|---|---|
| 预设剧本 | `scenarios/*.json`（项目根目录） | 游戏外编辑 |
| 运行时剧本 | `config/bettereve/scenarios/*.json` | 首次加载时自动从预设复制 |

## 剧本结构

```json
{
  "id": "default",
  "name": "默认剧本",
  "description": "红蓝两队标准对战",
  "keepAliveZone": { "x1": -60, "z1": -20, "x2": 60, "z2": 20 },
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
- `entitylvConfig` — 该阵营的等级参数（**不写 = 该阵营没有等级系统**）：
  - `maxLevel` 等级上限（默认 500）
  - `healthBonus` / `attackBonus` / `armorBonus` / `knockbackBonus` — 每级加成
  - `healthExponent` / `attackExponent` / `armorExponent` / `knockbackExponent` — 成长指数

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
- `announce` — 播报（`message` / `duration`）
- `commands[]` — 指令（`delay` 延迟秒 / `command`，多指令用 `&&` 分隔，OP 权限执行）
- `triggers[]` — 阶段切换触发器（见下）
- `spawnBases` — 生成水晶（`templateId` + `positions[]`）
- `resetAfter` — 延迟后重置
- ⚠️ `phaseType` 字段会被解析，但**没有任何代码读取**（写了无效）

剧本加载后自动进入 `phases[0]`。切换阶段会清空上一阶段尚未执行的延迟指令。

#### triggers — 触发器

| `type` | 语义 | 用到的字段 | 状态 |
|---|---|---|---|
| `crystal_destroyed` | 指定水晶被摧毁 | `targetId` = 水晶模板 id | ✅ 可用 |
| `timer` | 进入本阶段后 N 秒 | `threshold` = 秒 | ✅ 可用 |

```json
"triggers": [
  { "type": "crystal_destroyed", "targetId": "red_base",  "targetPhase": "blue_victory" },
  { "type": "timer", "threshold": 12, "targetPhase": "match_start" }
]
```

- `targetPhase` 必填，缺失则该触发器不会生效
- 触发器按数组顺序检查，**首个命中即切换并跳出**，后面的不再检查
- 同一阶段重复切换到当前阶段会被幂等忽略
- ⚠️ **一个阶段只应写一个 `timer` 触发器**：`timerTicks` 是阶段级单一计数器，且自增写在触发器循环体内 —— 写 N 个 timer 会让计数每 tick 加 N，触发时间变成 1/N

其余 `type` 值写了无效，见文末「已知未接线」。

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
| `spawnLayout` | 见下 | 指令召唤阵型 |

#### options.summon — 召唤物机制

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关 |
| `inheritFaction` | true | 召唤物是否继承宿主阵营 |
| `hideHealthBar` | false | 召唤物是否隐藏血条（默认 false = 显示，便于观察继承效果） |
| `countsForScore` | false | 召唤物击杀是否计分 |
| `healthBarOverrides` | 无 | 按实体类型覆盖血条，优先级最高 |

```json
"summon": {
  "inheritFaction": true,
  "countsForScore": false,
  "healthBarOverrides": { "jujutsucraft:projectile_slash": false }
}
```

#### options.spawnLayout — 指令召唤阵型

| 字段 | 默认 | 说明 |
|---|---|---|
| `formation` | `circle` | `circle` / `square` / `diamond` / `rectangle` / `triangle` / `line_x` / `line_z` / `grid` / `point` |
| `radius` | 2.0 | 半径（圆 / 方 / 菱 / 三角） |
| `spacing` | 2.0 | 间距（直线 / 网格） |
| `arc` | 360.0 | 张角（度）：360 整圆、180 半圆、≤0 退化为直线 |
| `width` | `radius × 2` | 矩形宽（X 方向） |
| `depth` | `radius × 2` | 矩形深（Z 方向） |

### keepAliveZone — 保活区域

- `x1,z1,x2,z2` 矩形区域，区域内生物禁止自然 despawn（直播地图无自然生成时使用）
- 区域内 chunk 常驻（运行时 ticket，不写盘）

## 生物 NBT 键

生成生物时可通过 NBT 参数设置：

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `BetterEvE:ShowHealthBar` | 布尔 | true | 该实体是否显示血条 |
| `BetterEvE:ShowLevelText` | 布尔 | true | 是否显示 `Lv.x` 文字 |
| `BetterEvE:ShowHealthText` | 布尔 | true | 是否显示血量数字 |
| `bettereve:route_id` | 字符串 | 无 | 绑定的路线 id（卸载重生后仍走原路线） |

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
| `/bettereve spawn <entityType> <factionId> <level> <x> <y> <z> <count> [routeId] [showHealthBar] [nbt]` | 按阵型批量生成生物 |

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
| `triggers[].type` | `base_destroyed` | 旧名残留（`crystal_destroyed` 的前身），switch 分支还在但无调用点 |
| `triggers[].type` | `entity_death_count` | 分支存在，从未接线 |
| `triggers[].type` | `custom` | 分支存在，从未接线 |
| `waypoints[].lane` / `waypoints[].order` | — | 旧版路线字段，被 `routes[]` 取代 |
| `/bettereve scenario info` | — | 处理函数 `executeScenarioInfo` 存在，但未注册到指令树 |

## 旧全局配置去向对照

| 旧配置项 | 新位置 |
|---|---|
| `targeting_defaultFollowRange` 寻敌范围 | 剧本 `options.targetingRange`（更细：常态 + 近距离重扫两档） |
| `crystal_defaultHealth` 水晶血量 | 剧本 `crystals[].health`（每水晶独立） |
| 等级参数（healthBonus / attackBonus / maxLevel） | 剧本 `factions[].entitylvConfig`（每阵营独立） |
| `global_hudEnabled` HUD 开关 | 剧本 `options.hudEnabled` |
| 渲染配置（血条显示等） | 剧本 `options.overheadHealthBar` / `healthBarScale` / `healthBarLevelText` / `healthBarHealthText` / `versusHealthBar*` |
| 召唤物机制配置 | 剧本 `options.summon` |
| 指令召唤阵型配置 | 剧本 `options.spawnLayout` |
| `crystal_clearRadius` / `counter_countEntityType` / `global_levelEnabled` | 已移除（无代码读取） |
