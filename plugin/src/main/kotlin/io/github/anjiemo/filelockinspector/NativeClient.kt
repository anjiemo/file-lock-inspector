package io.github.anjiemo.filelockinspector

import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class ProcessRow(
    val pid: Long, val startTime: String, val name: String, val executable: String,
    val owner: String, val file: String, val fileId: String, val evidence: String, val blockedReason: String, val canTerminate: Boolean,
) {
    fun startInstant(): Instant? = runCatching {
        val ticks = startTime.toLong()
        if (ticks == 0L) null else Instant.ofEpochSecond(ticks / 10_000_000 - 11_644_473_600, (ticks % 10_000_000) * 100)
    }.getOrNull()
    fun commandLine(): String? = runCatching {
        ProcessHandle.of(pid).orElse(null)?.takeIf { it.info().startInstant().orElse(null)?.toEpochMilli() == startInstant()?.toEpochMilli() }
            ?.info()?.commandLine()?.orElse(null)
    }.getOrNull()
}
data class Snapshot(val rows: List<ProcessRow>, val status: String, val time: Instant = Instant.now(), val skipped: Int = 0)

class Cancellation {
    val cancelled = AtomicBoolean(false)
    private val process = AtomicReference<Process?>()
    fun attach(value: Process) { process.set(value); if (cancelled.get()) value.destroyForcibly() }
    fun cancel() { cancelled.set(true); process.get()?.destroyForcibly() }
}

object NativeClient {
    private val timers get() = AppExecutorUtil.getAppScheduledExecutorService()
    @Synchronized private fun helper(): Path {
        val bytes = NativeClient::class.java.getResourceAsStream("/native/windows-x64/file-lock-inspector-helper.exe")?.use { it.readBytes() }
            ?: error("Missing native helper")
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val folder = Path.of(PathManager.getSystemPath(), "file-lock-inspector", hash)
        Files.createDirectories(folder)
        val path = folder.resolve("file-lock-inspector-helper.exe")
        if (!Files.exists(path) || !MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)), MessageDigest.getInstance("SHA-256").digest(bytes))) {
            val temp = Files.createTempFile(folder, "helper-", ".tmp")
            try { Files.write(temp, bytes); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) } finally { Files.deleteIfExists(temp) }
        }
        return path
    }

    fun scan(file: String, token: Cancellation, onRow: (ProcessRow) -> Unit = {}): Snapshot =
        execute(listOf("scan", file, ProcessHandle.current().pid().toString()), token, onRow)

    fun terminate(row: ProcessRow, token: Cancellation): String =
        execute(listOf("terminate", row.file, ProcessHandle.current().pid().toString(), row.pid.toString(), row.startTime, row.fileId), token).status

    private fun execute(args: List<String>, token: Cancellation, onRow: (ProcessRow) -> Unit = {}): Snapshot {
        val rows = mutableListOf<ProcessRow>()
        var status = "failed"
        var skipped = 0
        var finished = false
        var process: Process? = null
        val timedOut = AtomicBoolean(false)
        var deadline: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            if (token.cancelled.get()) return Snapshot(rows, "cancelled")
            val child = ProcessBuilder(listOf(helper().toString()) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process = child
            token.attach(child)
            deadline = timers.schedule({ timedOut.set(true); child.destroyForcibly() }, 30, TimeUnit.SECONDS)
            child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.length > 1_048_576) error("Oversized response")
                    val data = JsonParser.parseString(line).asJsonObject
                    require(data["protocol"].asInt == 1)
                    fun str(key: String) = data[key]?.asString ?: ""
                    when (str("type")) {
                        "process" -> {
                            val row = ProcessRow(data["pid"].asLong, str("startTime"), str("name"), str("executable"), str("owner"),
                                str("file"), str("fileId"), str("evidence"), str("blockedReason"), data["canTerminate"].asBoolean)
                            rows.add(row); onRow(row)
                        }
                        "end", "operation" -> { status = str("status"); skipped = data["skipped"]?.asInt ?: 0; finished = true }
                        else -> error("Unexpected response")
                    }
                }
            }
            child.waitFor(2, TimeUnit.SECONDS)
            if (child.isAlive || (!finished && !timedOut.get() && !token.cancelled.get())) status = "failed"
            if (!child.isAlive && child.exitValue() != 0 && status in listOf("complete", "partial", "terminated")) status = "failed"
        } catch (_: Exception) { status = "failed" }
        finally {
            deadline?.cancel(false)
            process?.let { if (it.isAlive) it.destroyForcibly(); it.waitFor(2, TimeUnit.SECONDS) }
        }
        if (token.cancelled.get()) status = "cancelled" else if (timedOut.get()) status = "timeout"
        return Snapshot(rows.toList(), status, skipped = skipped)
    }
}
