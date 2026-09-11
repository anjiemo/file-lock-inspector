package io.github.anjiemo.filelockinspector

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class Diagnostics : Disposable {
    private val history = ConcurrentHashMap<String, Snapshot>()
    private val active = ConcurrentHashMap<String, Cancellation>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String, Snapshot) -> Unit>()
    @Volatile private var disposed = false
    fun previous(path: String): Snapshot? = history[pathKey(path)]
    fun subscribe(parent: Disposable, listener: (String, Snapshot) -> Unit) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }
    @Synchronized private fun publish(path: String, snapshot: Snapshot) {
        if (disposed) return
        val key = pathKey(path)
        if (history.size >= 20 && !history.containsKey(key)) history.keys.firstOrNull()?.let(history::remove)
        history[key] = snapshot
        listeners.forEach { it(path, snapshot) }
    }
    @Synchronized fun capture(path: String) {
        val key = pathKey(path)
        if (disposed || active.size >= 2 || history[key]?.time?.isAfter(java.time.Instant.now().minusSeconds(60)) == true) return
        val token = Cancellation()
        if (active.putIfAbsent(key, token) != null) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val snapshot = NativeClient.scan(path, token)
                publish(path, snapshot)
            } finally { active.remove(key, token) }
        }
    }
    @Synchronized override fun dispose() { disposed = true; active.values.forEach { it.cancel() }; active.clear(); history.clear(); listeners.clear() }
    companion object {
        fun get(project: Project): Diagnostics = project.getService(Diagnostics::class.java)
        fun pathKey(path: String): String = java.nio.file.Path.of(path.replace('/', '\\')).normalize()
            .toString().lowercase(java.util.Locale.ROOT)
    }
}
