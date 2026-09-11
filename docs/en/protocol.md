# Native Protocol

English | [简体中文](../zh-CN/protocol.md)

The plugin starts the bundled helper with a direct argument list. Standard output contains UTF-8 newline-delimited JSON, with `protocol: 1` on every record. Output is flushed after every record. Standard error is not part of the protocol.

```text
file-lock-inspector-helper.exe scan <absolute-local-file> <ide-pid>
file-lock-inspector-helper.exe terminate <absolute-local-file> <ide-pid> <pid> <start-time> <file-id>
file-lock-inspector-helper.exe --version
```

`process` records contain `pid`, `startTime` (Windows creation FILETIME ticks as a string), `fileId` (volume serial and 128-bit file ID), `name`, `executable`, `owner`, `file`, `evidence` (`handle` or `module`), `blockedReason`, and `canTerminate`. Optional descriptive values use empty strings when unavailable.

`end` records contain `status`, optional `skipped` count, and optional native `error`. `complete` means the configured scan completed, not an atomic or exhaustive snapshot. `partial` means checks were skipped. The parent imposes a 30-second deadline, retaining earlier process records as partial evidence on timeout or cancellation.

`operation` records contain `pid` and `status`. Termination verifies process creation time on the handle used for the operation, rejects protected or unclassified targets, verifies file identity and current usage, terminates only the selected process, and waits up to five seconds for exit. Occupancy can still change after verification. An accepted termination request alone is not success.

The parent treats malformed responses, unsupported protocol versions, missing final records, and helper failures as failures. It kills and reaps its own helper on cancellation, timeout, dialog disposal, or project disposal. Existing scan results never authorize termination without another native check.
