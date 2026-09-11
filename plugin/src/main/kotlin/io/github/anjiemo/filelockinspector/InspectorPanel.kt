package io.github.anjiemo.filelockinspector

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel

class InspectAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.text = Text.get("inspect")
        e.presentation.description = Text.get("inspect")
        e.presentation.isEnabledAndVisible = SystemInfo.isWindows && e.project != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeUnless { it.isDirectory }?.path ?: ""
        InspectorToolWindow.open(project, file)
    }
}

data class BuildRestart(val action: AnAction, val context: DataContext)

class InspectorPanel(private val project: Project, initialPath: String, private var restart: BuildRestart? = null) : Disposable {
    private var isDisposed = false
    var onTargetChanged: (String) -> Unit = {}
    val targetPath: String get() = fileField.text.trim().removeSurrounding("\"")
    private val fileField = JTextField(initialPath)
    private val status = JLabel(Text.get("ready")).apply {
        addPropertyChangeListener("text") { toolTipText = text }
    }
    private val model = RowsModel()
    private val table = JBTable(model)
    private val details = JTextArea(7, 60).apply {
        isEditable = false; lineWrap = true; wrapStyleWord = true
        font = UIManager.getFont("Label.font")
    }
    private val detailsPanel = JPanel(BorderLayout(0, JBUI.scale(6))).apply { isVisible = false }
    private val resultsSplitter = com.intellij.ui.JBSplitter(true, "FileLockInspector.resultsProportion", 0.6f).apply {
        dividerWidth = JBUI.scale(8)
        setHonorComponentsMinimumSize(true)
        setAllowSwitchOrientationByMouseClick(false)
        var hovered = false
        var dragging = false
        divider.border = object : javax.swing.border.AbstractBorder() {
            override fun paintBorder(c: java.awt.Component, g: java.awt.Graphics, x: Int, y: Int, width: Int, height: Int) {
                g.color = if (hovered || dragging) com.intellij.ui.JBColor.namedColor("Component.focusColor", com.intellij.ui.JBColor(0x3574F0, 0x548AF7))
                    else com.intellij.ui.JBColor.border()
                val thickness = JBUI.scale(if (hovered || dragging) 2 else 1)
                g.fillRect(x, y + (height - thickness) / 2, width, thickness)
            }
        }
        divider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) { hovered = true; divider.repaint() }
            override fun mouseExited(e: java.awt.event.MouseEvent) { hovered = false; divider.repaint() }
            override fun mousePressed(e: java.awt.event.MouseEvent) { if (SwingUtilities.isLeftMouseButton(e)) dragging = true; divider.repaint() }
            override fun mouseReleased(e: java.awt.event.MouseEvent) { dragging = false; hovered = divider.contains(e.point); divider.repaint() }
        })
    }
    private val historical = JCheckBox(Text.get("history")).apply { isEnabled = false; isVisible = false }
    private var scannedPath: String? = null
    private var current: Snapshot? = null
    private var history: Snapshot? = null
    private var busy = false
    private var replacingRows = false
    private var token: Cancellation? = null
    private var generation = 0
    private fun toolButton(icon: Icon, label: String) = JButton(icon).apply {
        toolTipText = label
        accessibleContext.accessibleName = label
        isRequestFocusEnabled = false
        isDefaultCapable = false
        preferredSize = JBUI.size(30, 30)
        minimumSize = preferredSize
    }
    private val refresh = toolButton(AllIcons.Actions.Refresh, Text.get("refresh")).apply { addActionListener { scan() } }
    private val clear = toolButton(AllIcons.Actions.ClearCash, Text.get("clearResults")).apply {
        addActionListener { clearResults() }
    }
    private val copyDetails = toolButton(AllIcons.Actions.Copy, Text.get("copyDetails")).apply {
        addActionListener { copyText(details.text) }
    }
    private fun copyText(text: String) {
        if (text.isNotEmpty()) CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
    private fun detailLine(offset: Int): String {
        val line = details.getLineOfOffset(offset.coerceIn(0, details.document.length))
        val start = details.getLineStartOffset(line)
        return details.getText(start, details.getLineEndOffset(line) - start).trimEnd('\r', '\n')
    }
    private fun installDetailsMenu() {
        val menu = object : JPopupMenu() {
            override fun show(invoker: java.awt.Component, x: Int, y: Int) {
                removeAll()
                val line = detailLine(details.viewToModel2D(java.awt.Point(x, y)))
                add(JMenuItem(Text.get("copyLine"), AllIcons.Actions.Copy).apply {
                    isEnabled = line.isNotBlank()
                    addActionListener { copyText(line) }
                })
                val selectedText = details.selectedText.orEmpty()
                add(JMenuItem(Text.get("copySelection")).apply {
                    isEnabled = selectedText.isNotEmpty()
                    addActionListener { copyText(selectedText) }
                })
                addSeparator()
                add(JMenuItem(Text.get("copyDetails")).apply {
                    isEnabled = details.text.isNotEmpty()
                    addActionListener { copyText(details.text) }
                })
                super.show(invoker, x, y)
            }
        }
        details.componentPopupMenu = menu
    }
    private val cancel = toolButton(AllIcons.Actions.Cancel, Text.get("cancelScan")).apply { isEnabled = false; addActionListener { token?.cancel() } }
    private val terminate = object : AbstractAction(Text.get("terminate"), AllIcons.Debugger.KillProcess) {
        init { isEnabled = false }
        override fun actionPerformed(e: ActionEvent?) { terminateSelected() }
    }
    private val browse = toolButton(AllIcons.Actions.MenuOpen, Text.get("browse")).apply {
        addActionListener {
            FileChooser.chooseFile(FileChooserDescriptor(true, false, false, false, false, false), project, null)?.let {
                fileField.text = it.path; scan()
            }
        }
    }
    private val rebuild = object : AbstractAction(Text.get("rebuild"), AllIcons.Actions.Restart) {
        override fun actionPerformed(e: ActionEvent?) {
            restart?.let {
                val event = AnActionEvent.createEvent(it.action, it.context, null, "FileLockInspector", ActionUiKind.NONE, null)
                @Suppress("DEPRECATION")
                if (ActionUtil.lastUpdateAndCheckDumb(it.action, event, true)) {
                    ActionUtil.performAction(it.action, event)
                    isEnabled = false
                }
            }
        }
    }

    private val rebuildButton = commandButton(rebuild)
    val component: JComponent

    private fun commandButton(action: Action) = JButton(action).apply {
        text = null
        toolTipText = action.getValue(Action.NAME) as String
        accessibleContext.accessibleName = toolTipText
        isRequestFocusEnabled = false
        isDefaultCapable = false
        preferredSize = JBUI.size(30, 30)
        minimumSize = preferredSize
    }

    init {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.autoCreateRowSorter = true
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.rowHeight = JBUI.scale(28)
        table.setShowGrid(false)
        table.emptyText.text = Text.get("emptyResults")
        table.columnModel.getColumn(0).preferredWidth = 180
        table.columnModel.getColumn(1).preferredWidth = 70
        table.columnModel.getColumn(2).preferredWidth = 220
        table.columnModel.getColumn(3).preferredWidth = 180
        table.columnModel.getColumn(4).preferredWidth = 200
        table.selectionModel.addListSelectionListener { event: ListSelectionEvent ->
            if (!replacingRows && !event.valueIsAdjusting) updateSelection()
        }
        fileField.addActionListener { scan() }
        historical.addActionListener { display(if (historical.isSelected) history else current); updateSelection() }
        installDetailsMenu()
        component = createCenterPanel()
        rebuildButton.isVisible = restart != null
        Diagnostics.get(project).subscribe(this) { path, snapshot ->
            later {
                if (scannedPath == Diagnostics.pathKey(path)) {
                    history = snapshot
                    updateSnapshotControl()
                    if (historical.isSelected) display(snapshot)
                }
            }
        }
        if (initialPath.isNotBlank()) later { scan() }
    }

    fun updateRestart(value: BuildRestart?) {
        if (value != null) {
            restart = value
            rebuildButton.isVisible = true
            rebuild.isEnabled = !busy
        }
    }

    override fun dispose() {
        isDisposed = true
        generation++
        token?.cancel()
        restart = null
        resultsSplitter.dispose()
    }

    private fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
        border = JBUI.Borders.empty(8)
        preferredSize = JBUI.size(460, 560)
        minimumSize = JBUI.size(280, 240)
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                add(JLabel(Text.get("file")).apply { labelFor = fileField }, BorderLayout.WEST)
                add(fileField, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(browse); add(refresh); add(cancel); add(clear)
                add(commandButton(terminate)); add(rebuildButton)
            }, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        val tableScroll = JBScrollPane(table).apply {
            setColumnHeaderView(table.tableHeader)
            minimumSize = JBUI.size(0, 70)
        }
        detailsPanel.minimumSize = JBUI.size(0, 100)
        detailsPanel.add(JPanel(BorderLayout()).apply {
            add(JLabel(Text.get("processDetails")), BorderLayout.WEST)
            add(copyDetails, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        detailsPanel.add(JBScrollPane(details).apply {
            preferredSize = JBUI.size(0, 160)
            minimumSize = JBUI.size(0, 80)
            border = JBUI.Borders.empty()
        }, BorderLayout.CENTER)
        details.border = JBUI.Borders.empty(4, 0)
        details.background = UIManager.getColor("Panel.background")
        resultsSplitter.firstComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(JPanel(BorderLayout()).apply {
                add(JLabel(Text.get("processes")), BorderLayout.WEST)
                add(historical, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(tableScroll, BorderLayout.CENTER)
        }
        resultsSplitter.secondComponent = detailsPanel
        add(resultsSplitter, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(status, BorderLayout.CENTER)
        }, BorderLayout.SOUTH)
    }

    private fun later(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({ if (!isDisposed && !project.isDisposed) block() }, ModalityState.any())
    }
    private fun setBusy(value: Boolean, endingProcesses: Boolean = false) {
        val losingFocus = if (value) refresh.isFocusOwner || browse.isFocusOwner || clear.isFocusOwner || historical.isFocusOwner
            else cancel.isFocusOwner
        if (losingFocus) table.requestFocusInWindow()
        busy = value; refresh.isEnabled = !value; browse.isEnabled = !value; fileField.isEditable = !value
        clear.isEnabled = !value
        val cancelLabel = Text.get(if (endingProcesses) "cancelTermination" else "cancelScan")
        cancel.toolTipText = cancelLabel
        cancel.accessibleContext.accessibleName = cancelLabel
        cancel.isEnabled = value; updateSnapshotControl(); rebuild.isEnabled = !value; updateSelection()
    }
    private fun updateSnapshotControl() {
        historical.isEnabled = !busy && history != null
        historical.isVisible = history != null
        historical.parent?.revalidate()
        historical.parent?.repaint()
    }
    private fun clearResults() {
        if (busy) return
        generation++
        current = null
        history = null
        scannedPath = null
        historical.isSelected = false
        updateSnapshotControl()
        table.clearSelection()
        display(null)
        updateSelection()
    }
    private fun scan() {
        if (busy) return
        val file = fileField.text.trim().removeSurrounding("\"")
        val valid = runCatching { Path.of(file).isAbsolute && !file.startsWith("\\\\") }.getOrDefault(false)
        if (!valid) { status.text = Text.status("invalidPath"); return }
        fileField.text = file
        onTargetChanged(file)
        scannedPath = Diagnostics.pathKey(file)
        history = Diagnostics.get(project).previous(file)
        historical.isSelected = false
        current = null; model.replace(emptyList()); details.text = ""
        val ticket = ++generation
        val cancellation = Cancellation(); token = cancellation
        setBusy(true); status.text = Text.get("scanning")
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = NativeClient.scan(file, cancellation) { row -> later { if (ticket == generation) model.add(row) } }
            later {
                if (ticket == generation) {
                    current = result; display(result); setBusy(false)
                }
            }
        }
    }
    private fun display(snapshot: Snapshot?) {
        val rows = snapshot?.rows ?: emptyList()
        if (model.rows != rows) {
            val identities = selected().map { Triple(it.pid, it.startTime, it.fileId) }.toSet()
            // JTable clears selection on a full model update. Restore identities before updating details.
            replacingRows = true
            try {
                model.replace(rows)
                table.clearSelection()
                rows.forEachIndexed { index, row ->
                    if (Triple(row.pid, row.startTime, row.fileId) in identities) {
                        val viewIndex = table.convertRowIndexToView(index)
                        if (viewIndex >= 0) table.addRowSelectionInterval(viewIndex, viewIndex)
                    }
                }
            } finally { replacingRows = false }
        }
        updateSelection()
        status.text = snapshot?.let {
            Text.get("summary", Text.status(it.status), format(it.time), it.rows.size, it.skipped)
        } ?: Text.get("ready")
    }
    private fun selected(): List<ProcessRow> = table.selectedRows.filter { it < table.rowCount }.mapNotNull { model.rows.getOrNull(table.convertRowIndexToModel(it)) }
    private fun updateSelection() {
        val rows = selected()
        terminate.isEnabled = !busy && !historical.isSelected && rows.isNotEmpty() && rows.all { it.canTerminate }
        val text = rows.joinToString("\n\n") { row ->
            listOf(
                "PID: ${row.pid}",
                "${Text.get("executable")}: ${row.executable.ifEmpty { Text.get("unavailable") }}",
                "${Text.get("started")}: ${row.startInstant()?.let(::format) ?: Text.get("unavailable")}",
                "${Text.get("owner")}: ${row.owner.ifEmpty { Text.get("unavailable") }}",
                "${Text.get("file")}: ${row.file}",
                "${Text.get("evidence")}: ${Text.get("evidence.${row.evidence}")}",
                "${Text.get("commandLine")}: ${row.commandLine() ?: Text.get("unavailable")}",
                "${Text.get("operation")}: ${if (row.canTerminate) Text.get("selectable") else Text.status(row.blockedReason)}",
            ).joinToString("\n")
        }
        if (details.text != text) {
            details.text = text
            details.caretPosition = 0
        }
        if (detailsPanel.isVisible != rows.isNotEmpty()) {
            detailsPanel.isVisible = rows.isNotEmpty()
            detailsPanel.parent?.revalidate()
            detailsPanel.parent?.repaint()
        }
    }
    private fun terminateSelected() {
        val rows = selected()
        if (busy || historical.isSelected || rows.isEmpty() || rows.any { !it.canTerminate }) return
        val names = rows.joinToString("\n") { "${it.name} (PID ${it.pid})" }
        if (Messages.showYesNoDialog(project, Text.get("confirm", names), Text.get("terminate"),
                Text.get("terminate"), Text.get("cancel"), Messages.getWarningIcon()) != Messages.YES) return
        val cancellation = Cancellation(); token = cancellation; setBusy(true, endingProcesses = true)
        status.text = Text.get("terminating")
        ApplicationManager.getApplication().executeOnPooledThread {
            val outcomes = mutableListOf<String>()
            for (row in rows) {
                if (cancellation.cancelled.get()) break
                val result = NativeClient.terminate(row, cancellation)
                outcomes.add("${row.name} (PID ${row.pid}): ${Text.status(result)}")
            }
            val result = if (!cancellation.cancelled.get()) NativeClient.scan(rows.first().file, cancellation) else Snapshot(emptyList(), "cancelled")
            later {
                current = result; display(result); setBusy(false)
                details.text = outcomes.joinToString("\n"); details.caretPosition = 0
                detailsPanel.isVisible = outcomes.isNotEmpty()
                detailsPanel.parent?.revalidate()
                detailsPanel.parent?.repaint()
            }
        }
    }
    private fun format(instant: java.time.Instant): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant)
    private class RowsModel : AbstractTableModel() {
        val rows = mutableListOf<ProcessRow>()
        private val columns = listOf("process", "pid", "owner", "started", "operation")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = Text.get(columns[column])
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.name.ifEmpty { Text.get("unavailable") }
                1 -> row.pid
                2 -> row.owner.ifEmpty { Text.get("unavailable") }
                3 -> row.startInstant()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: Text.get("unavailable")
                else -> if (row.canTerminate) Text.get("selectable") else Text.status(row.blockedReason)
            }
        }
        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 1) Long::class.javaObjectType else String::class.java
        fun add(row: ProcessRow) { val index = rows.size; rows.add(row); fireTableRowsInserted(index, index) }
        fun replace(value: List<ProcessRow>) { rows.clear(); rows.addAll(value); fireTableDataChanged() }
    }
}
