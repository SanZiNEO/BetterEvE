# BetterEvE 测试指南

按此文档顺序测试，每步验证通过后再继续。

---

## 前置条件

1. 执行 `gradlew.bat runClient` 启动游戏
2. 创建/进入一个世界（作弊开启）
3. 按 `T` 打开聊天栏输入指令

---

## 测试步骤

### 1. 剧本（Scenario）系统

```
/bettereve scenario list
```
预期：列出 `scenarios/` 目录下的剧本名（default / red_blue_versus / simple_red_blue）。
若为空则提示"没有找到剧本（检查 config/bettereve/scenarios/ 目录）"——**模组不会自动创建默认剧本**。

```
/bettereve scenario load default
```
预期：提示"已加载剧本: default"，控制台打印加载的阵营/关系/路径点/路线数量。

### 2. 阵营（Faction）系统

```
/bettereve faction list
```
预期：列出 red（红队）、blue（蓝队）。**没有内置的 neutral 阵营** —— 未分配的玩家就是"无阵营"。

```
/bettereve faction join red
```
预期：提示"已加入阵营: 红队"

```
/bettereve faction my
```
预期：显示"当前阵营: 红队 (red)"

### 3. 玩家攻击

**PvE**：`/summon minecraft:zombie`，攻击它
预期：能正常造成伤害

**PvP**：开局域网联机，另一玩家 `/bettereve faction join blue` 后互相攻击
预期：可攻击（不同阵营）

**同阵营保护**：两人同阵营时互相攻击
预期：无法攻击

**无阵营行为**：对方 `/bettereve faction clear` 后（无阵营）
预期：可被攻击；无阵营玩家自己会打到所有人（"混乱生物"）

### 4. 生物阵营行为

```
/bettereve spawn minecraft:zombie red 1 10 60 10 1
/bettereve spawn minecraft:chicken blue 1 15 60 15 1
```
参数顺序：`<实体类型> <阵营> <等级> <x> <y> <z> <数量>`

预期：在 (10,60,10) 生成红队僵尸、(15,60,15) 生成蓝队鸡

观察：僵尸应主动走向并攻击鸡
注意：鸡没有 `ATTACK_DAMAGE` 属性，不参与近战（防崩溃设计）

### 5. 水晶基地（Crystal）系统

现场创建（参数：`<阵营> <x> <y> <z> <血量> <攻击力> <护甲> <攻击范围> <攻击冷却> <尺寸> <模型缩放>`）：

```
/bettereve crystal create red 0 64 0 5000 15 5 15.0 2.0 5x10 1.0
```
尺寸键格式为 `宽x高`，可用范围 **1x1 ~ 10x15**（共 150 种）。

按剧本模板生成（`default` 剧本已定义 4 个模板）：

```
/bettereve crystal spawn red_base
/bettereve crystal spawn blue_base
```

观察：
- 水晶顶部显示血条
- 敌对阵营生物会自动攻击它
- 同阵营玩家攻击无效

### 6. 阶段（Phase）系统

```
/bettereve scenario load default
/bettereve phase list
```
预期：列出 match_start、blue_victory、red_victory，并标出当前阶段

```
/bettereve phase switch match_start
```
预期：屏幕中央标题显示"§e§l比赛开始！"，随后 4 个水晶被生成

```
/bettereve phase switch blue_victory
```
预期：标题显示"红方基地被摧毁！蓝队获得最终胜利！"，执行 10 秒倒计时，
**12 秒后**自动切回 match_start（`timer` 触发器，`threshold: 12`）

⚠️ 一个阶段只应写一个 `timer` 触发器——计数器自增在触发器循环体内，写多个会成倍加速。

每次切换阶段日志会打一行固定格式（外部数据台按此行记比分，见 `CONFIGURATION.md`「阶段切换日志」）：

```
[bettereve] phase - -> match_start winner=none reason=load elapsed=0s
[bettereve] phase blue_victory -> match_start winner=none reason=timer elapsed=12s
```

### 7. 兵线（Waypoint + Route）

```
/bettereve scenario load default
/bettereve waypoint visualize
```
预期：在路径点区域四个角出现粒子柱（再次执行可关闭）

