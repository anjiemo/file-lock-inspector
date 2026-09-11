package io.github.anjiemo.filelockinspector

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import java.nio.file.Path

class InspectorToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project) = SystemInfo.isWindows

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        InspectorToolWindow.addQuery(project, toolWindow, "", null)
    }
}

object InspectorToolWindow {
    const val ID = "File Lock Inspector"
    private val PANEL = Key.create<InspectorPanel>("file.lock.inspector.panel")

    fun open(project: Project, path: String = "", restart: BuildRestart? = null) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
        window.activate {
            if (project.isDisposed) return@activate
            val manager = window.contentManager
            if (path.isBlank()) {
                if (manager.contentCount == 0) addQuery(project, window, "", null)
                return@activate
            }
            val existing = manager.contents.firstOrNull {
                runCatching { it.getUserData(PANEL)?.targetPath?.takeIf(String::isNotBlank)?.let(Diagnostics::pathKey) }.getOrNull() == Diagnostics.pathKey(path)
            }
            if (existing != null) {
                existing.getUserData(PANEL)?.updateRestart(restart)
                manager.setSelectedContent(existing)
            } else {
                addQuery(project, window, path, restart)
            }
        }
    }

    internal fun addQuery(project: Project, window: ToolWindow, path: String, restart: BuildRestart?) {
        val panel = InspectorPanel(project, path, restart)
        val emptyQueries = if (path.isBlank()) emptyList() else window.contentManager.contents.filter {
            it.getUserData(PANEL)?.targetPath?.isBlank() == true
        }
        val title = if (path.isBlank()) Text.get("inspect") else Path.of(path).fileName?.toString() ?: path
        val content = ContentFactory.getInstance().createContent(panel.component, title, false)
        content.putUserData(PANEL, panel)
        panel.onTargetChanged = { target ->
            content.displayName = Path.of(target).fileName.toString()
            content.description = target
        }
        content.setDisposer(panel)
        content.isCloseable = true
        window.contentManager.addContent(content)
        window.contentManager.setSelectedContent(content)
        emptyQueries.forEach { window.contentManager.removeContent(it, true) }
    }
}
