package io.github.anjiemo.filelockinspector.android

import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink
import com.android.tools.idea.project.messages.SyncMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import io.github.anjiemo.filelockinspector.*

class AndroidLockQuickFixProvider : GradleErrorQuickFixProvider {
    override fun createBuildIssueAdditionalQuickFix(buildEvent: BuildEvent, taskId: ExternalSystemTaskId): DescribedBuildIssueQuickFix? {
        if (!SystemInfo.isWindows || (buildEvent is MessageEvent && buildEvent.kind != MessageEvent.Kind.ERROR)) return null
        if (buildEvent is BuildIssueEvent && buildEvent.issue.quickFixes.any { it.id.startsWith("file-lock-inspector-") }) return null
        val text = listOfNotNull(buildEvent.message, buildEvent.description, (buildEvent as? MessageEvent)?.result?.details).joinToString("\n")
        val files = ErrorRecognition.paths(text)
        if (files.isEmpty()) return null
        taskId.findProject()?.let { project -> files.forEach { Diagnostics.get(project).capture(it) } }
        return object : InspectQuickFix(files), DescribedBuildIssueQuickFix {
            override val description: String get() = Text.get("inspect")
            override val html: String get() = "  <a href=\"$id\">${StringUtil.escapeXmlEntities(description)}</a>"
        }
    }
    override fun createSyncMessageAdditionalLink(syncMessage: SyncMessage, affectedModules: List<Module>, buildFileMap: Map<Module, VirtualFile>, rootProjectPath: String): SyncMessageHyperlink? = null
}
