package dev.sshtunnelexporter.datagrip.ssh

import dev.sshtunnelexporter.datagrip.model.DbKind
import dev.sshtunnelexporter.datagrip.model.TunnelTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SshCommandBuilderTest {
    private fun target(
        dbHost: String = "10.0.0.5", dbPort: Int = 5432,
        sshHost: String = "bastion.example.com", sshPort: Int = 22,
        sshUser: String = "deploy", keyPath: String? = null,
        defaultAlias: String = "my-db",
    ) = TunnelTarget(
        dbHost, dbPort, sshHost, sshPort, sshUser, keyPath,
        configuredLocalPort = null, defaultAlias = defaultAlias,
        dbKind = DbKind.OTHER, dbUser = null, dbName = null,
    )

    /** Options with every numeric -o disabled — asserts the bare command shape without the noise. */
    private val bare = SshOptions(connectTimeout = null, serverAliveInterval = null, serverAliveCountMax = null)

    // ---- command() ----

    @Test fun `bare foreground, default ssh port omitted`() {
        val c = SshCommandBuilder.command(target(), localPort = 5432, opts = bare)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c)
    }

    @Test fun `default options are minimal, no -o flags`() {
        val c = SshCommandBuilder.command(target(), localPort = 5432, opts = SshOptions())
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c)
    }

    @Test fun `background and compression cluster with -N as -fNC`() {
        val c = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(background = true, compression = true))
        assertEquals("ssh -fNC -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c)
    }

    @Test fun `verbose adds -v after the flag cluster`() {
        val withN = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(verbose = true))
        assertEquals("ssh -N -v -L 5432:10.0.0.5:5432 deploy@bastion.example.com", withN)
        val noN = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(noShell = false, verbose = true))
        assertEquals("ssh -v -L 5432:10.0.0.5:5432 deploy@bastion.example.com", noN)
    }

    @Test fun `no short flags when -N off and nothing else`() {
        val c = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(noShell = false))
        assertEquals("ssh -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c)
    }

    @Test fun `connect timeout emitted when positive, omitted at zero`() {
        val on = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(connectTimeout = 5))
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -o ConnectTimeout=5", on)
        val off = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(connectTimeout = 0))
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", off)
    }

    @Test fun `identitiesOnly requires a key`() {
        val withKey = SshCommandBuilder.command(target(keyPath = "/k"), localPort = 5432, opts = bare.copy(identitiesOnly = true))
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -i /k -o IdentitiesOnly=yes", withKey)
        val noKey = SshCommandBuilder.command(target(), localPort = 5432, opts = bare.copy(identitiesOnly = true))
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", noKey)
    }

    @Test fun `exitOnForwardFailure sits between connect-timeout and keep-alive`() {
        val c = SshCommandBuilder.command(
            target(), localPort = 5432,
            opts = SshOptions(exitOnForwardFailure = true, connectTimeout = 10, serverAliveInterval = 60, serverAliveCountMax = 3),
        )
        assertEquals(
            "ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -o ConnectTimeout=10 -o ExitOnForwardFailure=yes -o ServerAliveInterval=60 -o ServerAliveCountMax=3",
            c,
        )
    }

    @Test fun `non-default ssh port adds -p`() {
        val c = SshCommandBuilder.command(target(sshPort = 2222), localPort = 6543, opts = bare)
        assertEquals("ssh -N -L 6543:10.0.0.5:5432 deploy@bastion.example.com -p 2222", c)
    }

    @Test fun `key path adds -i, quoted only when it has spaces`() {
        val plain = SshCommandBuilder.command(target(keyPath = "/home/u/.ssh/id_ed25519"), localPort = 5432, opts = bare)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -i /home/u/.ssh/id_ed25519", plain)
        val spaced = SshCommandBuilder.command(target(keyPath = "/home/u/My Keys/id"), localPort = 5432, opts = bare)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -i \"/home/u/My Keys/id\"", spaced)
    }

    @Test fun `blank key path is ignored`() {
        val c = SshCommandBuilder.command(target(keyPath = "  "), localPort = 5432, opts = bare)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c)
    }

    @Test fun `every flag and option in canonical order`() {
        val c = SshCommandBuilder.command(
            target(sshPort = 2222, keyPath = "/home/u/.ssh/id"),
            localPort = 5432,
            opts = SshOptions(
                background = true, compression = true, verbose = true, identitiesOnly = true,
                exitOnForwardFailure = true, connectTimeout = 15, serverAliveInterval = 30, serverAliveCountMax = 5,
            ),
        )
        assertEquals(
            "ssh -fNC -v -L 5432:10.0.0.5:5432 deploy@bastion.example.com -p 2222 -i /home/u/.ssh/id -o ConnectTimeout=15 -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=5",
            c,
        )
    }

    @Test fun `multiline command breaks one flag per line with backslashes`() {
        val c = SshCommandBuilder.commandMultiline(
            target(sshPort = 2222, keyPath = "/home/u/.ssh/id"),
            localPort = 5432,
            opts = SshOptions(connectTimeout = 10, serverAliveInterval = 60, serverAliveCountMax = 3),
        )
        assertEquals(
            """
            ssh -N \
                -L 5432:10.0.0.5:5432 \
                deploy@bastion.example.com \
                -p 2222 \
                -i /home/u/.ssh/id \
                -o ConnectTimeout=10 \
                -o ServerAliveInterval=60 \
                -o ServerAliveCountMax=3
            """.trimIndent(),
            c,
        )
    }

    // ---- sshConfig() ----

    @Test fun `ssh config minimal stanza`() {
        val cfg = SshCommandBuilder.sshConfig(target(), localPort = 5432, alias = "db-tunnel", opts = bare)
        assertEquals(
            """
            Host db-tunnel
                HostName bastion.example.com
                User deploy
                LocalForward 5432 10.0.0.5:5432
            """.trimIndent(),
            cfg,
        )
    }

    @Test fun `ssh config full stanza, alias whitespace sanitised`() {
        val cfg = SshCommandBuilder.sshConfig(
            target(sshPort = 2222, keyPath = "/home/u/.ssh/id"),
            localPort = 6543,
            alias = "db tunnel",
            opts = SshOptions(
                compression = true, verbose = true, identitiesOnly = true, exitOnForwardFailure = true,
                connectTimeout = 10, serverAliveInterval = 60, serverAliveCountMax = 3,
            ),
        )
        assertEquals(
            """
            Host db-tunnel
                HostName bastion.example.com
                User deploy
                Port 2222
                IdentityFile /home/u/.ssh/id
                IdentitiesOnly yes
                LocalForward 6543 10.0.0.5:5432
                Compression yes
                ConnectTimeout 10
                ExitOnForwardFailure yes
                ServerAliveInterval 60
                ServerAliveCountMax 3
                LogLevel DEBUG
            """.trimIndent(),
            cfg,
        )
    }

    @Test fun `alias token sanitises whitespace and blanks to default`() {
        assertEquals("my-db", SshCommandBuilder.aliasToken("  my   db "))
        assertEquals("db-tunnel", SshCommandBuilder.aliasToken("   "))
    }
}
