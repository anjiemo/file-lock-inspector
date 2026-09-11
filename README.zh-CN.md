# File Lock Inspector

[English](README.md) | 简体中文

File Lock Inspector（文件占用诊断）是一个 IntelliJ Platform 插件，用于从 Gradle 构建错误和手动选择的文件路径诊断 Windows 文件占用。插件列出检测到的进程，在用户确认并重新核实进程身份后，支持结束所选进程。

## 运行环境

当前版本为 {{VERSION}}，运行于 Windows x64 和 Android Studio 261–262。插件内置 Windows 扫描程序，无需安装 PowerToys。

## 安装

通过 **Settings > Plugins > 齿轮菜单 > Install Plugin from Disk** 安装 `build/distributions/file-lock-inspector-{{VERSION}}.zip`，然后重启 IDE。执行 `gradlew renderDocumentation` 后，带版本号的文档会生成到 `build/rendered-docs/`。

## 使用

点击主工具栏搜索按钮前的“锁与使用者”图标，即可打开诊断窗口；悬浮提示为“查看文件占用”。

打开 **Tools > 查看文件占用**，选择文件或粘贴绝对路径，刷新查看结果。识别到的 Gradle 共享冲突错误也会提供诊断链接。结束前需明确选择进程。“报错时快照”在后台捕获结果可用时自动显示，仅供查看，不能用于结束操作。

点击取消图标可取消当前扫描，已返回的结果会保留。选中进程后显示详情，支持多选查看；结束操作需要确认并重新核实身份。

在 **Settings > Tools > File Lock Inspector** 中选择“跟随 IDE、中文、英文”，默认跟随 IDE。

点击进程详情标题旁的复制图标可复制全部已显示详情；右键单条信息可复制该条或所选文本，自动换行的路径会完整复制。

点击文件工具栏中的“清空结果”可重置列表、详情和状态，保留文件路径。扫描及进程操作期间按钮不可用，项目保存的历史记录不会删除。

## 文档

- [功能说明](docs/zh-CN/requirements.md)
- [架构设计](docs/zh-CN/architecture.md)
- [运行环境](docs/zh-CN/compatibility.md)
- [操作与状态](docs/zh-CN/troubleshooting.md)
- [构建与测试](docs/zh-CN/build.md)
- [原生协议](docs/zh-CN/protocol.md)
- [第三方说明](third-party-notices/README.zh-CN.md)

## 目录结构

```text
file-lock-inspector/
  plugin/                 公共界面、诊断流程及 IDEA Gradle 接入
  android-studio/         Android Studio 专用接入
  native/windows/         Windows 扫描与进程操作
  docs/en/                英文文档
  docs/zh-CN/             简体中文文档
  third-party-notices/    来源、版权和许可证说明
```

## 许可证

[Apache License 2.0](LICENSE)。
