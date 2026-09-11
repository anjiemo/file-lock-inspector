# 第三方说明

[English](README.md) | 简体中文

## 已包含代码

- Google Material Symbols 的 `lock_person` 图标，[固定来源提交 6d7ca43bd6e6668531a00fcaca06d921b63dd716](https://github.com/google/material-design-icons/blob/6d7ca43bd6e6668531a00fcaca06d921b63dd716/symbols/web/lock_person/materialsymbolsoutlined/lock_person_24px.svg)，采用 [Apache License 2.0](../LICENSE)。包含为 `icons/fileLock.svg` 和 `icons/fileLock_dark.svg`，修改尺寸为 16 px 并设置明暗主题配色。

- nlohmann/json 3.12.0，`native/windows/vendor/nlohmann/json.hpp`，来自上游 `v3.12.0` 发布，未修改。SHA-256：`aaf127c04cb31c406e5b04a63f1ae89369fccde6d8fa7cdda1ed4f32dfc5de63`。参见 [MIT 许可证](nlohmann-json-LICENSE.MIT)。原生辅助程序使用该库序列化结构化结果。
- Gradle Wrapper 9.5.0，由 Gradle 生成，采用 Apache License 2.0；生成脚本保留原有版权头。

## 设计参考

以下来源用于设计参考，实现未复制 PowerToys 或 Android Studio 源码：

- [Microsoft PowerToys](https://github.com/microsoft/PowerToys)，采用 [MIT 许可证](https://github.com/microsoft/PowerToys/blob/main/LICENSE)。
- [FileLocksmith.cpp](https://github.com/microsoft/PowerToys/blob/main/src/modules/FileLocksmith/FileLocksmithLibInterop/FileLocksmith.cpp)：目标匹配、系统句柄使用及已加载模块。
- [NtdllExtensions.cpp](https://github.com/microsoft/PowerToys/blob/main/src/modules/FileLocksmith/FileLocksmithLibInterop/NtdllExtensions.cpp)：原生句柄查询。
- [GradleErrorQuickFixProvider.kt](https://android.googlesource.com/platform/tools/adt/idea/+/mirror-goog-studio-main/project-system-gradle/src/com/android/tools/idea/gradle/project/build/events/GradleErrorQuickFixProvider.kt)：Android Studio 扩展接口，源码头声明 Apache 2.0 许可。
