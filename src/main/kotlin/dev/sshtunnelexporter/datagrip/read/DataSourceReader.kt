package dev.sshtunnelexporter.datagrip.read

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.JdbcUrlParserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.remote.AuthType
import dev.sshtunnelexporter.datagrip.model.AuthKind
import dev.sshtunnelexporter.datagrip.model.RawTunnelData
import dev.sshtunnelexporter.datagrip.model.ReadResult
import dev.sshtunnelexporter.datagrip.model.TunnelTargetMapper

class DataSourceReader(private val project: Project) {

    fun read(lds: LocalDataSource): ReadResult {
        val raw = try {
            extract(lds)
        } catch (t: Throwable) {
            LOG.warn("Failed to read SSH config for data source '${lds.name}'", t)
            return ReadResult.Failure(
                "Failed to read the SSH configuration for “${lds.name}” — the DataGrip API may have changed.",
                t,
            )
        }
        return TunnelTargetMapper.map(raw)
    }

    private fun extract(lds: LocalDataSource): RawTunnelData {
        val tunnel = lds.sshConfiguration
        val enabled = tunnel != null && tunnel.isEnabled && !tunnel.isEmpty
        val ssh = if (enabled) tunnel!!.getSshConfig(project) else null
        val (dbHost, dbPort) = extractDbHostPort(lds)
        return RawTunnelData(
            dsName = lds.name,
            tunnelEnabled = enabled,
            dbHost = dbHost,
            dbPort = dbPort,
            sshHost = ssh?.host,
            sshPort = ssh?.port ?: 22,
            sshUser = ssh?.username,
            authKind = ssh?.authType.toAuthKind(),
            keyPath = ssh?.keyPath,
            configuredLocalPort = tunnel?.localPort ?: 0,
        )
    }

    /** Reads DB host/port from the parsed JDBC URL via DataGrip's own driver-aware parser. */
    private fun extractDbHostPort(lds: LocalDataSource): Pair<String?, Int?> {
        val cfg = lds.connectionConfig ?: return null to null
        val parser = JdbcUrlParserUtil.parsed(cfg) ?: return null to null
        val host = parser.getParameter("host")?.takeIf { it.isNotBlank() }
        val port = parser.getParameter("port")?.toIntOrNull()
        return host to port
    }

    private companion object {
        val LOG = logger<DataSourceReader>()
    }
}

private fun AuthType?.toAuthKind(): AuthKind = when (this) {
    AuthType.PASSWORD -> AuthKind.PASSWORD
    AuthType.KEY_PAIR -> AuthKind.KEY_PAIR
    AuthType.OPEN_SSH -> AuthKind.OPEN_SSH
    null -> AuthKind.OTHER
}
