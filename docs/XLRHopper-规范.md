# XLRHopper 功能规范文档

| 项目 | 说明 |
|------|------|
| 插件名称 | XLRHopper |
| 版本 | 1.3.0 |
| 作者 | Shan |
| API | Spigot/Paper 1.21.1 |
| 主类 | `xlingran.Shan` |
| 开发分支 | `XLRHopper` |
| 文案与 GUI 布局 | **`Gui.yml`**（界面与悬浮开关按钮）；**`Message.yml`**（仅聊天提示）；**悬浮 Display 四行**在 Java 硬编码 |
| 模板业务数据 | **`shan.db`（SQLite）**；不再使用 `data.yml` |

---

## 1. 概述

XLRHopper 为高级漏斗传输插件。玩家可创建**过滤模板**，在模板中配置物品类型、显示名称、Lore 等规则；**启用**某一模板后，新放置的漏斗按该模板过滤进入物品；模板**关闭**时，放置与传输行为与**原版漏斗**一致。

---

## 2. 指令与权限

| 指令 | 权限节点 | 说明 |
|------|----------|------|
| `/xlrhopper create mode <名称>` | `xlrhopper.create.mode` | 创建名为 `<名称>` 的模板（默认启用该模板、黑名单），并打开「模板设置」 |
| `/xlrhopper edit mode <名称>` | `xlrhopper.edit.mode` | 编辑已有模板，打开「模板设置」 |
| `/xlrhopper mode` | `xlrhopper.mode` | 打开「漏斗模板」列表 GUI |
| `/xlrhopper give <玩家\|%player%> <等级ID> [数量]` | `xlrhopper.give` | 给予指定等级的漏斗物品（数量缺省 1）；`%player%` 表示执行者本人（仅玩家执行时） |
| `/xlrhopper reload` | `xlrhopper.admin` | 重载 `Gui.yml` + `Message.yml` + `Update.yml`、从 `shan.db` 重读模板、异步重登记已加载区块漏斗（玩家与控制台均可） |

- 根命令 `xlrhopper` 在 `plugin.yml` 注册；子命令在代码内解析。已移除的 `box` / `create box` 子命令会提示新用法。
- 各子命令在代码内分别校验权限。
- 无权限时发送对应拒绝提示（硬编码）。

---

## 3. 配置文件

| 文件 | 路径 | 用途 |
|------|------|------|
| `config.yml` | `plugins/XLRHopper/config.yml` | 插件配置占位（可为空） |
| `Gui.yml` | `plugins/XLRHopper/Gui.yml` | GUI 标题、按钮材质/槽位/Lore、附魔中文表 |
| `Message.yml` | `plugins/XLRHopper/Message.yml` | 聊天栏提示（`Messages.*`） |
| `shan.db` | `plugins/XLRHopper/shan.db` | **模板业务数据**（SQLite） |
| `Update.yml` | `plugins/XLRHopper/Update.yml` | 漏斗等级：`default` 服务器默认 + `levels.<id>`（`transfer-tick`、`max-item`、`name`、`Lore`） |

### 3.1 Gui.yml 要点

- 由 JAR 内预置文件 **`saveResource("Gui.yml", false)`** 释放；**不**用代码生成 YAML；已存在文件不会被覆盖。
- 占位符：`%Template%`、`%modename%`、`%toggle%`、`%filtermode%`、`%Durability%`、`%Enchant%` 等；`toggle` / `filtermode` 在 YAML 根节点定义。
- **仅** `Auto-Crafting`、`Filter-Item`、`Auto-Furnace` 可配置 **`rows`（≥1，建议 ≤6）**；其余界面行数在 `GuiConfig` 中硬编码（3/5/6 行等）。
- 附魔显示名：key 为 **registry 小写**（如 `fire_protection`）；`EnchantNameTable` 委托 `GuiConfig` 查询。
- 修改后执行 `/xlrhopper reload` 生效。
- **`HopperSetting.FloatOverlay`**（绿宝石，默认 slot 14）：仅悬浮**开关**按钮的名称/Lore（`%toggle%`）；**无** 世界上空四行 Display 布局或文案配置。

### 3.2 Message.yml 要点

