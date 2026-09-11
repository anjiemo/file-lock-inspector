package io.github.anjiemo.filelockinspector

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class Diagnostics : Disposable {
    private val history = ConcurrentHashMap<String, Snapshot>()
    private val active = ConcurrentHashMap<String, Cancellation>()
    @Volatile private var disposed = false
    fun previous(path: String): Snapshot? = history[path]
    @Synchronized fun capture(path: String) {
        if (disposed || active.size >= 2 || history[path]?.time?.isAfter(java.time.Instant.now().minusSeconds(60)) == true) return
        val token = Cancellation()
        if (active.putIfAbsent(path, token) != null) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val snapshot = NativeClient.scan(path, token)
                if (!disposed) { if (history.size >= 20) history.keys.firstOrNull()?.let(history::remove); history[path] = snapshot }
            } finally { active.remove(path, token) }
        }
    }
    @Synchronized override fun dispose() { disposed = true; active.values.forEach { it.cancel() }; active.clear(); history.clear() }
    companion object { fun get(project: Project): Diagnostics = project.getService(Diagnostics::class.java) }
}
