package dev.sshtunnelexporter.datagrip.db

import dev.sshtunnelexporter.datagrip.model.DbKind
import dev.sshtunnelexporter.datagrip.model.TunnelTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DbCliBuilderTest {
    private fun target(kind: DbKind, dbUser: String? = "app", dbName: String? = "shop") =
        TunnelTarget(
            dbHost = "db.internal", dbPort = 5432, sshHost = "bastion", sshPort = 22, sshUser = "deploy",
            keyPath = null, configuredLocalPort = null, defaultAlias = "db",
            dbKind = kind, dbUser = dbUser, dbName = dbName,
        )

    @Test fun `postgres with user and db`() {
        assertEquals("psql -h localhost -p 5432 -U app shop", DbCliBuilder.command(target(DbKind.POSTGRES), 5432))
    }

    @Test fun `postgres without user or db`() {
        assertEquals("psql -h localhost -p 6543", DbCliBuilder.command(target(DbKind.POSTGRES, null, null), 6543))
    }

    @Test fun `mysql forces TCP host`() {
        assertEquals("mysql -h 127.0.0.1 -P 3307 -u app shop", DbCliBuilder.command(target(DbKind.MYSQL), 3307))
    }

    @Test fun `mssql uses comma port and -d`() {
        assertEquals("sqlcmd -S localhost,1433 -U app -d shop", DbCliBuilder.command(target(DbKind.MSSQL), 1433))
    }

    @Test fun `mongo builds a uri`() {
        assertEquals("mongosh \"mongodb://localhost:27018/shop\"", DbCliBuilder.command(target(DbKind.MONGO), 27018))
        assertEquals("mongosh \"mongodb://localhost:27018\"", DbCliBuilder.command(target(DbKind.MONGO, dbName = null), 27018))
    }

    @Test fun `clickhouse long flags`() {
        assertEquals(
            "clickhouse-client --host localhost --port 9000 --user app --database shop",
            DbCliBuilder.command(target(DbKind.CLICKHOUSE), 9000),
        )
    }

    @Test fun `oracle needs both user and db, else null`() {
        assertEquals("sqlplus app@//localhost:1521/shop", DbCliBuilder.command(target(DbKind.ORACLE), 1521))
        assertNull(DbCliBuilder.command(target(DbKind.ORACLE, dbUser = null), 1521))
        assertNull(DbCliBuilder.command(target(DbKind.ORACLE, dbName = null), 1521))
    }

    @Test fun `unknown dbms yields no command`() {
        assertNull(DbCliBuilder.command(target(DbKind.OTHER), 5432))
    }
}
