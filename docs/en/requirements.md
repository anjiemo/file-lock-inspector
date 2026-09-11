# Features

English | [简体中文](../zh-CN/requirements.md)

File Lock Inspector diagnoses local Windows file usage inside the IDE, with process inspection, detail copying, and selected process termination.

## Entry Points

- The lock-and-person icon before Search in the main toolbar, with the **Inspect File Usage** tooltip.
- **Tools > Inspect File Usage**.
- **Inspect File Usage** in the project view context menu, using the selected file.
- Diagnosis links in recognized Chinese and English Gradle sharing-violation errors. The AS entry offers file selection when an error references multiple files.

## Scanning and Results

Select a file or paste its absolute local path, then click Refresh or press Enter in the path field. Opening the dialog with a file path starts a scan automatically. Paths support spaces and Chinese characters.

Background scans stream rows into the process list, showing process name, PID, owner, start time, and operation status. Columns support sorting and the list supports multiple selection. When scanning finishes, selections with the same PID, start time, and file identity remain selected.

Cancelling retains received results and displays the cancelled status. Each helper execution has a 30-second deadline. During scanning, the path field is read-only and Refresh, Browse, and Clear are disabled.

## Process Details

Selecting processes displays their details, including multiple processes together:

| Field | Content |
| --- | --- |
| PID | Process identifier |
| Executable | Full program path |
| Start time | Process creation time |
| Owner | Windows account |
| File | Target file |
| Evidence | Open file handle or loaded module |
| Command line | Displayed when readable and process identity matches |
| Operation | Available operation or protection status |

Details support wrapping, scrolling, and text selection. The header copy icon copies all displayed details. The context menu offers **Copy this detail**, **Copy selected text**, and **Copy all details**. Wrapped entries are copied in full.

## Clearing and History

**Clear results** resets the current dialog's list, details, selection, and status while retaining the file path. It does not delete files or terminate processes.

After recognizing a build error, the plugin can capture a timestamped background scan. **Historical results** displays saved diagnosis information for the file. Historical results cannot authorize termination. Clearing the dialog retains saved project history.

## Termination and Rebuilding

Termination requires explicit selection and confirmation. The helper rechecks process start time, file identity, and current usage before acting. The current IDE, helper, critical processes, and protected or unclassified targets cannot be terminated through this operation.

Only selected processes are terminated; child processes are not automatically included. Forced termination can lose unsaved content. The plugin waits for the outcome and scans again.

When opened from a build error with a single original-build retry action supplied by the IDE, the dialog provides **Rerun Original Build** as a separate user action.

## UI Language

Choose **Follow IDE**, **Chinese**, or **English** in **Settings > Tools > File Lock Inspector**. Follow IDE is the default: Chinese IDEs use Simplified Chinese and other languages use English. The setting persists at IDE level across projects. Paths, process names, command lines, and original build logs remain unchanged.
