# BetterEvE 模组配置文件说明

## 配置文件位置

BetterEvE 模组的配置文件位于：
`%游戏根目录%/config/bettereve-common.toml`

该配置文件对所有世界存档有效，修改后需要重新启动游戏才能生效。

## 配置文件结构

配置文件分为以下几个主要部分：

### 1. 阵营系统配置

```toml
# 全局默认规则：不同阵营是否敌对（true=敌对，false=和平）
faction.defaultHostile = true

# 自定义阵营列表（格式：ID=名称,颜色,是否启用，多个用;分隔，例：red=红队,red,true;blue=蓝队,blue,true）
faction.factions = "red=红队,red,true;blue=蓝队,blue,true"

# 自定义阵营关系（格式：阵营ID1:阵营ID2=关系，关系：hostile/friendly，多个用;分隔，例：red:blue=hostile;red:green=friendly）
faction.relations = "red:blue=hostile"
```

**配置说明：**
- `faction.defaultHostile`：全局默认规则，设置是否所有不同阵营的实体都是敌对的
- `faction.factions`：自定义阵营列表，每个阵营包含ID、名称、颜色和是否启用
- `faction.relations`：自定义阵营关系，优先级高于全局默认规则

### 2. 渲染系统配置

```toml
# 高亮描边线宽（满血状态，残血按比例缩小）
render.outlineWidth = 2.0

# 是否显示实体百分比血量（true=显示，false=隐藏）
render.showHealthPercent = true

# 血量文本相对名称标签的Y轴偏移（正数向上，负数向下）
render.healthTextOffsetY = 0.0
```

**配置说明：**
- `render.outlineWidth`：设置高亮描边的宽度，值越大描边越粗（范围：0.1-10.0）
- `render.showHealthPercent`：是否在实体头顶显示百分比血量
- `render.healthTextOffsetY`：血量文本相对于实体名称标签的垂直偏移（范围：-5.0-5.0）

### 3. 寻敌系统配置

```toml
# 全局默认寻敌范围（格），覆盖原版生物默认值
targeting.defaultFollowRange = 64.0

# 按实体类型自定义寻敌范围（格式：实体ID=距离，多个用;分隔，例：minecraft:zombie=80;minecraft:skeleton=70）
targeting.entityFollowRange = "minecraft:zombie=64;minecraft:skeleton=64"

# 按阵营自定义寻敌范围（格式：阵营ID=距离，多个用;分隔，例：red=70;blue=70）
targeting.factionFollowRange = "red=64;blue=64"
```

**配置说明：**
- `targeting.defaultFollowRange`：全局默认寻敌范围，适用于所有生物（范围：1.0-128.0格）
- `targeting.entityFollowRange`：按实体类型自定义寻敌范围，优先级高于全局默认值
- `targeting.factionFollowRange`：按阵营自定义寻敌范围，优先级最高

### 4. 计数器系统配置

```toml
# 是否启用计数器HUD显示（true=启用，false=关闭）
counter.hudEnabled = true

# HUD位置X轴（0=最左，1=最右，相对屏幕）
counter.hudPosX = 0.0

# HUD位置Y轴（0=最上，1=最下，相对屏幕）
counter.hudPosY = 0.0

# 是否启用TAB面板统计（true=启用，false=关闭）
counter.tabPanelEnabled = false

# TAB面板切换按键（支持MC按键名，例：KEY_TAB）
counter.tabPanelKey = "KEY_TAB"

# 是否统计实体类型明细（true=显示每种生物数量，false=仅总数量）
counter.countEntityType = true
```

**配置说明：**
- `counter.hudEnabled`：全局HUD显示开关
- `counter.hudPosX`和`counter.hudPosY`：HUD的位置，0表示屏幕左侧/顶部，1表示右侧/底部（范围：0.0-1.0）
- `counter.tabPanelEnabled`：是否替换原版TAB面板为BetterEvE的统计面板
- `counter.tabPanelKey`：TAB面板的切换按键
- `counter.countEntityType`：是否统计不同实体类型的详细死亡数据

### 5. 基地系统配置

