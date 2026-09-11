# Controls and Status

English | [简体中文](../zh-CN/troubleshooting.md)

## Toolbar

| Control | Behavior |
| --- | --- |
| Select file | Opens the IDE file chooser and scans the selected file |
| Refresh | Scans the current path |
| Cancel scan | Stops the current helper and retains received process results |
| Clear results | Clears the dialog while retaining the path |

Cancel is available only during scanning or process operations. Cancelling a process operation stops subsequent processing; termination already requested is not undone. Refresh, Browse, and Clear are disabled during these operations.

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
