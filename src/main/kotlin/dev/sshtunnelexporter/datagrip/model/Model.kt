package dev.sshtunnelexporter.datagrip.model

/** Auth kinds we care about, mapped from com.intellij.remote.AuthType at the IDE edge. */
enum class AuthKind { PASSWORD, KEY_PAIR, OPEN_SSH, OTHER }

/** DBMS families we can emit a connect-CLI for; everything else is OTHER (no CLI shown). */
enum class DbKind { POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, CLICKHOUSE, OTHER }

/** Raw values pulled from the DataGrip API; all IDE types already reduced to primitives. */
data class RawTunnelData(
    val dsName: String,
    val sshConfigName: String?,   // custom name of a shared SSH config, when one is set
    val tunnelEnabled: Boolean,
    val dbHost: String?,
    val dbPort: Int?,
    val sshHost: String?,
    val sshPort: Int,
    val sshUser: String?,
    val authKind: AuthKind,
    val keyPath: String?,
    val configuredLocalPort: Int, // 0 == auto
    val dbKind: DbKind,
    val dbUser: String?,
    val dbName: String?,
)

/** Validated, ready-to-render tunnel description. */
data class TunnelTarget(
    val dbHost: String,
    val dbPort: Int,
    val sshHost: String,
    val sshPort: Int,
    val sshUser: String,
    val keyPath: String?,            // non-null only for KEY_PAIR auth
    val configuredLocalPort: Int?,   // null == auto (DataGrip assigns at connect time)
    val defaultAlias: String,        // seed for the ~/.ssh/config Host token: SSH config name, else data source name
    val dbKind: DbKind,              // drives the optional connect-CLI hint
    val dbUser: String?,             // DB account, when known
    val dbName: String?,             // target database/schema, when known
)

sealed interface ReadResult {
    data class Ok(val target: TunnelTarget) : ReadResult
    data class NoTunnel(val dsName: String) : ReadResult
    data class Failure(val message: String, val cause: Throwable? = null) : ReadResult
}

object TunnelTargetMapper {
    fun map(d: RawTunnelData): ReadResult {
        if (!d.tunnelEnabled) return ReadResult.NoTunnel(d.dsName)
        val dbHost = d.dbHost?.takeIf { it.isNotBlank() } ?: return fail(d, "database host")
        val dbPort = d.dbPort ?: return fail(d, "database port")
        val sshHost = d.sshHost?.takeIf { it.isNotBlank() } ?: return fail(d, "SSH host")
        val sshUser = d.sshUser?.takeIf { it.isNotBlank() } ?: return fail(d, "SSH user")
        val keyPath = d.keyPath?.takeIf { d.authKind == AuthKind.KEY_PAIR && it.isNotBlank() }
        return ReadResult.Ok(
            TunnelTarget(
                dbHost = dbHost,
                dbPort = dbPort,
                sshHost = sshHost,
                sshPort = if (d.sshPort > 0) d.sshPort else 22,
                sshUser = sshUser,
                keyPath = keyPath,
                configuredLocalPort = d.configuredLocalPort.takeIf { it > 0 },
                defaultAlias = d.sshConfigName?.takeIf { it.isNotBlank() } ?: d.dsName,
                dbKind = d.dbKind,
                dbUser = d.dbUser?.takeIf { it.isNotBlank() },
                dbName = d.dbName?.takeIf { it.isNotBlank() },
            )
        )
    }

    private fun fail(d: RawTunnelData, what: String) =
        ReadResult.Failure("Could not determine $what for “${d.dsName}”.")
}
