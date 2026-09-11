package io.github.anjiemo.filelockinspector

import com.intellij.build.FilePosition
import com.intellij.build.BuildView
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

open class InspectQuickFix(private val files: List<String>) : BuildIssueQuickFix {
    constructor(file: String) : this(listOf(file))
    override val id = "file-lock-inspector-${files.joinToString("\n").hashCode().toUInt()}"
    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
        val future = CompletableFuture<Void>()
        ApplicationManager.getApplication().invokeLater {
            try {
                if (!project.isDisposed) {
                    val file = if (files.size == 1) files.single() else {
                        val selection = Messages.showChooseDialog(project, Text.get("chooseTarget"), Text.get("inspect"),
                            Messages.getQuestionIcon(), files.toTypedArray(), files.first())
                        files.getOrNull(selection)
                    }
                    val actions = BuildView.RESTART_ACTIONS.getData(dataContext).orEmpty()
                    if (file != null) InspectorDialog(project, file, actions.singleOrNull()?.let { BuildRestart(it, dataContext) }).show()
                }
                future.complete(null)
            }
            catch (error: Exception) { future.completeExceptionally(error) }
        }
        return future
    }
}

class GradleLockIssueChecker : GradleIssueChecker {
    private fun enabled() = SystemInfo.isWindows && !PluginManagerCore.isPluginInstalled(PluginId.getId("org.jetbrains.android"))
    private fun issue(text: String): BuildIssue? {
        if (!enabled()) return null
        val paths = ErrorRecognition.paths(text)
        if (paths.isEmpty()) return null
        for (project in ProjectManager.getInstance().openProjects) {
            val base = project.basePath ?: continue
            paths.filter { it.replace('\\', '/').startsWith(base.trimEnd('/') + "/", ignoreCase = true) }
                .forEach { Diagnostics.get(project).capture(it) }
        }
        val fixes = paths.map(::InspectQuickFix)
        return object : BuildIssue {
            override val title = Text.get("lockedFile")
            override val description = StringUtil.escapeXmlEntities(text) + "\n\n" + fixes.mapIndexed { index, fix ->
                "<a href=\"${fix.id}\">${StringUtil.escapeXmlEntities(Text.get("inspect"))}: ${StringUtil.escapeXmlEntities(paths[index])}</a>"
            }.joinToString("\n")
            override val quickFixes = fixes
            override fun getNavigatable(project: Project): Navigatable? = null
        }
    }
    override fun check(issueData: GradleIssueData): BuildIssue? {
        // GradleIssueData changed from a class in 261 to an interface in 262.
        val error = GradleIssueData::class.java.getMethod("getError").invoke(issueData) as? Throwable
        return error?.let { issue(it.stackTraceToString()) }
    }
    override fun consumeBuildOutputFailureMessage(message: String, failureCause: String, stacktrace: String?, location: FilePosition?, parentEventId: Any, messageConsumer: Consumer<in BuildEvent>): Boolean {
        val issue = issue(listOfNotNull(message, stacktrace).joinToString("\n")) ?: return false
        messageConsumer.accept(BuildIssueEventImpl(parentEventId, issue, MessageEvent.Kind.ERROR))
        return true
    }
}
