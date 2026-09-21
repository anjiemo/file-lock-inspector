package io.github.anjiemo.filelockinspector

import com.intellij.build.FilePosition
import com.intellij.build.BuildView
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
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
                    fun inspect(file: String) {
                        val actions = BuildView.RESTART_ACTIONS.getData(dataContext).orEmpty()
                        InspectorToolWindow.open(project, file, actions.singleOrNull()?.let { BuildRestart(it, dataContext) })
                        future.complete(null)
                    }
                    if (files.size == 1) inspect(files.single())
                    else JBPopupFactory.getInstance().createPopupChooserBuilder(files)
                        .setTitle(Text.get("chooseTarget"))
                        .setItemChosenCallback(::inspect)
                        .setCancelCallback { future.complete(null); true }
                        .createPopup()
                        .showCenteredInCurrentWindow(project)
                } else {
                    future.complete(null)
                }
            }
            catch (error: Exception) { future.completeExceptionally(error) }
        }
        return future
    }
}

private class LockBuildIssueEvent(private val parent: Any, private val buildIssue: BuildIssue) : BuildIssueEvent {
    private val eventId = Any()
    private val createdAt = System.currentTimeMillis()

    override fun getId(): Any = eventId
    override fun getParentId(): Any = parent
    override fun getEventTime(): Long = createdAt
    override fun getMessage(): String = buildIssue.title
    override fun getHint(): String? = null
    override fun getDescription(): String = buildIssue.description
    override fun getKind(): MessageEvent.Kind = MessageEvent.Kind.ERROR
    override fun getGroup(): String = buildIssue.title
    override fun getNavigatable(project: Project): Navigatable? = buildIssue.getNavigatable(project)
    override fun getIssue(): BuildIssue = buildIssue
    override fun getResult(): MessageEventResult = object : MessageEventResult {
        override fun getKind(): MessageEvent.Kind = MessageEvent.Kind.ERROR
        override fun getDetails(): String = buildIssue.description
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
        messageConsumer.accept(LockBuildIssueEvent(parentEventId, issue))
        return true
    }
}
