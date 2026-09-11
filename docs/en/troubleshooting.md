# Controls and Status

English | [简体中文](../zh-CN/troubleshooting.md)

## Tool Window

File Lock Inspector opens in the right tool window bar. Use the IDE's window menu to switch between docked, floating, and windowed modes or move it to another edge. The IDE saves the window layout.

Hiding the tool window preserves query results and running tasks. Closing a query tab cancels its task and releases its resources. Different files can be inspected in separate tabs; opening an existing target selects its tab without restarting the scan. Refresh starts a new scan. Narrow views provide horizontal scrolling for the process table.

## Toolbar

Drag the divider between the process list and details to adjust their height ratio. Hovering highlights the divider and shows a vertical resize cursor. The ratio is remembered; the list fills the space when details are hidden.

| Control | Behavior |
| --- | --- |
| Select file | Opens the IDE file chooser and scans the selected file |
| Refresh | Scans the current path |
| Cancel scan | Stops the current helper and retains received process results |
| Clear results | Clears the query tab while retaining the path |

Cancel is available only during scanning or process operations. Its tooltip changes to **Cancel remaining operations** while ending processes; termination already requested is not undone. Refresh, Browse, and Clear are disabled during these operations.

## Scan Status

| Status | Meaning |
| --- | --- |
| Ready | A path can be entered or a scan started |
| Scanning | Background query with streamed results |
| Scan completed | The configured scan finished |
| Partial scan | Some checks were skipped; available results are returned |
| Cancelled | Execution was cancelled; received results are retained |
| Timed out | The 30-second deadline was reached; received results are retained |
| File does not exist | The target file could not be found |
| Access denied | Current permissions do not allow the query or operation |
| Invalid path or unsupported target | Input is outside the local single-file scope |
| Identity unavailable | Required file or process identity could not be obtained |
| Cannot prepare or start the helper | The helper could not be extracted or launched; details are recorded in IDE logs |
| Invalid helper response | The helper returned an unexpected response; details are recorded in IDE logs |

The status bar shows query time, process count, and skipped checks. Its tooltip contains the full status text. An empty list means the query returned no matching processes.

## Process Operation Status

| Status | Meaning |
| --- | --- |
| Available after confirmation | Selection and submission are allowed; execution still verifies identity |
| Protected process or unknown protection | Termination is unavailable |
| Process identity changed | No termination is performed |
| Current file usage not confirmed | No termination is performed |
| Process already exited | The target has ended |
| Process exit confirmed | Exit was observed after termination |
| Termination requested; exit not confirmed | Exit was not confirmed within five seconds |

After the operation, another scan displays current usage and the detail area shows operation outcomes.
