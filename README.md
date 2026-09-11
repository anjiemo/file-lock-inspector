# File Lock Inspector

English | [简体中文](README.zh-CN.md)

File Lock Inspector is an IntelliJ Platform plugin for diagnosing Windows file usage from Gradle build errors and manually selected file paths. It lists detected processes and allows users to terminate selected processes after confirmation and identity verification.

## Environment

Version {{VERSION}} runs on Windows x64 with Android Studio 261–262. The Windows scanner is bundled; PowerToys is not required.

## Installation

Install `build/distributions/file-lock-inspector-{{VERSION}}.zip` through **Settings > Plugins > gear menu > Install Plugin from Disk**, then restart the IDE. Rendered documentation is written to `build/rendered-docs/` by `gradlew renderDocumentation`.

## Usage

Click the lock-and-person icon before Search in the main toolbar to open File Lock Inspector. Its tooltip is **Inspect File Usage**.

Open **Tools > Inspect File Usage**, select a file or paste its absolute path, and refresh the results. Recognized Gradle sharing-violation errors also provide a diagnosis link. Select processes explicitly before ending them; historical results cannot be used for termination.

Cancel an active scan with the cancel icon; received results remain visible. Select a process to show details, or select multiple rows to inspect them together. Ending processes requires confirmation and a fresh identity check.

Choose **Follow IDE**, **Chinese**, or **English** in **Settings > Tools > File Lock Inspector**. The default is Follow IDE.

Use the copy icon beside Process details to copy all displayed details. Right-click a detail to copy that entry or selected text; wrapped paths are copied in full.

Use **Clear results** in the file toolbar to reset the list, details, and status while keeping the file path. It is disabled during scans and process operations; saved project history is retained.

## Documentation

- [Features](docs/en/requirements.md)
- [Architecture](docs/en/architecture.md)
- [Runtime environment](docs/en/compatibility.md)
- [Controls and status](docs/en/troubleshooting.md)
- [Build and test](docs/en/build.md)
- [Native protocol](docs/en/protocol.md)
- [Third-party notices](third-party-notices/README.md)

## Structure

```text
file-lock-inspector/
  plugin/                 Shared UI, diagnosis flow, and IDEA Gradle integration
  android-studio/         Android Studio-specific integration
  native/windows/         Windows scanning and process operations
  docs/en/                English documentation
  docs/zh-CN/             Simplified Chinese documentation
  third-party-notices/    Source attribution and license notices
```

## License

[Apache License 2.0](LICENSE).