```
/bettereve spawn minecraft:zombie red 1 -55 60 -5 1
```
预期：僵尸自动沿 `route_a` 推进 —— 红队按列表顺序走
（red_waypoint_1 → red_waypoint_2），随后接敌方 waypoint 的**逆序**
（blue_waypoint_2 → blue_waypoint_1）

观察：生物 NBT 会被写入 `bettereve:route_id`，卸载重生后仍走原路线

### 8. 计分板与格斗血条

观察屏幕顶部
预期：显示各阵营积分（水晶摧毁数 / 实体击杀数）

在剧本 `options` 里开 `versusHealthBar: true` 后
预期：顶部出现阵营格斗血条（主血条 + 每阵营若干条次要血条）

### 9. 水晶摧毁联动

```
/bettereve scenario load default
/bettereve phase switch match_start
```
用红队生物或玩家摧毁 `blue_base`
预期：播报"蓝方基地被摧毁！红队获得最终胜利！"，进入 red_victory 阶段，
12 秒后回到 match_start 并重新生成 4 个水晶

### 10. 召唤物阵营继承

```
/bettereve scenario load default
/bettereve spawn minecraft:skeleton red 1 0 60 0 1
```
观察：
- 骷髅射出的箭应继承红队阵营（血条颜色 / 不会误伤红队）
- 开启 **debug 日志**后，控制台会打印判定依据：
  `[召唤继承] <实体> 继承阵营 <阵营> ← 主体 <召唤者> (REASON)`
  - `REASON` 取值：`OWNER`（原版投掷物/恼鬼）/ `OWNER_UUID`（可驯服体系）/ `OWNER_NBT`（跨模组 NBT 痕迹，主通道）/ `TARGET`（目标反推）/ `PARENT`（多部件部件）/ `MANUAL`（指令显式指定）
- 若召唤者本身有等级，召唤物同时继承等级：`[召唤继承] <实体> 继承等级 <等级> ← 主体 <召唤者> (REASON)`
- 可用 `options.summon.inheritLevel: false` 单独关掉等级继承（阵营继承不受影响）：
  召唤物仍有阵营色，但头顶不再显示 `Lv.`

**无归属证据的召唤物不会被继承**（设计铁律：证据不存在时什么都不做，宁可漏绝不误判）。

### 11. 等级（EntityLevel）系统

`default` 剧本的阵营**没有配 `levelTable`**，所以没有等级。
用 `simple_red_blue` 剧本（红蓝都引用 `levelTables.standard`，`maxLevel: 1000`）验证：

```
/bettereve scenario load simple_red_blue
/bettereve spawn minecraft:zombie red 500 0 60 0 1
/bettereve spawn minecraft:zombie blue 1 10 60 0 1
```
预期：等级高的僵尸血量/攻击明显更高，头顶血条显示 `Lv.500`

**多套表 + 实体类型覆盖**：顶层加一张弱化表，让僵尸单独引用它：

```json
"levelTables": {
  "standard": { ...原有九项... },
  "weak": { "maxLevel": 1000, "healthBonus": 0.01, "healthExponent": 2.0,
            "attackBonus": 0.01, "attackExponent": 2.0, "armorBonus": 0.01, "armorExponent": 0.7,
            "knockbackBonus": 0.0, "knockbackExponent": 1.0 }
},
"levelTableOverrides": { "minecraft:zombie": "weak" }
```
重新加载剧本后分别生成 `minecraft:zombie` 与 `minecraft:skeleton`（都设 level 500）：
预期：僵尸血量接近基础值（`weak` 表），骷髅远高于僵尸（`standard` 表）；
两者头顶都显示 `Lv.500` —— 差异来自表而不是等级。

**投掷物开关**：`options` 里写 `"projectile": { "inheritFaction": true, "inheritLevel": false }`，
生成 `minecraft:skeleton red 500` 让它射箭：
预期：箭仍继承阵营色，但箭伤接近基础值；改回 `inheritLevel: true` 后箭伤显著放大
（level 500 时攻击倍率封顶 200×）。`enabled: false` 时箭连阵营都不继承。