- JAR 预置 **`saveResource("Message.yml", false)`**；根节点 `Messages`，key 如 `reload-success`、`no-template`、`enchant-prompt` 等。
- 支持 `%Enchant%` 等占位符（如 `enchant-cleared`）。
- `/xlrhopper reload` 与 `Gui.yml` 一并热重载。
- **仅** `sendMessage` 类聊天提示；**不包含** 漏斗上方悬浮 Display 文案、`overlay-*` / `hover-*` 键；`MessageConfig` **不参与** 构建悬浮实体。

### 3.3 Update.yml 要点

- JAR 预置 **`saveResource("Update.yml", false)`**；`UpdateConfig` 加载并校验：`transfer-tick` ≥ 8 且为 8 的倍数，`max-item` ≥ 1。
- **`default`**：无 `hopper-level` PDC 的漏斗使用此段参数。
- **`levels.<id>`**：唯一 ID（如 `iron`）；`/xlrhopper give` 与物品 PDC / 方块 PDC `hopper-level` 引用同一 ID。
- 全局 **8 tick** 定时器不变；每 lane 的 `transfer-tick` 为门控间隔（累计达阈值才执行一步自动化）；`max-item` 限制单步反向搬运与 `InventoryMoveItem` 单次数量。

### 3.4 shan.db

- 表结构由 `ShanDatabase` 初始化；读写经 `TemplateRepository`（异步加载、防抖保存、关服 `flushSync`）。
- 模板字段：白名单、filter-items、自动合成/熔炼、附魔过滤、耐久阈值等。
- `/xlrhopper reload` 会 **重读数据库**（不先 save），便于外部工具改库后热重载。
- **1.2 遗留 `data.yml` / `data.yml.bak` 可安全删除**；插件不再读取或生成。

### 3.5 主线程 / 异步边界

| 主线程 | 异步 |
|--------|------|
| 库存读写、物品转移、8 tick 单步自动化 | SQLite 读写、`Gui.yml` 解析、区块漏斗坐标收集 |
| PDC / GUI / `workQueue` 入队出队 | `reload` 中 DB + Gui 重载；`reindexLoadedChunks` 扫坐标后主线程登记 |

---

## 4. GUI 规范

所有界面通过 `InventoryHolder` 识别类型，**不依赖**窗口标题字符串匹配。标题、按钮材质/槽位/Lore 默认来自 **`Gui.yml`**（`GuiConfig` 加载）；行为逻辑仍在 `Gui.java`。

### 4.0 GUI 安全（全局）

除 **「过滤的物品」「自动合成」「自动熔炼」** 外，所有 GUI 必须实现：

1. **物品点击限制**：禁止从界面取出物品（含 Shift、数字键、双击等）；仅执行插件定义的左/右键逻辑；`InventoryClickEvent` / `InventoryDragEvent` 默认取消。
2. **鼠标点击速度限制**：每玩家点击冷却（建议硬编码 **200ms**），过快连点忽略，防止刷物品或重复触发。
3. **「过滤的物品」例外**：**不** 做防取出与点击冷却（或仅可选轻量防抖）；玩家可 **自由放入、拿出、堆叠**。

实现建议：统一在 `Gui` 或 `GuiClickGuard` 中处理；`PlayerGuiSession` 记录 `lastClickMillis`。

**「过滤的物品」关闭时去重退回**（`FilterItem` / `Gui` 关闭逻辑）：

- 按 `Material` 统计 GUI 内所有物品。
- 每种材质在过滤模板中 **只保留 1 条**。
- 该材质总数量 **> 1** 时（单格 `amount>1` 或多格各放 1），超出部分 **全部退回** 玩家背包（`Inventory#addItem`）。
- 例：一格 `钻石×2` → 记录钻石 + 退回 `钻石×1`；两格各 `钻石×1` → 记录钻石 + 退回 `钻石×1`。

### 4.1 漏斗模板（3 行 × 9 列）

- **标题**：`Gui.yml` → `HopperTemplateList.name`（默认 `&6漏斗模板`）
- **打开**：`/xlrhopper mode`

