package dev.sshtunnelexporter.datagrip.db

import dev.sshtunnelexporter.datagrip.model.DbKind
import dev.sshtunnelexporter.datagrip.model.TunnelTarget

/**
 * Best-effort native-client command to connect through the *local* end of the tunnel
 * (i.e. against localhost:[localPort]). Returns null when the DBMS has no obvious CLI,
 * or when a required piece (Oracle service/user) is missing.
 */
object DbCliBuilder {
    fun command(target: TunnelTarget, localPort: Int): String? {
        val user = target.dbUser?.takeIf { it.isNotBlank() }
        val db = target.dbName?.takeIf { it.isNotBlank() }
        return when (target.dbKind) {
            DbKind.POSTGRES -> buildString {
                append("psql -h localhost -p ").append(localPort)
                user?.let { append(" -U ").append(it) }
                db?.let { append(' ').append(it) }
            }
            DbKind.MYSQL -> buildString {
                append("mysql -h 127.0.0.1 -P ").append(localPort)
                user?.let { append(" -u ").append(it) }
                db?.let { append(' ').append(it) }
            }
            DbKind.MSSQL -> buildString {
                append("sqlcmd -S localhost,").append(localPort)
                user?.let { append(" -U ").append(it) }
                db?.let { append(" -d ").append(it) }
            }
            DbKind.MONGO -> "mongosh \"mongodb://localhost:$localPort" + (db?.let { "/$it" } ?: "") + "\""
            DbKind.CLICKHOUSE -> buildString {
                append("clickhouse-client --host localhost --port ").append(localPort)
                user?.let { append(" --user ").append(it) }
                db?.let { append(" --database ").append(it) }
            }
            DbKind.ORACLE -> if (user != null && db != null) "sqlplus $user@//localhost:$localPort/$db" else null
            DbKind.OTHER -> null
        }
    }
}