```toml
# 基地默认血量
base.defaultHealth = 10000

# 基地描边线宽（普通实体的2倍为默认）
base.outlineWidth = 4.0

# 胜利播报时长（秒）
base.victoryAnnounceTime = 5

# 基地清空地图范围（以基地为中心的半径，格）
base.clearRadius = 50

# 是否仅敌对阵营可攻击基地（true=仅敌对能打，false=所有人能打）
base.onlyHostileAttack = true
```

**配置说明：**
- `base.defaultHealth`：基地（末影水晶）的默认血量（范围：100-100000）
- `base.outlineWidth`：基地高亮描边的宽度，值越大描边越粗（范围：0.1-20.0）
- `base.victoryAnnounceTime`：基地被摧毁后的胜利播报持续时间（范围：1-30秒）
- `base.clearRadius`：基地被摧毁后，清空地图内指定半径范围的所有实体（范围：10-200格）
- `base.onlyHostileAttack`：是否只有敌对阵营的实体可以攻击基地

### 6. 等级系统配置

```toml
# 每级生命值加成比例（%）
level.healthBonus = 20.0

# 每级攻击力加成比例（%）
level.attackBonus = 20.0

# 每级移动速度加成比例（%）
level.speedBonus = 20.0

# 等级上限
level.maxLevel = 10
```

**配置说明：**
- `level.healthBonus`：每级健康值加成比例（范围：0.0-100.0%）
- `level.attackBonus`：每级攻击力加成比例（范围：0.0-100.0%）
- `level.speedBonus`：每级移动速度加成比例（范围：0.0-100.0%）
- `level.maxLevel`：实体的最高等级上限（范围：1-100级）

### 7. 全局开关配置

```toml
# 全局HUD显示总开关（覆盖counter.hudEnabled）
global.hudEnabled = true

# 全局高亮描边总开关（覆盖render.outlineWidth）
global.outlineEnabled = true

# 全局等级系统总开关（覆盖level所有配置）
global.levelEnabled = true
```

**配置说明：**
- `global.hudEnabled`：全局HUD显示开关
- `global.outlineEnabled`：全局高亮描边开关
- `global.levelEnabled`：全局等级系统开关

## 游戏内配置页面

BetterEvE 模组已集成到 Forge 原生配置页面中，您可以通过以下步骤在游戏内直接配置：

1. 启动 Minecraft
2. 在主菜单点击「Mods」
3. 在模组列表中找到「BetterEvE」
4. 点击「Configure」按钮
5. 在配置页面中可以看到所有7大类配置项，按分组显示
6. 修改配置后点击「Done」保存
7. 重新启动游戏使配置生效

**配置页面特点：**
- 配置项按模块分组，页面显示更清晰
- 每个配置项有注释说明
- 输入超出范围的值会被自动限制（比如寻敌范围填0会自动改成1）
- 配置修改后保存到 `.minecraft/config/bettereve-common.toml` 文件

## 修改配置文件的方法

### 方法1：游戏内配置页面（推荐）

通过 Forge 原生配置页面进行配置，操作简单且有验证机制。

### 方法2：直接编辑配置文件

1. 打开游戏根目录下的 `config` 文件夹
2. 找到并打开 `bettereve-common.toml` 文件
3. 根据需要修改配置选项
4. 保存文件并重新启动游戏

## 注意事项

1. 配置文件的格式必须严格遵循TOML语法
2. 颜色值必须使用十六进制格式，如 #FF0000（红色）
3. 所有距离和位置值都是相对于游戏世界的单位
4. 修改配置后必须重新启动游戏才能生效
5. 不要随意删除或修改配置文件的结构，否则可能导致游戏崩溃

## 配置示例

以下是一个完整的配置文件示例：

