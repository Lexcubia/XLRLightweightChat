# XLRHopper

> **主要开发分支**：`XLRHopper`（本分支）。克隆 / Codespace / PR 请以此分支为准。  
> 聊天插件见分支 `XLRLightweightChat`。详见 [docs/主要开发分支.md](docs/主要开发分支.md)。

高级漏斗传输插件（Spigot / Paper **1.21.1**）

- **作者**: Shan  
- **版本**: 1.3.0  
- **主类**: `xlingran.Shan`

## 构建

```bash
mvn clean package
```

产物：`target/XLRHopper-1.3.0.jar`

## 安装

将 JAR 放入服务端 `plugins/` 目录并重启（或热插拔后重载插件）。

首次运行会在 `plugins/XLRHopper/` 生成：

| 文件 | 说明 |
|------|------|
| `config.yml` | 插件占位配置（可为空） |
| `Gui.yml` | **GUI 标题、按钮槽位/Lore、附魔中文表**（由 JAR 内预置文件复制，不覆盖已有文件） |
| `Message.yml` | **聊天栏提示**（reload、批量模式、耐久/附魔输入等） |
| `shan.db` | **模板业务数据**（SQLite）；若存在旧版 `data.yml` 会一次性迁移并改名为 `data.yml.bak` |

## 配置与重载

- 修改 `Gui.yml`、`Message.yml` 或外部工具编辑 `shan.db` 后，执行 **`/xlrhopper reload`**（权限 **`xlrhopper.admin`**，玩家与控制台均可），无需整服 `/reload`。
- 存储类界面（自动合成 / 过滤的物品 / 自动熔炼）可在 `Gui.yml` 中配置 **`rows`（≥1）**；其余界面行数在代码中固定。

完整行为说明见 [docs/XLRHopper-规范.md](docs/XLRHopper-规范.md)。

## 架构要点（1.3.0）

- **P0**：登记漏斗、解析模板快照、入队/出队均在 **Bukkit 事件** 或异步任务中完成；**8 tick 定时器仅消费 `HopperWorkQueue`**，对队列内漏斗执行熔炼 → 合成 → 反向各至多一步。
- 不再在定时器内全服盲扫或每 tick `resolve` 模板。

## 开发

推荐使用 GitHub Codespace（仓库内已配置 `.devcontainer`）或本地 JDK 21 + Maven。

## 仓库与分支

- 远程仓库：`Lexcubia/XLRLightweightChat`（单仓多分支）
- **当前主要开发**：`XLRHopper`（本目录即漏斗插件根项目）
- **聊天插件维护**：`XLRLightweightChat` 分支