| 条件 | 显示 |
|------|------|
| 无任何模板 | 27 格**全部为空**，不填充玻璃或任何物品 |
| 有模板 | 从 **第 1 行第 1 格（slot 0）** 起顺序放置命名牌，最多 27 个；未使用格**保持空** |

**命名牌**

- 显示名：`&a<模板名>`
- **启用**：附魔光效（`UNBREAKING` + `HIDE_ENCHANTS`）
- **未启用**：无附魔

**Lore（`Gui.yml`）**

- 启用/未启用：`SetTemplate` / `Template` 节点 + `%toggle%` 占位符
- 建议附加：`&7左键 开/关` · `&7右键 编辑`（可在 YAML 中改）

**交互**

| 操作 | 行为 |
|------|------|
| 左键 | 切换该模板启用/关闭；**每名玩家同时仅一个「开」**（开 A 则关其它） |
| 右键 | 进入「模板设置」编辑该模板 |

- 无分页；超过 7 个模板时仅显示前 7 个。

### 4.2 模板设置（5 行 × 9 列 = 45 格）

- **标题**：`TemplateSet.name` + `%modename%`（默认 `&e模板设置: &b<模板名称>`）
- **打开**：创建模板后 / 列表右键 / 编辑流程

| 配置键 | 默认 slot | 行为 |
|--------|-----------|------|
| `TemplateSet.AutoCrafting` | 10 | **左键** 开/关；**右键** 打开「自动合成」（`Auto-Crafting.rows` 决定格数） |
| `TemplateSet.Break` | 12 | 左/右键切换模板级自动销毁 |
| `TemplateSet.FilterItem` | 14 | 打开「过滤的物品」 |
| `TemplateSet.FilterMode` | 16 | 左/右键切换模板白/黑名单 |
| `TemplateSet.FilterEnchant` | 28 | 打开「过滤附魔属性」 |
| `TemplateSet.Durability` | 30 | 关 GUI + 聊天输入耐久阈值 |
| `TemplateSet.AutoFurnace` | 32 | **左键** 开/关；**右键** 打开「自动熔炼」 |
| `TemplateSet.Batch` | 34 | 批量模式；`xlrquit` 退出 |
| `TemplateSet.Filler` | 其余 | 占位玻璃，不可取出 |

非法 `slot` 会打 warning 并回退默认 slot。

**红石块 Lore 示例**

- `&7仅作用于过滤物品`
- `当前模式: &a白名单模式` 或 `&c黑名单模式`

- 关闭 GUI：防抖保存至 `shan.db`；**不改变**列表中的启用开关。

### 4.3 过滤的物品（默认可配 6 行 × 9 列）

- **标题**：`Filter-Item.name`（默认 `&6过滤的物品`）
- **行数**：`Filter-Item.rows`（≥1）
- **不适用** §4.0（可正常放入、拿出、堆叠）
- 每条规则为 **样板 ItemStack**（数量 1）：样板上**已设置的**显示名、Lore、附魔、CustomModelData、**药水基础类型（PotionType）/自定义药水效果** 等才参与匹配；未设置药水类型时不会把所有药水视为同一种
- **关闭时**：按样板规则去重，每种逻辑规则仅保留 1 个；多余物品退回背包；**清空 GUI 格子**（防止关闭界面时复制物品）
- 旧版 `materials` 列表在加载时自动迁移为仅材质的样板
- 无分页

### 4.7 自动合成（行数可配）

- **标题**：`Auto-Crafting.name`
- **行数**：`Auto-Crafting.rows`
- 规则同「过滤的物品」：可自由存取；关闭时按 `FilterItemMatcher.sameRule` 去重并退回重复项
- 样板为**合成结果**（如工作台）；插件在漏斗 5 格内匹配 `CraftingRecipe` 后扣除原料并产出 1 个结果
- 未凑齐配方的原料槽位由 `HopperReservation` 预留，不参与反向 push/pull

### 4.8 自动熔炼（行数可配）

- **标题**：`Auto-Furnace.name`
- **行数**：`Auto-Furnace.rows`
- 规则同「过滤的物品」
- 样板为**熔炼产出**（如烤马铃薯）；由 `FurnaceRecipe` / `CookingRecipe` 反查输入
- 每漏斗同时仅 1 个熔炼 job；**100 tick（5 秒）** 后产出 1 个放回漏斗；熔炼中输入预留

