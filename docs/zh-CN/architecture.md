# 实现架构

[English](../en/architecture.md) | 简体中文

## 模块

| 目录 | 职责 |
| --- | --- |
| `plugin/` | Kotlin 界面、语言设置、错误识别、历史记录、辅助程序通信和通用 Gradle 接入 |
| `android-studio/` | Android Studio 构建错误诊断链接 |
| `native/windows/` | C++ 文件扫描与进程操作 |

## IDE 接入

AS Provider 实现 `GradleErrorQuickFixProvider`，注册到 `com.android.tools.idea.gradle.errorQuickFixProvider`，向识别到的构建错误追加诊断入口。通用 Gradle 路径使用 `org.jetbrains.plugins.gradle.issueChecker` 和 `BuildIssueQuickFix`；安装 Android 插件时停用通用检查器。

可选插件描述文件分别声明两条接入路径。手动诊断通过主工具栏、Tools 菜单和项目视图右键菜单提供。

## 扫描与通信

Kotlin 客户端将内置辅助程序提取到 IDE 系统目录下按 SHA-256 区分的目录，通过 `ProcessBuilder` 参数列表启动。辅助程序以 UTF-8 逐行 JSON 返回数据，协议见[原生协议](protocol.md)。

原生扫描枚举系统文件句柄和已加载模块，以卷序列号与 128 位文件 ID 匹配目标，句柄通过作用域自动释放。父进程控制取消和 30 秒超时，并保留此前返回的结果。扫描不是原子快照，权限不足等跳过检查通过状态和计数表示。

识别构建错误后的后台捕获最多同时执行两次，相同文件的近期记录在 60 秒内去重，项目历史缓存最多保存 20 个文件的结果。项目关闭时取消捕获并清空缓存。

## 界面与操作

后台结果切换到 IDE 事件线程后更新表格。扫描完成时根据 PID、启动时间和文件 ID 保留有效选择，更新表格期间合并详情刷新，详情内容相同时保持原有文本选择。

详情支持全量复制和单条信息复制。清空操作只重置弹窗状态。图标按钮使用 IDE 控件、缩放尺寸和本地化提示，鼠标点击保留原焦点，键盘导航仍可使用。

结束操作重新检查进程身份、文件身份和当前占用，使用同一进程句柄验证创建时间并执行终止，等待最多五秒确认退出，再扫描文件。原构建重试动作由 IDE 提供，用户单独触发。

## 语言与分发

语言选择保存在 IDE 级 `file-lock-inspector.xml` 中，通过 IDE 实际语言和显式设置解析中英文资源。原生状态码由插件转换为本地化文本。

插件 ZIP 包含 Kotlin 实现、Windows x64 辅助程序、明暗主题图标和许可证。项目采用 [Apache License 2.0](../../LICENSE)，组件来源见[第三方说明](../../third-party-notices/README.zh-CN.md)。