**缺表告警**：把 `levelTableOverrides` 的值改成不存在的表名，加载剧本并生成该实体：
预期：日志出现一次 `等级表 '<名字>' 未在 scenario.levelTables 中定义`，实体回退到阵营表（仍显示 `Lv.`），
同一名字不重复告警。

### 12. 保活区域

`default` 剧本的 `keepAliveZone` 是 x∈[-60,60]、z∈[-20,20]。
在该区域内 `/summon minecraft:zombie`，然后跑远（>128 格）
预期：生物不自然消失（区域内 chunk 常驻）

**单生物防清除（不依赖场地）**：

```
/bettereve spawn minecraft:zombie red 1 0 -59 0 1            ← 对照组（不带标记）
/bettereve spawn minecraft:zombie red 1 5 -59 0 1 true       ← 标记组
```
把玩家 `/tp` 到约 60 格外（区块仍在模拟距离内、超过 32 格），等 60 秒
预期：对照组被原版清除，标记组仍在
`/data get entity @e[type=zombie,limit=1]` 应能看到 `PersistenceRequired:1b`
与 `ForgeData.BetterEvE:KeepAlive:1b`；`save-all` 后退出重进世界，标记仍在

### 13. EnderDragonMixin

```
/summon minecraft:ender_dragon
```
让非玩家实体（如骷髅）攻击末影龙
预期：能造成伤害（原版只有玩家能伤龙）

### 14. WardenMixin

```
/summon minecraft:warden
```
召唤两只不同阵营的 Warden，观察是否互打
预期：不同阵营敌对 → 可以互打（原版 Warden 不打 Warden）

### 15. 多场次剧本（`faction_wiped` / `timeout` / 切阶段日志）

新建探针剧本 `config/bettereve/scenarios/zz_wipe_probe.json`：

```json
{
  "id": "zz_wipe_probe",
  "name": "多场次探针",
  "factions": [
    { "id": "red",  "name": "红队", "color": "#FF0000" },
    { "id": "blue", "name": "蓝队", "color": "#0000FF" }
  ],
  "phases": [
    { "id": "p1",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie red 1 0 60 0 1" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie blue 1 5 60 0 1" }
      ],
      "triggers": [
        { "type": "faction_wiped", "factionId": "blue", "timeout": 30, "targetPhase": "p2" },
        { "type": "faction_wiped", "factionId": "red",  "timeout": 30, "targetPhase": "p2" }
      ] },
    { "id": "p2",
      "commands": [ { "delay": 0, "command": "kill @e[type=!minecraft:player]" } ],
      "triggers": [ { "type": "timer", "threshold": 3, "targetPhase": "p1" } ] }
  ]
}
```

```
/bettereve scenario load zz_wipe_probe
```
预期：日志 `[bettereve] phase - -> p1 winner=none reason=load elapsed=0s`；p1 在 (0,60,0)/(5,60,0) 各生成一只僵尸

**wiped（分胜负）**：只杀蓝队那只，保留红队：

```
/kill @e[type=minecraft:zombie,nbt={ForgeData:{"BetterEvE:FactionID":"blue"}}]
```
预期（约 1~1.5 秒内 —— 尸体 20 tick 后移除，触发器每 5 tick 采样一次）：日志 `[bettereve] phase p1 -> p2 winner=red reason=wiped elapsed=Ns`；
p2 清场，3 秒后回 p1（日志 `phase p2 -> p1 winner=none reason=timer elapsed=3s`）

**timeout（僵局兜底）**：进 p1 后什么都不杀，等 30 秒
预期：日志 `[bettereve] phase p1 -> p2 winner=none reason=timeout elapsed=30s`

**边界**：

- 进 p1 后阵营还没召唤（空场）→ 不触发（不会一进阶段就切走）
- 同一 tick 内红蓝全灭 → 只切一次（首个命中即跳出），winner=`none`
- 玩家加入某阵营也算该阵营的存活实体 → 该阵营不会判"被消灭"

**复活生物（不会误判 wipe）**：新建探针剧本 `config/bettereve/scenarios/zz_revive_probe.json`：

