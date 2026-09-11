# Runtime Environment

English | [简体中文](../zh-CN/compatibility.md)

Plugin version {{VERSION}} is compiled against IntelliJ Platform SDK 261 and declares a platform range of 261 through 262.*.

| Item | Configuration |
| --- | --- |
| Operating system | Windows x64; local verification uses Windows 11 x64 |
| Verified Android Studio builds | AI-261.26222.65.2614.16000733, AI-262.9437.185.2621.16297395 |
| Target | A single file on a local disk |
| Build integration | Local Gradle build errors |
| Scanner | Bundled Windows x64 helper |
| UI languages | Follow IDE, Simplified Chinese, English |

Diagnosis requires no PowerToys, Python, separate C++ runtime, or network service. The helper uses Windows system and Universal CRT libraries.

## IDE Integration

Android Studio uses a dedicated build-error extension. The code also provides a generic Gradle checker for IntelliJ IDEA. The runtime verification builds listed above are Android Studio builds.

The IDE manages its own runtime. Product metadata specifies Java 21 as the minimum for 261 and Java 25 for 262; plugin bytecode targets JVM 21. The IDE runtime and the project's Gradle JDK are configured separately.

## Installation and Updates

Select the plugin ZIP through **Settings > Plugins > gear menu > Install Plugin from Disk**. Android Studio's build-error extension point does not support dynamic unloading, so plugin updates require an IDE restart.

## File Scope

Scanning accepts absolute local file paths and recognizes hard-link aliases with the same file identity. Directories, network paths, mapped network drives, and alternate data streams are rejected. Scanning uses the current user's permissions without automatic elevation.
