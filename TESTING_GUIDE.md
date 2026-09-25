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
| 阶段不切换 | 触发器类型未接线 | 只能用 `crystal_destroyed` 和 `timer`，详见 `CONFIGURATION.md`「已知未接线」 |
| 水晶不生成 | 模板 id 写错 | `/bettereve crystal spawn <模板id>` 需与剧本 `crystals[].id` 一致 |

---

## 已知未接线（测试时不要用）

以下功能写了也不会生效，详见 `CONFIGURATION.md`「已知未接线」：

- `phases[].phaseType`（`point` / `loop` / `branch`）
- `triggers[].type`：`base_destroyed` / `entity_death_count` / `custom`
- `waypoints[].lane` / `waypoints[].order`
- `/bettereve scenario info`