```json
{
  "id": "zz_revive_probe",
  "name": "复活判定探针",
  "keepAliveZone": { "x1": -40, "z1": -40, "x2": 40, "z2": 40 },
  "factions": [
    { "id": "red",  "name": "红队", "color": "#FF0000" },
    { "id": "blue", "name": "蓝队", "color": "#0000FF" }
  ],
  "relations": [
    { "from": "red", "to": "blue", "type": "hostile" },
    { "from": "blue", "to": "red", "type": "hostile" }
  ],
  "phases": [
    { "id": "p1",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 3, "command": "bettereve spawn mutantmonsters:mutant_zombie blue 0 ~-5 ~ ~ 1 true" }
      ],
      "triggers": [
        { "type": "faction_wiped", "factionId": "blue", "targetPhase": "p2", "timeout": 180 }
      ] },
    { "id": "p2",
      "commands": [ { "delay": 0, "command": "kill @e[type=!minecraft:player]" } ] }
  ]
}
```

```
/bettereve scenario load zz_revive_probe
```
然后对僵尸突变体连打 4 次：

```
/damage @e[type=mutantmonsters:mutant_zombie,limit=1] 1000
```
预期：前 3 次倒地约 7 秒后原地复活（37.5 血），**期间日志里不应出现 `phase p1 -> p2`**；
第 4 次（命数耗尽）走 5 秒消散、实体移除后才出现
`[bettereve] phase p1 -> p2 winner=none reason=wiped elapsed=Ns`（红方没召唤，winner=none 是预期）

### 16. 阶段触发器全条件（探针剧本）

新建探针剧本 `config/bettereve/scenarios/zz_trig_probe.json`（每个阶段测一个条件，用 `/bettereve phase switch tN` 逐个进）：

