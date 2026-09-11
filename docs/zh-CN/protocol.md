# 原生协议

[English](../en/protocol.md) | 简体中文

插件通过直接参数列表启动内置辅助程序。标准输出为 UTF-8 逐行 JSON，每条记录包含 `protocol: 1`，输出一条即刷新缓冲。标准错误不属于协议。

```text
file-lock-inspector-helper.exe scan <本地文件绝对路径> <IDE进程PID>
file-lock-inspector-helper.exe terminate <本地文件绝对路径> <IDE进程PID> <PID> <启动时间> <文件ID>
file-lock-inspector-helper.exe --version
```

`process` 记录包含 `pid`、`startTime`（字符串形式的 Windows 创建 FILETIME 时钟数）、`fileId`（卷序列号与 128 位文件 ID）、`name`、`executable`、`owner`、`file`、`evidence`（`handle` 或 `module`）、`blockedReason` 和 `canTerminate`。不可获取的描述字段使用空字符串。

`end` 记录包含 `status`，以及可选的 `skipped` 数量和原生 `error`。`complete` 仅表示既定扫描执行完成，不代表原子快照或查全一切占用；`partial` 表示有检查被跳过。父进程实施 30 秒时间限制，超时或取消时保留此前返回的进程作为部分证据。

`operation` 记录包含 `pid` 和 `status`。结束操作使用实际操作句柄核实进程创建时间，拒绝受保护或无法分类的目标，确认文件身份和当前使用情况，仅结束所选进程，并最多等待五秒确认退出。占用仍可能在核实后变化，不能仅凭结束请求被接受就报告成功。

父进程将格式错误、协议版本不支持、缺少结束记录和辅助程序故障视为失败。取消、超时、查询页销毁或项目关闭时结束并回收自身辅助程序。已有扫描结果不能绕过新的原生核验直接授权结束进程。
