package dev.sshtunnelexporter.datagrip.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.sshtunnelexporter.datagrip.db.DbCliBuilder
import dev.sshtunnelexporter.datagrip.model.TunnelTarget
import dev.sshtunnelexporter.datagrip.ssh.SshCommandBuilder
import dev.sshtunnelexporter.datagrip.ssh.SshOptions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SshTunnelDialog(project: Project, private val target: TunnelTarget) : DialogWrapper(project) {

    private val portField = JBTextField((target.configuredLocalPort ?: target.dbPort).toString(), 8)
    private val aliasField = JBTextField(SshCommandBuilder.aliasToken(target.defaultAlias), 24)

    private val noShellCheck = JBCheckBox("No shell (-N)", true)
    private val backgroundCheck = JBCheckBox("Background (-f)", false)
    private val compressionCheck = JBCheckBox("Compression (-C)", false)
    private val verboseCheck = JBCheckBox("Verbose (-v)", false)
    private val identitiesOnlyCheck = JBCheckBox("IdentitiesOnly", false)
    private val exitOnForwardFailureCheck = JBCheckBox("ExitOnForwardFailure", false)
    private val connectTimeoutField = JBTextField("", 6).apply { emptyText.text = "e.g. 10" }
    private val serverAliveIntervalField = JBTextField("", 6).apply { emptyText.text = "e.g. 60" }
    private val serverAliveCountMaxField = JBTextField("", 6).apply { emptyText.text = "e.g. 3" }

    private val commandArea = JBTextArea(3, 40).apply {
        isEditable = false
        lineWrap = false
    }
    private val dbCliField = JBTextField().apply { isEditable = false; columns = 40 }
    private val configArea = JBTextArea(6, 46).apply {
        isEditable = false
        lineWrap = false
    }
    private val showCli = DbCliBuilder.command(target, target.configuredLocalPort ?: target.dbPort) != null

    private val blue = JBColor(Color(0x3D, 0x7E, 0xF0), Color(0x5A, 0x9B, 0xFF))
    private val teal = JBColor(Color(0x12, 0x96, 0x88), Color(0x20, 0xC9, 0xB6))
    private val flowLocalBox = Pill("", blue, ColorUtil.withAlpha(blue, 0.14))
    private val flowCaption = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    init {
        title = "SSH Tunnel Command"
        listOf(portField, aliasField, connectTimeoutField, serverAliveIntervalField, serverAliveCountMaxField)
            .forEach { it.onChange(::recompute) }
        listOf(noShellCheck, backgroundCheck, compressionCheck, verboseCheck, identitiesOnlyCheck, exitOnForwardFailureCheck)
            .forEach { box -> box.addItemListener { recompute() } }
        init()
        isResizable = true
        recompute()
    }

    private fun currentLocalPort(): Int = portField.text.trim().toIntOrNull() ?: target.dbPort

    private fun currentOptions() = SshOptions(
        noShell = noShellCheck.isSelected,
        background = backgroundCheck.isSelected,
        compression = compressionCheck.isSelected,
        verbose = verboseCheck.isSelected,
        identitiesOnly = identitiesOnlyCheck.isSelected,
        exitOnForwardFailure = exitOnForwardFailureCheck.isSelected,
        connectTimeout = connectTimeoutField.text.trim().toIntOrNull(),
        serverAliveInterval = serverAliveIntervalField.text.trim().toIntOrNull(),
        serverAliveCountMax = serverAliveCountMaxField.text.trim().toIntOrNull(),
    )

    private fun recompute() {
        val port = currentLocalPort()
        val opts = currentOptions()
        val cmd = SshCommandBuilder.commandMultiline(target, port, opts)
        commandArea.text = cmd
        commandArea.rows = cmd.count { it == '\n' } + 1
        val cfg = SshCommandBuilder.sshConfig(target, port, aliasField.text, opts)
        configArea.text = cfg
        configArea.rows = maxOf(4, cfg.count { it == '\n' } + 1) // grow to show every line, no inner scroll
        flowLocalBox.text = "your machine :$port"
        flowCaption.text = "Traffic on your port $port goes through ${target.sshUser}@${target.sshHost} and reaches ${target.dbHost}:${target.dbPort}"
        if (showCli) dbCliField.text = DbCliBuilder.command(target, port).orEmpty()
    }

    override fun createCenterPanel(): JComponent {
        recompute() // populate text + size the config area before we measure
        val content = panel {
            group("How it works") {
                row { cell(howItWorksCard()).align(AlignX.FILL).resizableColumn() }
            }
            row("Local port:") { cell(portField) }
            if (target.configuredLocalPort == null) {
                row { comment("No fixed local port configured; DataGrip auto-assigns one at connect time. Using the database port — change it if it is taken.") }
            }
            if (target.keyPath == null) {
                row { comment("Password / agent / OpenSSH-config auth: no <code>-i</code> emitted; ssh will prompt or use your agent.") }
            }
            group("Options") {
                row { cell(JBCheckBox("Local forward (-L) — always on", true).apply { isEnabled = false }) }
                row { comment("The tunnel itself: forwards your local port to ${target.dbHost}:${target.dbPort}. Always present, can't be disabled.") }
                row {
                    cell(noShellCheck)
                    cell(backgroundCheck)
                    cell(compressionCheck)
                    cell(verboseCheck)
                    cell(exitOnForwardFailureCheck)
                    if (target.keyPath != null) cell(identitiesOnlyCheck)
                }
                collapsibleGroup("Advanced") {
                    row {
                        label("ConnectTimeout (s):")
                        cell(connectTimeoutField)
                        label("ServerAlive (interval / countMax):")
                        cell(serverAliveIntervalField)
                        cell(serverAliveCountMaxField)
                    }
                }.apply { expanded = false }
            }
            row { cell(buildTabs()).align(AlignX.FILL).resizableColumn() }
        }.apply { border = JBUI.Borders.empty(10) }

        val natural = content.preferredSize
        // Auto-fit width to the longest generated output so the full command/config shows without manual resize.
        val want = longestOutputWidth() + JBUI.scale(140)
        val width = want.coerceIn(natural.width, JBUI.scale(1100))
        content.preferredSize = Dimension(width, natural.height)
        return JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            preferredSize = Dimension(width + JBUI.scale(24), minOf(natural.height + JBUI.scale(4), JBUI.scale(680)))
        }
    }

    /** Width (px) of the longest line across the command, CLI and ~/.ssh/config outputs at the current state. */
    private fun longestOutputWidth(): Int {
        val port = currentLocalPort()
        val opts = currentOptions()
        val cmd = SshCommandBuilder.commandMultiline(target, port, opts)
        val cli = if (showCli) DbCliBuilder.command(target, port).orEmpty() else ""
        val cfg = SshCommandBuilder.sshConfig(target, port, aliasField.text, opts)
        val fmCmd = commandArea.getFontMetrics(commandArea.font)
        val fmCli = dbCliField.getFontMetrics(dbCliField.font)
        val fmCfg = configArea.getFontMetrics(configArea.font)
        val cmdW = cmd.lineSequence().maxOf { fmCmd.stringWidth(it) }
        val cliW = if (cli.isEmpty()) 0 else fmCli.stringWidth(cli)
        val cfgW = cfg.lineSequence().maxOf { fmCfg.stringWidth(it) }
        return maxOf(cmdW, cliW, cfgW)
    }

    // ---- output tabs ----

    private fun buildTabs(): JBTabbedPane = JBTabbedPane().apply {
        addTab("Open tunnel", tunnelTab())
        if (showCli) addTab("Connect to DB", cliTab())
        addTab("SSH config", configTab())
    }

    private fun tunnelTab(): JComponent = panel {
        row { scrollCell(commandArea).align(AlignX.FILL).resizableColumn() }
        row { button("Copy command") { copy(commandArea.text, it.source as JComponent) } }
    }.apply { border = JBUI.Borders.empty(8) }

    private fun cliTab(): JComponent = panel {
        row { cell(dbCliField).align(AlignX.FILL).resizableColumn() }
        row { button("Copy command") { copy(dbCliField.text, it.source as JComponent) } }
    }.apply { border = JBUI.Borders.empty(8) }

    private fun configTab(): JComponent = panel {
        row("Host alias:") { cell(aliasField).align(AlignX.FILL).resizableColumn() }
        row { scrollCell(configArea).align(AlignX.FILL).resizableColumn() }
        row { comment("<code>-N</code>/<code>-f</code> have no <code>~/.ssh/config</code> form — invoke e.g. <code>ssh -fN &lt;alias&gt;</code>.") }
        row { button("Copy config") { copy(configArea.text, it.source as JComponent) } }
    }.apply { border = JBUI.Borders.empty(8) }

    // ---- "how it works" card ----

    private fun howItWorksCard(): JComponent {
        val inner = panel {
            row { cell(flowDiagram()) }
            row { cell(flowCaption) }
        }.apply { isOpaque = false }
        return Card(inner, ColorUtil.withAlpha(UIUtil.getLabelForeground(), 0.06), JBColor.border())
    }

    private fun flowDiagram(): JPanel {
        val mid = Pill("${target.sshUser}@${target.sshHost}", UIUtil.getLabelForeground(), ColorUtil.withAlpha(UIUtil.getLabelForeground(), 0.07))
        val remote = Pill("${target.dbHost}:${target.dbPort}", teal, ColorUtil.withAlpha(teal, 0.14))
        val arrowSsh = JBLabel("─ SSH →").apply { foreground = UIUtil.getContextHelpForeground() }
        val arrow = JBLabel("→").apply { foreground = UIUtil.getContextHelpForeground() }
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            add(flowLocalBox)
            add(arrowSsh)
            add(mid)
            add(arrow)
            add(remote)
        }
    }

    // ---- helpers ----

    private fun copy(text: String, near: JComponent) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("Copied", MessageType.INFO, null)
            .setFadeoutTime(1000)
            .createBalloon()
            .show(RelativePoint.getNorthEastOf(near), Balloon.Position.above)
    }

    private fun JBTextField.onChange(block: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = block()
            override fun removeUpdate(e: DocumentEvent) = block()
            override fun changedUpdate(e: DocumentEvent) = block()
        })
    }

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, "Close")
        return arrayOf(okAction)
    }

    /** A rounded, softly-filled "pill" label used for the flow-diagram nodes. */
    private class Pill(text: String, private val stroke: Color, private val fill: Color) : JBLabel(text) {
        init {
            foreground = stroke
            font = JBFont.create(Font(Font.MONOSPACED, Font.BOLD, JBFont.label().size))
            border = JBUI.Borders.empty(3, 9)
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(12)
            val w = width - JBUI.scale(1)
            val h = height - JBUI.scale(1)
            g2.color = fill
            g2.fillRoundRect(0, 0, w, h, arc, arc)
            g2.color = stroke
            g2.drawRoundRect(0, 0, w, h, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    /** A rounded card surface that hosts the flow diagram. */
    private class Card(content: JComponent, private val bg: Color, private val line: Color) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = JBUI.Borders.empty(10, 12)
            add(content, BorderLayout.CENTER)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(14)
            val w = width - JBUI.scale(1)
            val h = height - JBUI.scale(1)
            g2.color = bg
            g2.fillRoundRect(0, 0, w, h, arc, arc)
            g2.color = line
            g2.drawRoundRect(0, 0, w, h, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }
}
