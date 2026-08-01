# BetterEvE 配置说明

## 简介

BetterEvE 的全部配置已迁移到**剧本系统**：一个剧本 JSON 文件定义一个完整的游戏模式。旧的全局配置文件（`config/bettereve-common.toml`）与配置 GUI 已移除，模组完全由剧本驱动。

## 剧本位置

| 用途 | 路径 | 说明 |
|---|---|---|
| 预设剧本 | `scenarios/*.json`（项目根目录） | 游戏外编辑 |
| 运行时剧本 | `config/bettereve/scenarios/*.json` | 首次加载时自动从预设复制 |

## 剧本结构

一个剧本文件包含以下部分：

```json
{
  "id": "default",
  "name": "默认剧本",
  "description": "红蓝两队标准对战",
  "keepAliveZone": { "x1": -60, "z1": -20, "x2": 60, "z2": 20 },
  "factions": [ ... ],
  "relations": [ ... ],
  "waypoints": [ ... ],
  "crystals": [ ... ],
  "phases": [ ... ],
  "options": { ... }
}
```

### factions — 阵营
- `id` / `name` / `color`（`#RRGGBB`）/ `showOnScoreboard`（是否显示在计分板）
- `entitylvConfig` — 该阵营的等级参数：
  - `maxLevel` 等级上限（默认 500）
  - `healthBonus` / `attackBonus` / `armorBonus` / `knockbackBonus` — 每级加成
  - `healthExponent` / `attackExponent` / `armorExponent` / `knockbackExponent` — 成长指数

### relations — 阵营关系
- `from` / `to`（支持通配符 `*`）/ `type`（`hostile` / `neutral`）
- 同阵营不敌对；未定义的关系默认敌对

### waypoints — 兵线路径点
- `id` / `factionId` / `order` / `x1,z1,x2,z2`（XZ 矩形柱体，无限高）

### crystals — 水晶基地
- `id` / `factionId` / 位置 `x,y,z`
- `health` 血量、`attackDamage` 攻击力、`armor` 护甲
- `attackRange` / `attackCooldown` — 攻击范围与冷却
- `sizeKey` / `modelScale` — 尺寸与模型缩放、`showHealthBar` 是否显示血条

### phases — 游戏阶段
- `id` / `phaseType`（`point` / `loop` / `branch`）/ `description`
- `announce` — 播报（`message` / `duration`）
- `commands[]` — 指令（`delay` 延迟秒 / `command`，多指令用 `&&` 分隔，OP 权限执行）
- `triggers[]` — 阶段切换触发器（`type`：`base_destroyed` / `entity_death_count` / `timer` / `custom`；`factionId` / `targetId` / `threshold` / `targetPhase`）
- `spawnBases` — 生成水晶（`templateId` + `positions[]`）
- `resetAfter` — 延迟后重置

### options — 全局选项
- `playersDefaultFaction` 新玩家默认阵营（默认 `neutral`）
- `targetingRange` 常态巡敌距离（格，默认 64）
- `targetingFocusRange` 近距离重扫距离（格，默认 10）
- `targetingFocusInterval` 近距离重扫间隔（tick，默认 20）
- `hudEnabled` HUD 显示总开关（默认 true）

### keepAliveZone — 保活区域
- `x1,z1,x2,z2` 矩形区域，区域内的生物禁止自然 despawn（直播地图无自然生成时使用）

## 旧全局配置去向对照

| 旧配置项 | 新位置 |
|---|---|
| `targeting_defaultFollowRange` 寻敌范围 | 剧本 `options.targetingRange`（更细：常态 + 近距离重扫两档） |
| `crystal_defaultHealth` 水晶血量 | 剧本 `crystals[].health`（每水晶独立） |
| 等级参数（healthBonus / attackBonus / maxLevel） | 剧本 `factions[].entitylvConfig`（每阵营独立） |
| `global_hudEnabled` HUD 开关 | 剧本 `options.hudEnabled` |
| 渲染配置（血条显示等 6 项） | 已移除（血条显示方式计划更新） |
| `crystal_clearRadius` / `counter_countEntityType` / `global_levelEnabled` | 已移除（无代码读取） |

## 管理指令

```
/bettereve scenario list
/bettereve scenario load <名>
/bettereve scenario info
```