### 4.4 过滤附魔属性（6 行 × 9 列）

- **标题**：`&6过滤附魔属性`
- 未配置：普通 **书**（`BOOK`），显示名来自 `Gui.yml` 附魔表（[`EnchantNameTable`](src/main/xlingran/EnchantNameTable.java) 委托 `GuiConfig`）
- 已配置：**附魔书**（`ENCHANTED_BOOK`，隐藏等级）+ Lore：`&a当前过滤: &e<附魔名> <等级>`
- **左键** 某附魔书 → 关 GUI → 聊天输入最低等级（纯数字）→ 返回本 GUI
- 已配置附魔：**右键** 清除该条过滤并刷新 GUI（保存 `shan.db`）
- 附魔超过 54 个时仅显示前 54 个

### 4.5 漏斗设置（3 行硬编码）

- **标题**：`HopperSetting.name`（默认 `&e漏斗设置`）
- **打开**：对已套用模板（PDC 含 `template` + `owner`）的漏斗 **Shift + 左键**（手持物品亦可）；无模板时提示（`Message.yml` → `no-template`），不打开 GUI
- **`HopperSetting.Redstone`**（默认 slot 10）→ PDC `redstone-list-toggle`
- **`HopperSetting.Reverse`**（默认 slot 12）→ PDC `reverse-suction`
- **`HopperSetting.FloatOverlay`**（默认 slot 14，绿宝石）→ PDC `hover-display`（默认 **false**）；左/右键切换后**同一 tick、主线程**立即 `show` / `hide` 悬浮实体，再刷新本 GUI 的 `%toggle%`（不依赖关 GUI 或重进世界）
- **`HopperSetting.Filler`**：占位玻璃

### 4.6 漏斗上方悬浮 Display（硬编码）

- 实现：`display.HopperOverlayDisplayService` + `display.HopperOverlayListener`；**不**进入 `HopperTickService`（属 P0 事件驱动，与 8 tick `workQueue` 排水无关）。
- 实体：`ItemDisplay`（`ItemDisplayTransform.GUI`，独立 scale；漏斗 5 槽非空物品各 1 个、无数量）+ 四行 `TextDisplay`（模板名、白/黑名单模式、附魔过滤条数、最低耐久）。
- 文案与 Y 偏移均在 Java 常量中拼接；**不读** `Message.yml` / `Gui.yml` 悬浮行配置。
- PDC：`hover-display`（开关）、`overlay-marker`（实体归属标记）；套模板/初始化时 `hover-display=false`。
- **刷新事件**（仅 `hover-display=true`）：`InventoryMoveItemEvent`、`InventoryPickupItemEvent`、漏斗 GUI 的 `InventoryClickEvent` / `InventoryDragEvent` / `InventoryCloseEvent`；`ChunkLoad` 恢复 `show`；破坏/爆炸/卸载 `hide`；`onDisable` → `hideAll`。

---

## 5. 聊天输入

| 场景 | 提示 | 处理后 |
|------|------|--------|
| 「耐久过滤」 | 输入数字剩余耐久阈值 | 写入 `durability-threshold`；非纯数字提示错误 |
| 「附魔过滤」选附魔后 | 输入附魔最低等级 | 写入 `enchant-filters`；非纯数字提示错误 |
| 「批量设置」 | `xlrquit` | 退出批量模式并返回「模板设置」 |
| 聊天输入中 | 发送 `xlrquit` | 取消当前输入并返回对应 GUI |

---

## 6. 过滤逻辑

### 6.1 总公式

```
允许进入漏斗 = passItem(样板) ∧ passDurability ∧ passEnchant
```

**有效白/黑名单**（仅影响 passItem）：

- 方块 PDC `redstone-list-toggle` 关闭 → 使用模板 `whitelist`（模板设置红石块切换）
- 方块 PDC `redstone-list-toggle` 开启 → 漏斗方块 **有充能** = 白名单，**无充能** = 黑名单

任一维度不通过 → 取消对应进入路径的事件。

