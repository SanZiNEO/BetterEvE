# BetterEvE 测试指南

按此文档顺序测试，每步验证通过后再继续。

---

## 前置条件

1. 执行 `gradlew.bat runClient` 启动游戏
2. 创建/进入一个世界
3. 按 `T` 打开聊天栏输入指令

---

## 测试步骤

### 1. 验证场景（Scenario）系统

```
/bettereve scenario list
```
预期：列出 "default"（如果为空会自动创建默认剧本）

```
/bettereve scenario load default
```
预期：提示"已加载剧本: default"

### 2. 验证阵营（Faction）系统

```
/bettereve faction list
```
预期：列出红队(red)、蓝队(blue)、中立(neutral)

```
/bettereve faction join red
```
预期：提示"已加入阵营: 红队"

```
/bettereve faction my
```
预期：显示"当前阵营: 红队 (red)"

### 3. 验证玩家攻击

**测试 PvE**：使用 `/summon zombie` 召唤一只僵尸，攻击它
预期：能正常造成伤害

**测试 PvP**：开局域网联机（作弊开启），另一个玩家加入后：
- 对方 `/bettereve faction join blue`
- 你攻击对方
预期：可攻击（不同阵营）

- 同阵营攻击测试：两人同阵营时攻击
预期：无法攻击（同阵营保护）

### 4. 验证培养皿（生物阵营行为）

```
/bettereve scenario load default
/bettereve spawn minecraft:zombie red 1 10 60 10 1
```
预期：在坐标 (10, 60, 10) 生成一只红队僵尸

```
/bettereve spawn minecraft:chicken blue 1 15 60 15 1
```
预期：在坐标 (15, 60, 15) 生成一只蓝队鸡

观察：僵尸应自动走向并攻击鸡（不同阵营敌对）
注意：鸡作为被动生物也有 1 点基础攻击力，会还手

### 5. 验证建筑（Base）系统

```
/bettereve base create red 0 64 0 5000 15 5 15.0 2.0 5x10
```
预期：在坐标 (0, 64, 0) 生成一个 5x10 尺寸的红色基地，血量 5000

观察：
- 基地顶部显示血条
- 不同阵营生物会自动攻击基地
- 玩家（红队）攻击同色基地无效

### 6. 验证阶段（Phase）系统

```
/bettereve scenario load default
/bettereve phase list
```
预期：列出 match_start, blue_victory, red_victory

```
/bettereve phase switch match_start
```
预期：触发"战斗开始！"播报

```
/bettereve phase switch blue_victory
```
预期：触发"蓝队胜利！"播报，30 秒后自动重置

### 7. 验证兵线（Goal）系统

```
/bettereve scenario load default
/bettereve goal visualize
```
预期：在 Goal 区域四个角出现火焰粒子柱

生成红队生物后观察其行为：
```
/bettereve spawn minecraft:zombie red 1 -55 60 -5 1
```
预期：僵尸自动沿 Red_Goal_1 → Red_Goal_2 路径推进

### 8. 验证自定义计分板

完成上述操作后观察屏幕顶部
预期：显示"=== 阵营计分板 ==="，列出各阵营基地摧毁数和击杀数

### 9. 验证死亡播报

```
/bettereve base create blue 5 64 5 5000 15 5 15.0 2.0 5x10
```
然后用红队生物或玩家摧毁这个蓝色基地
预期：屏幕中央显示"蓝队基地被摧毁！X秒后进入下一轮"

### 10. 验证 EnderDragonMixin

```
/summon minecraft:ender_dragon
```
让非玩家实体（如骷髅）攻击末影龙
预期：能造成伤害（原版只有玩家能伤龙）

### 11. 验证 WardenMixin

```
/summon minecraft:warden
```
召唤两只不同阵营的 Warden，观察它们是否能互打
预期：不同阵营敌对 → 可以互打（原版 Warden 不打 Warden）

---

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 玩家无法攻击 | 阵营为 neutral | 执行 `/bettereve faction join <阵营>` |
| 指令不完整 | 缺少自动补全 | 按 Tab 键看可用参数 |
| 剧本未加载 | 未执行 scenario load | 执行 `/bettereve scenario load default` |
| 生物不动 | 无 Goal 区域 | 加载含 goals 的剧本 + `goal visualize` |
| 建筑不生成 | 缺少模板或 Level 参数 | PhaseSpawnBasesEvent 需 Level 非 null |
