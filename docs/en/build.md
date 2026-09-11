# Build and Test

English | [简体中文](../zh-CN/build.md)

## Prerequisites

- Windows x64 and JDK 25 (Android Studio includes a suitable JBR).
- A local Android Studio 261 installation as the minimum SDK target. Release artifacts are compiled against 261.
- Zig 0.14.1 for the statically linked native helper. Set `ZIG_EXE` to `zig.exe`.

Download Zig from [the official release](https://ziglang.org/download/0.14.1/zig-x86_64-windows-0.14.1.zip). Its SHA-256 is `554f5378228923ffd558eac35e21af020c73789d87afeabf4bfd16f2e6feed2c`. The build also checks `%LOCALAPPDATA%\FileLockInspector\toolchains\zig-x86_64-windows-0.14.1\zig.exe` when `ZIG_EXE` is unset.

## Commands

Run from the repository root in PowerShell:

```powershell
$env:IDE_HOME = 'D:\ProgramFiles\Android\StudioApps\Android Studio 4'
$env:JAVA_HOME = Join-Path $env:IDE_HOME 'jbr'
$env:ZIG_EXE = 'C:\toolchains\zig-x86_64-windows-0.14.1\zig.exe'
.\gradlew.bat test buildPlugin
powershell.exe -NoProfile -ExecutionPolicy Bypass -File native/windows/tests/integration.ps1
```

You can also pass `-PidePath=...` instead of `IDE_HOME`. If Maven Central is inaccessible, supply an explicitly chosen mirror such as `-PmavenMirror=https://maven.aliyun.com/repository/central`. No mirror is enabled by default.

The plugin ZIP is written under `build/distributions/`. Install it through the IDE's **Settings > Plugins > gear menu > Install Plugin from Disk**. For an isolated development IDE, run `gradlew.bat runIde`; it uses the build sandbox rather than your normal configuration.

The native integration test creates its own file holders under `build/native-tests/`, tests identification and selected termination, and cleans up only its own processes. The test targets are generated fixtures.

## Version and Documentation

`version.properties` supplies the plugin and native helper versions. `gradlew.bat renderDocumentation` replaces `{{VERSION}}` in Markdown and produces a complete documentation directory under `build/rendered-docs/`, including license and attribution files. Each generation removes stale output files.

`gradlew.bat buildPlugin` also generates the documentation archive `build/distributions/file-lock-inspector-docs-{{VERSION}}.zip`. Open its README to browse documentation with the resolved version. Repository Markdown is the source template; GitHub does not evaluate its placeholders.

## IDE Smoke Check

Run `gradlew.bat -PideSmoke runIde` with the same environment to load a test-only application entry in an isolated AS sandbox. It checks toolbar and provider registration, native scanning, cancellation, process selection retention, detail copying, clearing, control states, and bilingual dialog rendering, writes `build/ide-smoke/result.txt` and PNGs, then exits. Do not use `-PideSmoke` when packaging; `buildPlugin` rejects that combination.
