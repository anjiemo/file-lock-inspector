package io.github.anjiemo.filelockinspector

import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.ProjectManager
import java.awt.Container
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JTable
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import kotlin.system.exitProcess

/** Compiled and registered only by -PideSmoke; excluded from release artifacts. */
class IdeSmokeStarter : ApplicationStarter {
    override val isHeadless = false
    override fun main(args: List<String>) {
        val output = Path.of(args[1]); Files.createDirectories(output)
        try {
            check(PluginManagerCore.getPlugin(PluginId.getId("io.github.anjiemo.filelockinspector"))?.isEnabled == true)
            val provider = GradleErrorQuickFixProvider.getProviders().single { it.javaClass.name.endsWith("AndroidLockQuickFixProvider") }
            val project = ProjectManager.getInstance().defaultProject
            val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            val inspectAction = checkNotNull(actionManager.getAction("FileLockInspector.Inspect"))
            val toolbar = actionManager.getAction("MainToolbarRight") as com.intellij.openapi.actionSystem.DefaultActionGroup
            val toolbarActions = toolbar.getChildren(actionManager).toList()
            check(toolbarActions.indexOf(inspectAction) in 0 until toolbarActions.indexOf(actionManager.getAction("SearchEverywhere")))
            checkNotNull(inspectAction.templatePresentation.icon)
            for ((suffix, background) in listOf("" to java.awt.Color.WHITE, "_dark" to java.awt.Color(30, 31, 34))) {
                val icon = com.intellij.openapi.util.IconLoader.getDarkIcon(
                    com.intellij.openapi.util.IconLoader.getIcon("/icons/fileLock.svg", javaClass), suffix.isNotEmpty())
                check(icon.iconWidth > 0 && icon.iconHeight > 0)
                val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()
                graphics.color = background; graphics.fillRect(0, 0, 40, 40)
                icon.paintIcon(null, graphics, 12, 12); graphics.dispose()
                check((0 until 40).any { x -> (0 until 40).any { y -> image.getRGB(x, y) != background.rgb } })
                ImageIO.write(image, "png", output.resolve("toolbar-icon$suffix.png").toFile())
            }
            val file = Files.createTempFile(output, "smoke-", ".jar")
            val snapshot = java.io.RandomAccessFile(file.toFile(), "rw").use {
                NativeClient.scan(file.toString(), Cancellation())
            }
            check(snapshot.status in listOf("complete", "partial")) { "Native scan: ${snapshot.status}" }
            check(snapshot.rows.any { it.pid == ProcessHandle.current().pid() && !it.canTerminate }) { "IDE holder not detected/protected" }
            java.io.RandomAccessFile(file.toFile(), "rw").use {
                val cancellation = Cancellation()
                val cancelled = NativeClient.scan(file.toString(), cancellation) { cancellation.cancel() }
                check(cancelled.status == "cancelled" && cancelled.rows.isNotEmpty()) { "Cancellation lost partial results" }
            }
            val error = "$file: The process cannot access the file because it is being used by another process"
            val event = MessageEventImpl("smoke", MessageEvent.Kind.ERROR, null, error, error)
            val task = ExternalSystemTaskId.create(ProjectSystemId("GRADLE"), ExternalSystemTaskType.EXECUTE_TASK, project)
            check(provider.createBuildIssueAdditionalQuickFix(event, task) != null) { "No AS quick fix" }
            val issueType = GradleIssueData::class.java
            val throwable = IllegalStateException(error)
            val issueData = if (issueType.isInterface) {
                val failureType = Class.forName("org.jetbrains.plugins.gradle.issue.GradleIssueFailure")
                val failure = failureType.getMethod("createIssueFailure", Throwable::class.java).invoke(null, throwable)
                issueType.methods.single { it.name == "createIssueData" }.invoke(null, output, failure, null, null)
            } else {
                issueType.constructors.single { it.parameterCount == 4 }.newInstance(output.toString(), throwable, null, null)
            }
            // AS disables the generic checker, but its version-dependent accessor must still link.
            check(GradleLockIssueChecker().check(issueData as GradleIssueData) == null)
            val settings = LanguageSettings.get()
            for (language in listOf("EN", "ZH")) {
                settings.state.language = language
                val configurable = LanguageConfigurable()
                try {
                    val form = configurable.createComponent()
                    for (width in listOf(360, 650)) {
                        form.setSize(width, 550)
                        fun layoutForm(container: Container) {
                            container.doLayout()
                            container.components.filterIsInstance<Container>().forEach(::layoutForm)
                        }
                        layoutForm(form)
                        val image = BufferedImage(width, form.height, BufferedImage.TYPE_INT_RGB)
                        val graphics = image.createGraphics()
                        form.printAll(graphics); graphics.dispose()
                        ImageIO.write(image, "png", output.resolve("settings-$language-$width.png").toFile())
                    }
                } finally { configurable.disposeUIResources() }
                val dialog = InspectorDialog(project, "")
                try {
                    val panel = dialog.contentPane
                    fun renderDialog(name: String, width: Int, height: Int) {
                        panel.setSize(width, height)
                        fun layout(container: Container) { container.doLayout(); container.components.filterIsInstance<Container>().forEach(::layout) }
                        layout(panel)
                        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_RGB)
                        val graphics = image.createGraphics()
                        panel.printAll(graphics); graphics.dispose()
                        ImageIO.write(image, "png", output.resolve(name).toFile())
                    }
                    renderDialog("dialog-empty-$language.png", 920, 530)
                    fun findTable(container: Container): JTable? {
                        for (component in container.components) {
                            if (component is JTable) return component
                            if (component is Container) findTable(component)?.let { return it }
                        }
                        return null
                    }
                    val table = checkNotNull(findTable(panel))
                    val display = dialog.javaClass.getDeclaredMethod("display", Snapshot::class.java).apply { isAccessible = true }
                    display.invoke(dialog, snapshot)
                    val fileField = dialog.javaClass.getDeclaredField("fileField").apply { isAccessible = true }.get(dialog) as javax.swing.JTextField
                    fileField.text = file.toString()
                    table.setRowSelectionInterval(0, 0)
                    val details = dialog.javaClass.getDeclaredField("details").apply { isAccessible = true }.get(dialog) as javax.swing.JTextArea
                    checkNotNull(details.componentPopupMenu)
                    val selectedPid = table.getValueAt(table.selectedRow, 1)
                    val originalDetails = details.text
                    details.select(0, 3)
                    display.invoke(dialog, snapshot)
                    check(table.getValueAt(table.selectedRow, 1) == selectedPid && details.selectedText == originalDetails.take(3))
                    val synthetic = snapshot.rows.first().copy(pid = Long.MAX_VALUE, name = "extra-test-process")
                    table.rowSorter.toggleSortOrder(1)
                    display.invoke(dialog, snapshot.copy(rows = listOf(synthetic) + snapshot.rows))
                    check(table.selectedRowCount == 1 && table.getValueAt(table.selectedRow, 1) == selectedPid)
                    check(details.text == originalDetails && details.selectedText == originalDetails.take(3))
                    display.invoke(dialog, snapshot.copy(rows = snapshot.rows.map { it.copy(startTime = "1") }))
                    check(table.selectedRowCount == 0 && details.text.isEmpty())
                    display.invoke(dialog, snapshot)
                    table.setRowSelectionInterval(0, 0)
                    val lineMethod = dialog.javaClass.getDeclaredMethod("detailLine", Int::class.javaPrimitiveType).apply { isAccessible = true }
                    var offset = 0
                    for (line in details.text.split('\n')) {
                        check(lineMethod.invoke(dialog, offset) == line)
                        offset += line.length + 1
                    }
                    val copyButton = dialog.javaClass.getDeclaredField("copyDetails").apply { isAccessible = true }.get(dialog) as javax.swing.JButton
                    val clipboard = com.intellij.openapi.ide.CopyPasteManager.getInstance()
                    val previous = clipboard.contents
                    try {
                        copyButton.doClick()
                        check(clipboard.getContents<String>(java.awt.datatransfer.DataFlavor.stringFlavor) == details.text)
                    } finally {
                        clipboard.setContents(previous ?: java.awt.datatransfer.StringSelection(""))
                    }
                    renderDialog("dialog-$language.png", 920, 530)
                    renderDialog("dialog-compact-$language.png", 720, 520)
                    val clear = dialog.javaClass.getDeclaredField("clear").apply { isAccessible = true }.get(dialog) as javax.swing.JButton
                    for (name in listOf("refresh", "cancel", "clear", "browse", "copyDetails")) {
                        val button = dialog.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(dialog) as javax.swing.JButton
                        check(!button.isRequestFocusEnabled && !button.isDefaultCapable && button.isFocusable)
                    }
                    val setBusy = dialog.javaClass.getDeclaredMethod("setBusy", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                    val snapshotToggle = dialog.javaClass.getDeclaredField("historical").apply { isAccessible = true }.get(dialog) as javax.swing.JCheckBox
                    dialog.javaClass.getDeclaredField("current").apply { isAccessible = true }.set(dialog, snapshot)
                    check(!snapshotToggle.isVisible)
                    val pathField = dialog.javaClass.getDeclaredField("scannedPath").apply { isAccessible = true }
                    pathField.set(dialog, Diagnostics.pathKey(file.toString()))
                    val diagnostics = Diagnostics.get(project)
                    val publish = diagnostics.javaClass.getDeclaredMethod("publish", String::class.java, Snapshot::class.java).apply { isAccessible = true }
                    fun publishAndDispatch(path: String) {
                        publish.invoke(diagnostics, path, snapshot)
                        val loop = java.awt.Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
                        javax.swing.SwingUtilities.invokeLater { loop.exit() }
                        check(loop.enter())
                    }
                    publishAndDispatch(file.toString() + ".other")
                    check(!snapshotToggle.isVisible)
                    publishAndDispatch(file.toString().uppercase(java.util.Locale.ROOT).replace('\\', '/'))
                    check(diagnostics.previous(file.toString()) == snapshot)
                    check(snapshotToggle.isVisible && snapshotToggle.isEnabled)
                    snapshotToggle.doClick()
                    check(snapshotToggle.isSelected)
                    snapshotToggle.doClick()
                    setBusy.invoke(dialog, true, false)
                    val cancelButton = dialog.javaClass.getDeclaredField("cancel").apply { isAccessible = true }.get(dialog) as javax.swing.JButton
                    check(cancelButton.toolTipText == Text.get("cancelScan"))
                    setBusy.invoke(dialog, true, true)
                    check(cancelButton.toolTipText == Text.get("cancelTermination"))
                    check(fileField.isEnabled && !fileField.isEditable)
                    check(!clear.isEnabled)
                    check(!snapshotToggle.isEnabled)
                    clear.doClick()
                    check(table.rowCount > 0)
                    setBusy.invoke(dialog, false, false)
                    check(fileField.isEnabled && fileField.isEditable)
                    clear.doClick()
                    check(table.rowCount == 0 && details.text.isEmpty() && fileField.text == file.toString())
                    val detailsPanel = dialog.javaClass.getDeclaredField("detailsPanel").apply { isAccessible = true }.get(dialog) as javax.swing.JPanel
                    check(!detailsPanel.isVisible)
                    publishAndDispatch(file.toString())
                    check(!snapshotToggle.isVisible && !snapshotToggle.isSelected)
                    val status = dialog.javaClass.getDeclaredField("status").apply { isAccessible = true }.get(dialog) as javax.swing.JLabel
                    check(status.text == Text.get("ready"))
                    renderDialog("dialog-cleared-$language.png", 720, 520)
                    check(table.getColumnName(0) == if (language == "ZH") "进程" else "Process")
                } finally { dialog.disposeIfNeeded() }
            }
            settings.state.language = "IDE"
            Files.writeString(output.resolve("result.txt"), "PASS: plugin loading, AS provider registration and quick fix, GradleIssueData compatibility, bundled native scan, cancellation with partial results, IDE protection, English and Chinese dialog rendering")
            exitProcess(0)
        } catch (error: Throwable) {
            Files.writeString(output.resolve("result.txt"), error.stackTraceToString())
            error.printStackTrace()
            exitProcess(1)
        }
    }
}
