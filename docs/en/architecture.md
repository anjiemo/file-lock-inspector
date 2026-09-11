# Architecture

English | [简体中文](../zh-CN/architecture.md)

## Modules

| Directory | Responsibility |
| --- | --- |
| `plugin/` | Kotlin UI, language settings, error recognition, history, helper communication, and generic Gradle integration |
| `android-studio/` | Android Studio build-error diagnosis links |
| `native/windows/` | C++ file scanning and process operations |

## IDE Integration

The AS provider implements `GradleErrorQuickFixProvider` and registers with `com.android.tools.idea.gradle.errorQuickFixProvider` to append diagnosis links to recognized build errors. Generic Gradle integration uses `org.jetbrains.plugins.gradle.issueChecker` and `BuildIssueQuickFix`; the generic checker is disabled when the Android plugin is installed.

Optional plugin descriptors declare the two integration paths. Manual diagnosis is available through the main toolbar, Tools menu, and project view context menu.

## Scanning and Communication

The Kotlin client extracts the bundled helper into a SHA-256-specific directory under the IDE system directory and launches it with a `ProcessBuilder` argument list. The helper streams UTF-8 newline-delimited JSON; see [Native Protocol](protocol.md).

Native scanning enumerates system file handles and loaded modules, matching targets by volume serial number and 128-bit file ID. Handles use scoped cleanup. The parent controls cancellation and the 30-second deadline while retaining received results. Scans are not atomic snapshots; skipped checks, including permission limitations, are represented by status and counts.

Build-error capture allows at most two concurrent scans, deduplicates recent records for the same file within 60 seconds, and retains results for up to 20 files per project. Project disposal cancels captures and clears the cache.

## UI and Operations

Background results update the table on the IDE event thread. Scan completion preserves valid selections by PID, start time, and file ID. Table replacement batches detail updates, and unchanged detail text retains its text selection.

Details support full and single-entry copying. Clear resets only query tab state. Icon buttons use IDE controls, scaled dimensions, and localized tooltips; mouse clicks retain existing focus while keyboard navigation remains available.

Termination rechecks process identity, file identity, and current usage. Creation time is verified on the same process handle used for termination. The helper waits up to five seconds for exit, followed by another scan. The IDE supplies the original-build retry action, which the user triggers separately.

## Language and Distribution

Language selection is stored in the IDE-level `file-lock-inspector.xml`. IDE language and explicit selection resolve Chinese or English resources. The plugin converts native status codes into localized text.

The plugin ZIP includes the Kotlin implementation, Windows x64 helper, light/dark icons, and licenses. The project uses [Apache License 2.0](../../LICENSE); see [Third-party Notices](../../third-party-notices/README.md) for component attribution.
