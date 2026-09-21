# 运行环境

[English](../en/compatibility.md) | 简体中文

当前插件版本为 {{VERSION}}，使用 IntelliJ Platform 252 SDK 编译，声明平台范围为 252 至 262.*。

| 项目 | 配置 |
| --- | --- |
| 运行系统 | Windows x64；本地验证环境为 Windows 11 x64 |
| Android Studio 验证版本 | AI-252.25557.131.2521.14432022、AI-261.26222.65.2614.16000733、AI-262.9437.185.2621.16297395 |
| 诊断目标 | 本地磁盘上的单个文件 |
| 构建接入 | 本地 Gradle 构建错误 |
| 扫描程序 | 插件内置 Windows x64 辅助程序 |
| 界面语言 | 跟随 IDE、简体中文、英文 |

诊断不需要 PowerToys、Python、独立 C++ 运行库或网络服务。辅助程序使用 Windows 系统和 Universal CRT 库。

## IDE 接入

Android Studio 使用专用的构建错误扩展；代码同时提供 IntelliJ IDEA 的通用 Gradle 检查器。上述运行验证版本为 Android Studio。

IDE 运行时由 IDE 本身管理。252 和 261 产品元数据的最低 Java 版本为 21，262 为 25；插件 JVM 字节码目标为 21。IDE 运行时与项目的 Gradle JDK 分别配置。

## 安装与更新

通过 **Settings > Plugins > 齿轮菜单 > Install Plugin from Disk** 选择插件 ZIP。Android Studio 的构建错误扩展点不支持动态卸载，因此插件更新后需要重启 IDE。

## 文件范围

扫描接受本地文件绝对路径，支持文件身份相同的硬链接别名。目录、网络路径、网络映射驱动器和备用数据流被拒绝。扫描按当前用户权限执行，不自动提权。