```json
{
  "id": "zz_trig_probe",
  "name": "触发器探针",
  "keepAliveZone": { "x1": -40, "z1": -40, "x2": 40, "z2": 40 },
  "factions": [
    { "id": "red",  "name": "红队", "color": "#FF0000" },
    { "id": "blue", "name": "蓝队", "color": "#0000FF" }
  ],
  "relations": [
    { "from": "red",  "to": "blue", "type": "hostile" },
    { "from": "blue", "to": "red",  "type": "hostile" }
  ],
  "crystals": [
    { "id": "probe_crystal", "factionId": "blue", "x": 8, "y": 60, "z": 8,
      "health": 40, "attackDamage": 0, "armor": 0, "attackRange": 1.0,
      "attackCooldown": 10.0, "sizeKey": "1x1", "modelScale": 1.0, "showHealthBar": false }
  ],
  "phases": [
    { "id": "t1",
      "commands": [
        { "delay": 0, "command": "gamerule doMobSpawning false" },
        { "delay": 0, "command": "gamerule doDaylightCycle false" },
        { "delay": 0, "command": "time set midnight" },
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 0, "command": "fill -5 58 -5 10 64 5 minecraft:air" },
        { "delay": 0, "command": "fill -5 59 -5 10 59 5 minecraft:stone" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie blue 1 0 60 0 3" }
      ],
      "triggers": [
        { "type": "faction_wiped", "factionId": "blue", "threshold": 1, "winner": "red", "targetPhase": "t2" }
      ] },

    { "id": "t2",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "summon minecraft:zombie 0 60 0 {CustomName:'\"probe_boss\"',NoAI:1b}" }
      ],
      "triggers": [
        { "type": "entity_death", "name": "probe_boss", "winner": "blue", "targetPhase": "t3" }
      ] },

    { "id": "t3",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "summon minecraft:zombie 0 60 0 {NoAI:1b}" },
        { "delay": 1, "command": "summon minecraft:zombie 3 60 0 {NoAI:1b}" }
      ],
      "triggers": [
        { "type": "entity_death", "entityType": "zombie", "nbt": { "NoAI": true }, "count": 2, "targetPhase": "t4" }
      ] },

    { "id": "t4",
      "commands": [ { "delay": 0, "command": "kill @e[type=!minecraft:player]" } ],
      "triggers": [
        { "type": "entity_state", "entityType": "zombie", "nbt": { "NoAI": true }, "targetPhase": "t5" }
      ] },

    { "id": "t5",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "summon minecraft:zombie 0 60 0 {NoAI:1b}" }
      ],
      "triggers": [
        { "type": "entity_health", "entityType": "zombie", "ratio": 0.5, "winner": "red", "targetPhase": "t6" }
      ] },

    { "id": "t6",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "summon minecraft:zombie 20 60 0 {NoAI:1b}" }
      ],
      "triggers": [
        { "type": "zone_enter", "entityType": "zombie", "zone": { "x1": -5, "z1": -5, "x2": 5, "z2": 5 }, "targetPhase": "t7" }
      ] },

    { "id": "t7",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "summon minecraft:zombie 0 60 0 {NoAI:1b}" }
      ],
      "triggers": [
        { "type": "zone_leave", "entityType": "zombie", "zone": { "x1": -5, "z1": -5, "x2": 5, "z2": 5 }, "targetPhase": "t8" }
      ] },

    { "id": "t8",
      "commands": [ { "delay": 0, "command": "kill @e[type=!minecraft:player]" } ],
      "triggers": [
        { "type": "command_check", "command": "execute if entity @e[type=minecraft:zombie]", "winner": "blue", "targetPhase": "t9" }
      ] },

    { "id": "t9",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie red 1 -3 60 0 1" },
        { "delay": 1, "command": "summon minecraft:zombie 3 60 0 {ForgeData:{\"BetterEvE:FactionID\":\"blue\"},Health:1.0f}" },
        { "delay": 1, "command": "summon minecraft:zombie 6 60 0 {ForgeData:{\"BetterEvE:FactionID\":\"blue\"},Health:1.0f}" }
      ],
      "triggers": [
        { "type": "score", "factionId": "red", "kind": "kills", "threshold": 2, "targetPhase": "t10" }
      ] },

    { "id": "t10",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie red 1 -3 60 0 1" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie blue 1 3 60 0 1" }
      ],
      "triggers": [
        { "type": "all_of",
          "triggers": [
            { "type": "faction_wiped", "factionId": "red" },
            { "type": "faction_wiped", "factionId": "blue" }
          ],
          "winner": "none", "targetPhase": "t11" }
      ] },

    { "id": "t11",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 0, "command": "bettereve spawn minecraft:zombie red 1 0 60 0 4",
          "layout": { "formation": "grid", "spacing": 3 } }
      ],
      "triggers": [ { "type": "timer", "threshold": 3, "targetPhase": "t12" } ] },

    { "id": "t12",
      "commands": [ { "delay": 0, "command": "kill @e[type=!minecraft:player]" } ],
      "triggers": [
        { "type": "faction_wiped", "factionId": "red", "timeout": 5, "targetPhase": "t13" }
      ] },

    { "id": "t13",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 1, "command": "bettereve crystal spawn probe_crystal" },
        { "delay": 1, "command": "bettereve spawn minecraft:zombie red 1 -3 60 0 1" }
      ],
      "triggers": [
        { "type": "crystal_destroyed", "targetId": "probe_crystal", "winner": "red", "targetPhase": "t14" }
      ] },

    { "id": "t14",
      "commands": [
        { "delay": 0, "command": "kill @e[type=!minecraft:player]" },
        { "delay": 0, "command": "say probe done" }
      ] }
  ]
}
```

逐个进阶段（`/bettereve phase switch tN`）并按表操作，日志里应出现对应行：