**进入路径（事件不同，逻辑相同）**

| 方式 | 主要事件 |
|------|----------|
| 容器 → 漏斗 | `InventoryMoveItemEvent` |
| 地上物品实体 → 漏斗吸入 | `InventoryPickupItemEvent`（含死亡掉落、Q/背包丢弃后落地） |
| 玩家丢弃（Q / 背包丢出 / 死亡掉落等） | 不取消丢弃；物品正常落地后由 `InventoryPickupItemEvent` 决定是否吸入 |
| 打开漏斗 GUI 放入 | `InventoryClickEvent` / `InventoryDragEvent` |

- 漏斗 PDC：`template`、`owner`、`redstone-list-toggle`、`reverse-suction`、`hover-display`（默认 false）、`hopper-level`（等级 ID，来自 give 物品放置）；过滤样板/附魔/耐久仍来自模板，修改模板后绑定漏斗立即生效。

### 6.2 样板物品（FilterItemMatcher）— 受有效白/黑名单影响

| 模式 | 规则列表非空 | 规则列表为空 |
|------|----------------|----------------|
| **白名单** | 须匹配任一样板 | **不按样板限制**（全部允许） |
| **黑名单** | 不得匹配任一样板 | **不按样板限制**（全部允许） |

### 6.3 反向吸取（reverse-suction）

- 单漏斗 PDC `reverse-suction` 为 true 时，`HopperReverseHandler` **取消原版四向冲突移动**；并 **`scheduleEvaluate`** 由事件侧决定是否入队，**不在** handler 内 `syncHopper` 或全量登记
- 每 **8 game tick**、每个**已在 workQueue 中**的 lane **最多 1 步**反向搬运（同上 push/pull 规则）
- `HopperTransferReverse` 直接读写方块 `Container` 库存，扣减前后校验；写失败退回目标容器（满则 `dropItemNaturally`）

### 6.3.1 事件驱动 + 8 tick 管线（1.3.0）

**阶段 A（事件，`HopperLaneListener` / `HopperWorkEvaluator`）**

- `ChunkLoad`、`BlockPlace`、批量套模板、库存移动、邻居变化等：登记 `HopperLane`、构建 **`FilterSnapshot`**（一次 resolve）、更新自动化标志、`workQueue.offer/remove`
- **禁止**在 8 tick 内做登记、resolve、全服盲扫、`syncHopper`

**阶段 B（每 8 tick，`HopperTickService.tickAll`）**

- 仅对 `HopperLaneRegistry.workQueueSnapshot(256)` 中的 lane 执行：
  1. `HopperAutoSmeltService.tick`
  2. `HopperAutoCraftService.tryCraft`
  3. `HopperTransferReverse.transferStep`（仅 `reverse-suction`）
- 无剩余工作或目标满缓存则 **出队**；否则保留待下周期

合成在传输前执行；熔炼/合成预留槽位不参与反向搬运。

### 6.3.2 自动销毁（auto-destroy）

- 模板开启后，不符合过滤规则的 **地上吸入** 会被取消并移除物品实体

### 6.4 耐久（FilterDurability）

- 未设置 `durability-threshold` → 不按耐久限制。
- 已设置 → 剩余耐久 `maxDamage - damage` **严格小于** 阈值则拒绝；无耐久物品视为通过。

### 6.5 附魔（FilterEnchant）

- 每条规则：附魔 + 最低等级；物品**含有**该附魔且等级 **< 最低等级** → 拒绝；不含该附魔 → 该条通过。
- 多条规则之间 AND。

---

## 7. 漏斗放置与 PDC

| 玩家状态 | 放置漏斗时 |
|----------|------------|
| **有启用的模板** | 写入 PDC：`xlrhopper:template`（模板名）、`xlrhopper:owner`（UUID） |
| **无启用模板** | **不写** PDC；行为与原版一致 |

- 已放置且带 PDC 的漏斗：按**写入 PDC 时**绑定的模板名继续过滤；**不因之后在列表里启用/切换其它模板而改写旧方块**。
- 例：漏斗已套用「模板1」后，再在列表启用「模板2」→ 该漏斗 PDC 仍为 `模板1`，过滤规则仍读「模板1」的配置。
- 仅**新放置**的漏斗（或**批量设置**左/右键点击）会写入/覆盖 PDC。

