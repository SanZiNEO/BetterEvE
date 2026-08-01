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

### 兵线推进（Waypoint）
- XZ 矩形柱体区域定义兵线路径点
- 粒子柱可视化显示区域位置
- 阵营实体自动沿路径推进，战斗优先于赶路

## 技术说明

- 所有反射调用已移除
- 无 `@Overwrite` / `@Redirect` Mixin（全部使用 `@Inject` + Mixin Accessor）
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
│   │   │   ├── counter/     # 死亡计数持久化
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
└── README.md
```
