# XLRLightweightChat

面向 Spigot/Paper **1.21.1** 的多功能聊天插件：可配置聊天格式、十六进制渐变、称号仓库、聊天悬浮/点击交互、主手物品 `[item]` 展示。

| 项目 | 说明 |
|------|------|
| 版本 | 1.0.0 |
| Java | 21 |
| API | Spigot `1.21.1-R0.1-SNAPSHOT` |
| 主类 | `xlingran.Shan` |
| 默认分支 | `XLRLightweightChat` |

## 快速开始

1. 将构建产物 `XLRLightweightChat-1.0.0.jar` 放入服务端 `plugins/` 目录。
2. 启动或重载服务器，编辑 `plugins/XLRLightweightChat/config.yml` 与 `Gui.yml`。
3. 游戏内执行 `/xlrchat reload`（需权限 `xlr.admin.reload`）使配置生效。
4. 玩家使用 `/xlrchat cp` 打开称号仓库（需权限 `xlr.command.cp`）。

## 文档

完整配置与行为规范见：

- **[docs/配置与使用手册.md](docs/配置与使用手册.md)** — 命令、权限、占位符、`config.yml` / `Gui.yml` 字段说明、示例与 FAQ

## 构建

```bash
mvn clean package -DskipTests
```

产物路径：`target/XLRLightweightChat-1.0.0.jar`

GitHub Actions 工作流 `Build Plugin` 在推送至 `XLRLightweightChat` 分支时自动构建，并上传 JAR 构件。

## 仓库结构

```
├── config.yml          # 默认配置（打包进 JAR）
├── Gui.yml             # 称号仓库 GUI 配置
├── plugin.yml          # 插件元数据
├── src/main/xlingran/
│   ├── Shan.java       # 主类：聊天、命令、配置
│   ├── GuiManager.java # 称号仓库界面
│   └── Item.java       # 物品中英文名与 [item] 展示
└── docs/
    └── 配置与使用手册.md
```

## 许可证

请参阅仓库所有者提供的许可说明；未单独声明时以仓库政策为准。
