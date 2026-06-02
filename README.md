# XLRHopper

> **主要开发分支**：`XLRHopper`（本分支）。克隆/ Codespace / PR 请以此分支为准。  
> 聊天插件见分支 `XLRLightweightChat`。详见 [docs/主要开发分支.md](docs/主要开发分支.md)。

高级漏斗传输插件（Spigot / Paper **1.21.1**）

- **作者**: Shan  
- **版本**: 1.0.0  
- **主类**: `xlingran.Shan`

## 构建

```bash
mvn clean package
```

产物：`target/XLRHopper-1.0.0.jar`

## 安装

将 JAR 放入服务端 `plugins/` 目录，重启服务器。首次运行会在 `plugins/XLRHopper/` 生成空的 `config.yml`。

## 开发

推荐使用 GitHub Codespace（仓库内已配置 `.devcontainer`）或本地 JDK 21 + Maven。

## 仓库与分支

- 远程仓库：`Lexcubia/XLRLightweightChat`（单仓多分支）
- **当前主要开发**：`XLRHopper`（本目录即漏斗插件根项目）
- **聊天插件维护**：`XLRLightweightChat` 分支
