package dev.sshtunnelexporter.datagrip.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.sshtunnelexporter.datagrip.model.TunnelTarget
import dev.sshtunnelexporter.datagrip.ssh.SshCommandBuilder
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SshTunnelDialog(project: Project, private val target: TunnelTarget) : DialogWrapper(project) {

    private val portField = JBTextField((target.configuredLocalPort ?: target.dbPort).toString(), 8)
    private val commandField = JBTextField().apply { isEditable = false }
    private val backgroundCheck = JBCheckBox("Run in background (-f, detaches)", false)

    init {
        title = "SSH Tunnel Command"
        portField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = recompute()
            override fun removeUpdate(e: DocumentEvent) = recompute()
            override fun changedUpdate(e: DocumentEvent) = recompute()
        })
        backgroundCheck.addItemListener { recompute() }
        init()
        recompute()
    }

    private fun currentLocalPort(): Int = portField.text.trim().toIntOrNull() ?: target.dbPort

    private fun recompute() {
        val cmds = SshCommandBuilder.build(target, currentLocalPort())
        commandField.text = if (backgroundCheck.isSelected) cmds.background else cmds.foreground
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Local port:") { cell(portField) }
        if (target.configuredLocalPort == null) {
            row { comment("No fixed local port configured; DataGrip auto-assigns one at connect time. Using the database port — change it if it is taken.") }
        }
        if (target.keyPath == null) {
            row { comment("Password / agent / OpenSSH-config auth: no <code>-i</code> emitted; ssh will prompt or use your agent.") }
        }
        row("Command:") { cell(commandField).align(AlignX.FILL).resizableColumn() }
        row { cell(backgroundCheck) }
        row { button("Copy") { copy(commandField.text) } }
    }.apply { border = JBUI.Borders.empty(10) }

    private fun copy(text: String) =
        CopyPasteManager.getInstance().setContents(StringSelection(text))

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, "Close")
        return arrayOf(okAction)
    }
}
