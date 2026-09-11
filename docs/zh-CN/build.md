# 构建与测试

[English](../en/build.md) | 简体中文

## 前提

- Windows x64 和 JDK 25，Android Studio 自带可用 JBR。
- 本地 Android Studio 261 安装，作为最低 SDK 目标。发布产物使用 261 编译。
- Zig 0.14.1，用于构建静态链接的原生辅助程序；将 `ZIG_EXE` 指向 `zig.exe`。

从[官方发布地址](https://ziglang.org/download/0.14.1/zig-x86_64-windows-0.14.1.zip)下载 Zig，SHA-256 为 `554f5378228923ffd558eac35e21af020c73789d87afeabf4bfd16f2e6feed2c`。未设置 `ZIG_EXE` 时，构建也会检查 `%LOCALAPPDATA%\FileLockInspector\toolchains\zig-x86_64-windows-0.14.1\zig.exe`。

## 命令

在仓库根目录使用 PowerShell：

```powershell
$env:IDE_HOME = 'D:\ProgramFiles\Android\StudioApps\Android Studio 4'
$env:JAVA_HOME = Join-Path $env:IDE_HOME 'jbr'
$env:ZIG_EXE = 'C:\toolchains\zig-x86_64-windows-0.14.1\zig.exe'
.\gradlew.bat test buildPlugin
powershell.exe -NoProfile -ExecutionPolicy Bypass -File native/windows/tests/integration.ps1
```

也可以用 `-PidePath=...` 替代 `IDE_HOME`。Maven Central 不可访问时，显式指定所选镜像，例如 `-PmavenMirror=https://maven.aliyun.com/repository/central`；默认不启用镜像。

插件 ZIP 输出到 `build/distributions/`。在 IDE 的 **Settings > Plugins > 齿轮菜单 > Install Plugin from Disk** 中安装。使用 `gradlew.bat runIde` 可启动独立开发 IDE，使用构建沙箱而非日常配置。

原生集成测试在 `build/native-tests/` 中创建自己的文件占用进程，验证识别和选择性结束，最后仅回收这些测试进程。测试目标均为生成的测试文件。

## 版本与文档

构建配置按职责组织：

| 文件 | 内容 |
| --- | --- |
| `version.properties` | 插件和原生辅助程序共用的版本号 |
| `config/plugin.properties` | 插件 ID、名称、Maven 分组及作者信息 |
| `config/compatibility.properties` | 支持的 IDE 构建版本范围 |
| `config/description.en.html` | 英文插件介绍 |
| `config/description.zh-CN.html` | 简体中文插件介绍 |
| `gradle/documentation.gradle.kts` | 文档渲染和压缩包任务 |
| `gradle/native.gradle.kts` | 原生辅助程序编译及资源打包 |
| `build.gradle.kts` | SDK、依赖、编译、测试和配置组装 |
| `plugin/src/main/resources/META-INF/plugin.xml` | 平台依赖、扩展点和操作入口 |

构建时从上述配置文件读取发布资料，并注入安装包中的 `plugin.xml`。两种语言的介绍合并为双语描述，在 IDE 和插件市场中同时展示。

`version.properties` 统一提供插件和原生辅助程序的版本号。`gradlew.bat renderDocumentation` 替换 Markdown 中的 `{{VERSION}}`，在 `build/rendered-docs/` 生成完整文档目录，包含许可证和第三方说明。每次生成都会清理失效的旧文件。

`gradlew.bat buildPlugin` 同时生成文档压缩包 `build/distributions/file-lock-inspector-docs-{{VERSION}}.zip`。打开其中的 README 可浏览已填入版本号的文档。仓库 Markdown 是源模板，GitHub 不会计算其中的占位符。

## IDE 冒烟检查

在相同环境下执行 `gradlew.bat -PideSmoke runIde`，通过独立 AS 沙箱加载仅用于测试的应用入口，检查工具栏和 Provider 注册、原生扫描、取消、选择保持、详情复制、清空、控件状态和双语弹窗渲染，将结果及 PNG 写入 `build/ide-smoke/`，然后退出。打包时不使用 `-PideSmoke`，`buildPlugin` 会拒绝此组合。
