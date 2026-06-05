package dev.sshtunnelexporter.datagrip.read

import com.intellij.database.Dbms
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.JdbcUrlParserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.remote.AuthType
import dev.sshtunnelexporter.datagrip.model.AuthKind
import dev.sshtunnelexporter.datagrip.model.DbKind
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
        val (dbHost, dbPort, dbName) = extractDbCoords(lds)
        return RawTunnelData(
            dsName = lds.name,
            sshConfigName = ssh?.customName?.takeIf { it.isNotBlank() },
            tunnelEnabled = enabled,
            dbHost = dbHost,
            dbPort = dbPort,
            sshHost = ssh?.host,
            sshPort = ssh?.port ?: 22,
            sshUser = ssh?.username,
            authKind = ssh?.authType.toAuthKind(),
            keyPath = ssh?.keyPath,
            configuredLocalPort = tunnel?.localPort ?: 0,
            dbKind = lds.dbKind(),
            dbUser = lds.username?.takeIf { it.isNotBlank() },
            dbName = dbName,
        )
    }

    /** Reads DB host/port/database from the parsed JDBC URL via DataGrip's own driver-aware parser. */
    private fun extractDbCoords(lds: LocalDataSource): Triple<String?, Int?, String?> {
        val cfg = lds.connectionConfig ?: return Triple(null, null, null)
        val parser = JdbcUrlParserUtil.parsed(cfg) ?: return Triple(null, null, null)
        val host = parser.getParameter("host")?.takeIf { it.isNotBlank() }
        val port = parser.getParameter("port")?.toIntOrNull()
        val db = (parser.getParameter("database")
            ?: parser.getParameter("databaseName")
            ?: parser.getParameter("path")?.removePrefix("/"))
            ?.takeIf { it.isNotBlank() }
        return Triple(host, port, db)
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

/** Reduce DataGrip's [Dbms] to the connect-CLI families we know how to render. */
private fun LocalDataSource.dbKind(): DbKind {
    val d = dbms ?: return DbKind.OTHER
    return when {
        d.isPostgres || d == Dbms.GREENPLUM || d == Dbms.REDSHIFT || d == Dbms.COCKROACH -> DbKind.POSTGRES
        d.isMysql || d == Dbms.MARIA || d == Dbms.MYSQL_AURORA || d == Dbms.MEMSQL -> DbKind.MYSQL
        d == Dbms.MSSQL || d == Dbms.MSSQL_LOCALDB || d == Dbms.AZURE || d == Dbms.SYNAPSE -> DbKind.MSSQL
        d.isOracle -> DbKind.ORACLE
        d == Dbms.MONGO -> DbKind.MONGO
        d.isClickHouse -> DbKind.CLICKHOUSE
        else -> DbKind.OTHER
    }
}