| 阶段 | 操作 | 期望日志 |
|---|---|---|
| t1 | 生成 3 只蓝僵尸后杀 2 只（`kill @e[type=minecraft:zombie,limit=2]`） | `phase t1 -> t2 winner=red reason=wiped`（`threshold` + `winner` 覆盖） |
| t2 | `/kill @e[type=minecraft:zombie]` | `reason=entity_death`（按 `name` 匹配） |
| t3 | 杀 1 只（不切）→ 再杀 1 只 | `reason=entity_death`（按 `nbt` + `count: 2`） |
| t4 | `/summon minecraft:zombie 0 60 0 {NoAI:1b}` | `reason=entity_state` |
| t5 | `/damage @e[type=minecraft:zombie,limit=1] 12` | `reason=entity_health`（20 血降到 8，`ratio 0.5`） |
| t6 | `/tp @e[type=minecraft:zombie,limit=1] 0 60 0` | `reason=zone_enter` |
| t7 | 等 1 秒（记基线）→ `/tp @e[type=minecraft:zombie,limit=1] 20 60 0` | `reason=zone_leave` |
| t8 | `/summon minecraft:zombie 0 60 0 {NoAI:1b}` | `reason=command_check` |
| t9 | 等红僵尸把两只 1 血蓝僵尸打死（约 5-10 秒） | `reason=score` |
| t10 | 先杀红（不切）→ 再杀蓝 | `reason=all_of` |
| t11 | 无需操作（3 秒后自动切） | 日志里 spawn 那行带 `Formation: grid`（`CommandDef.layout` 覆盖），随后 `reason=timer` |
| t12 | 等 5 秒（红队一直空场，永不触发） | `reason=timeout` |
| t13 | `/damage @e[type=bettereve:crystal_entity_1x1,limit=1] 999 minecraft:player_attack by @e[type=minecraft:zombie,limit=1]` | `reason=crystal_destroyed` |

> ⚠️ 服务器测试（无玩家）注意：chunk 不做实体 tick 时僵尸不会移动/不会打架。
> 想在没有玩家时测 `score` 这类需要生物 AI 的条件，先用 `/forceload add -32 -32 32 32` 强制加载竞技场；
> 客户端测试（有玩家）不受影响。

`player_death` 需要真实玩家（`{ "type": "player_death", "player": "Steve", "targetPhase": "..." }`），
在客户端里用第二个账号进服后 `/kill Steve` 验证。

**`delay`（命中后延迟切换）**：把 t1 的触发器改成
`{ "type": "faction_wiped", "factionId": "blue", "threshold": 1, "delay": 3, "winner": "red", "targetPhase": "t2" }`，
杀 2 只蓝僵尸后观察：
- 阶段**不会立刻切**（1 秒后 `phase list` 仍是 t1）
- 日志先出现 `[触发器] faction_wiped 命中，3 秒后切换到 t2`，约 3 秒后才出现 `phase t1 -> t2 winner=red reason=wiped elapsed=3s`（`elapsed` 含延迟）
- 延迟期间该阶段的 `timer` / `timeout` 不生效；期间用 `/bettereve phase switch` 手动切走 → 预定取消，之后不会再多切一次

---

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 玩家无法攻击 | 玩家无阵营 | `/bettereve faction join <阵营>` |
| 指令不完整 | 缺少自动补全 | 按 Tab 键看可用参数 |
| 剧本未加载 | 未执行 scenario load | `/bettereve scenario load default` |
| `scenario list` 为空 | 剧本文件不在 | 确认 `config/bettereve/scenarios/` 下有 json |
| 生物不动 | 剧本没有 `routes` | 用含 `routes` 的剧本 + `/bettereve waypoint visualize` 确认区域 |
| 生物不互打 | 关系未定义 | `relations` 里补 `hostile`——**未定义 = 不敌对** |
| 阶段不切换 | 触发器类型未接线 | 可用 `crystal_destroyed` / `timer` / `faction_wiped`，其余详见 `CONFIGURATION.md`「已知未接线」 |
| 水晶不生成 | 模板 id 写错 | `/bettereve crystal spawn <模板id>` 需与剧本 `crystals[].id` 一致 |

---

## 已知未接线（测试时不要用）

以下功能写了也不会生效，详见 `CONFIGURATION.md`「已知未接线」：

- `phases[].phaseType`（`point` / `loop` / `branch`）
- `phases[].announce`（会 post 成事件但无订阅者，播报写进 `commands`）/ `phases[].resetAfter`
- `triggers[].type`：`base_destroyed` / `entity_death_count` / `custom`
- `waypoints[].lane` / `waypoints[].order`
- `/bettereve scenario info`