---

## 8. 类职责

| 类 / 包 | 职责 |
|---------|------|
| `Shan` | 插件入口；`saveResource(Gui.yml)`；异步 DB；`reload()` |
| `gui.GuiConfig` | 解析 `Gui.yml`、占位符、slot 校验、附魔显示名 |
| `gui.MessageConfig` | 解析 `Message.yml` 聊天提示 |
| `gui.UpdateConfig` | 解析 `Update.yml` 漏斗等级与传输参数 |
| `HopperLevelItems` / `HopperLevelResolver` | 等级物品、方块 PDC、lane 参数解析 |
| `Gui` | GUI 构建与事件（读 `GuiConfig`） |
| `storage.ShanDatabase` / `TemplateRepository` | SQLite 与迁移、异步加载/防抖保存 |
| `core.HopperLane` / `FilterSnapshot` | 单漏斗运行态与模板快照缓存 |
| `core.HopperLaneRegistry` | `active` 登记 + **`workQueue`** |
| `core.HopperWorkEvaluator` | 三条件入队/出队 |
| `core.HopperLaneListener` | 区块/放置/库存事件 → 登记与 evaluate |
| `HopperTickService` | **仅** workQueue 排水：熔炼 → 合成 → 反向 |
| `HopperReverseHandler` | 反向四向取消 + `scheduleEvaluate` |
| `feature.HopperFeature` | 自动化扩展接口（预留） |
| `HopperTemplate` / `HopperTemplateManager` | 模板模型与内存集合 |
| `FilterItem` / `FilterItemMatcher` 等 | 过滤维度 |
| `HopperAutoCraftService` / `HopperAutoSmeltService` | 漏斗内合成与熔炼 |
| `HopperCommand` | 指令（含 `reload`） |
| `display.HopperOverlayDisplayService` | 悬浮 TextDisplay/ItemDisplay 生成与刷新（硬编码文案） |
| `display.HopperOverlayListener` | 库存/区块/破坏事件驱动悬浮刷新与清理 |
| `HopperSettingsListener` | Shift+左键打开漏斗设置 |

---

## 9. 首版不包含

- `config.yml` 配置 GUI（使用 **`Gui.yml`**）
- 模板列表 / 规则列表分页
- 修改已放置漏斗的绑定模板
- 漏斗破坏转移 PDC、跨世界
- 除 `xlrhopper.create.mode`、`xlrhopper.mode` 外的细粒度权限

---

## 10. 验收要点

1. 无模板时「漏斗模板」界面全空。
2. 左键开关：附魔 + `&a开` / 无附魔 + `&c关`；同时仅一个开。
3. 全部关闭时放置漏斗无过滤；启用后放置有 PDC 且过滤生效。
4. 白/黑名单**仅影响材质**；名称/Lore **固定黑名单**（命中规则即拒绝，不可切换）。
5. 重启后 `shan.db` 与 GUI 数据一致。
6. `/xlrhopper reload`：无 `xlrhopper.admin` 拒绝；改 `Gui.yml` / `Message.yml` 后重载界面与聊天提示变化。
7. `Filter-Item.rows: 2` 重载后为 18 格且可存取。
8. 模板设置等 GUI：无法取出玻璃/命名牌；连点被冷却限制。
9. 过滤物品 GUI：可拿可取；关闭时重复材质退回（堆叠×2 或多格各 1 均退回 1 个）。
10. 空漏斗 / 无 pending：不在 8 tick 盲扫；有货或邻居变化事件入队后才处理。
11. 漏斗设置内点「悬浮开关」：不开关重进世界，悬浮**立即**出现/消失；`Message.yml` 无 `overlay-*` 键；`Gui.yml` 仅有 `FloatOverlay` 开关项。
12. 打开漏斗设置：**Shift+左键**；默认无悬浮直至开启 `hover-display`。

---

*文档版本：与实现同步，适用于 XLRHopper 1.3.0（Gui.yml、Message.yml、shan.db、事件驱动 workQueue、悬浮 Display、reload）。*