```toml
# BetterEvE 模组配置文件

[faction]
# 全局默认规则：不同阵营是否敌对（true=敌对，false=和平）
defaultHostile = true
# 自定义阵营列表（格式：ID=名称,颜色,是否启用，多个用;分隔，例：red=红队,red,true;blue=蓝队,blue,true）
factions = "red=红队,red,true;blue=蓝队,blue,true"
# 自定义阵营关系（格式：阵营ID1:阵营ID2=关系，关系：hostile/friendly，多个用;分隔，例：red:blue=hostile;red:green=friendly）
relations = "red:blue=hostile"

[render]
# 高亮描边线宽（满血状态，残血按比例缩小）
outlineWidth = 3.0
# 是否显示实体百分比血量（true=显示，false=隐藏）
showHealthPercent = true
# 血量文本相对名称标签的Y轴偏移（正数向上，负数向下）
healthTextOffsetY = -0.3

[targeting]
# 全局默认寻敌范围（格），覆盖原版生物默认值
defaultFollowRange = 80.0
# 按实体类型自定义寻敌范围（格式：实体ID=距离，多个用;分隔，例：minecraft:zombie=80;minecraft:skeleton=70）
entityFollowRange = "minecraft:zombie=100.0;minecraft:skeleton=120.0;minecraft:creeper=70.0"
# 按阵营自定义寻敌范围（格式：阵营ID=距离，多个用;分隔，例：red=70;blue=70）
factionFollowRange = "red=90.0;blue=90.0;green=85.0"

[counter]
# 是否启用计数器HUD显示（true=启用，false=关闭）
hudEnabled = true
# HUD位置X轴（0=最左，1=最右，相对屏幕）
hudPosX = 0.85
# HUD位置Y轴（0=最上，1=最下，相对屏幕）
hudPosY = 0.1
# 是否启用TAB面板统计（true=启用，false=关闭）
tabPanelEnabled = true
# TAB面板切换按键（支持MC按键名，例：KEY_TAB）
tabPanelKey = "KEY_TAB"
# 是否统计实体类型明细（true=显示每种生物数量，false=仅总数量）
countEntityType = true

[base]
# 基地默认血量
defaultHealth = 15000
# 基地描边线宽（普通实体的2倍为默认）
outlineWidth = 5.0
# 胜利播报时长（秒）
victoryAnnounceTime = 15
# 基地清空地图范围（以基地为中心的半径，格）
clearRadius = 150
# 是否仅敌对阵营可攻击基地（true=仅敌对能打，false=所有人能打）
onlyHostileAttack = true

[level]
# 每级生命值加成比例（%）
healthBonus = 25.0
# 每级攻击力加成比例（%）
attackBonus = 25.0
# 每级移动速度加成比例（%）
speedBonus = 15.0
# 等级上限
maxLevel = 15

[global]
# 全局HUD显示总开关（覆盖counter.hudEnabled）
hudEnabled = true
# 全局高亮描边总开关（覆盖render.outlineWidth）
outlineEnabled = true
# 全局等级系统总开关（覆盖level所有配置）
levelEnabled = true
```

## 常见问题

### 1. 为什么修改配置后没有生效？

- 确保已经保存配置文件
- 确保已经重新启动游戏
- 检查配置文件的格式是否正确

### 2. 如何添加新阵营？

在 `faction.factions` 配置项中添加一个新的阵营项，格式为：`ID=名称,颜色,是否启用`

例如，添加一个黄色阵营：
```toml
faction.factions = "red=红队,red,true;blue=蓝队,blue,true;yellow=黄队,yellow,true"
```

### 3. 如何设置阵营关系？

在 `faction.relations` 配置项中添加一个新的关系项，格式为：`阵营ID1:阵营ID2=关系`

关系类型可以是：
- `hostile`：敌对关系
- `friendly`：友好关系

例如，设置黄方和红方为敌对关系：
```toml
faction.relations = "red:blue=hostile;yellow:red=hostile"
```

### 4. 如何调整高亮描边的宽度？

修改 `render.outlineWidth` 的值，值越大描边越粗。例如，设置为3.0会比默认的2.0更粗。

### 5. 如何修改HUD的位置？

修改 `counter.hudPosX` 和 `counter.hudPosY` 的值，0表示屏幕左侧/顶部，1表示右侧/底部。例如，设置为：
```toml
counter.hudPosX = 0.1
counter.hudPosY = 0.1
```

会将HUD显示在屏幕的左上角。

## 支持的实体类型

模组支持所有继承自LivingEntity的实体类型，包括：
- 原版生物（如僵尸、骷髅、苦力怕等）
- 第三方模组生物（只要继承自LivingEntity）
- 玩家（默认归属中立阵营）

## 支持的游戏版本

该配置文件适用于Minecraft Forge 1.19.2+版本的BetterEvE模组。