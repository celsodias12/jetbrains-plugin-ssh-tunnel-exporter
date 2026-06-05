package dev.sshtunnelexporter.datagrip.action

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import dev.sshtunnelexporter.datagrip.model.ReadResult
import dev.sshtunnelexporter.datagrip.read.DataSourceReader
import dev.sshtunnelexporter.datagrip.ui.SshTunnelDialog

private const val NOTIFY_GROUP = "SSH Tunnel Exporter"

private fun localDataSourceFrom(element: Any?): LocalDataSource? = when (element) {
    is LocalDataSource -> element
    is DbDataSource -> element.delegateDataSource as? LocalDataSource
    else -> null
}

private fun hasEnabledTunnel(lds: LocalDataSource): Boolean =
    lds.sshConfiguration?.let { it.isEnabled && !it.isEmpty } == true

private fun notify(project: Project, message: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFY_GROUP)
        .createNotification(message, type)
        .notify(project)
}

private fun present(project: Project, lds: LocalDataSource) {
    when (val r = DataSourceReader(project).read(lds)) {
        is ReadResult.Ok -> SshTunnelDialog(project, r.target).show()
        is ReadResult.NoTunnel -> notify(project, "“${r.dsName}” has no SSH tunnel configured.", NotificationType.WARNING)
        is ReadResult.Failure -> notify(project, r.message, NotificationType.ERROR)
    }
}

/** Database tool-window context menu, scoped to the selected data source. */
class ExportSshFromDbViewAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val lds = localDataSourceFrom(e.getData(CommonDataKeys.PSI_ELEMENT))
        e.presentation.isEnabledAndVisible = lds != null && hasEnabledTunnel(lds)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val lds = localDataSourceFrom(e.getData(CommonDataKeys.PSI_ELEMENT)) ?: return
        present(project, lds)
    }
}

/** Tools menu: pick from all data sources that have an enabled SSH tunnel. */
class ExportSshFromToolsAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val candidates = DbPsiFacade.getInstance(project).dataSources
            .mapNotNull { it.delegateDataSource as? LocalDataSource }
            .filter { hasEnabledTunnel(it) }
        if (candidates.isEmpty()) {
            notify(project, "No data sources with an SSH tunnel were found.", NotificationType.WARNING)
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(candidates)
            .setTitle("Select Data Source")
            .setRenderer(SimpleListCellRenderer.create<LocalDataSource> { label, value, _ -> label.text = value?.name ?: "" })
            .setItemChosenCallback { present(project, it) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }
}
