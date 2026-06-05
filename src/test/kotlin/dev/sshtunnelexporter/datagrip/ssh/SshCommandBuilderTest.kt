package dev.sshtunnelexporter.datagrip.ssh

import dev.sshtunnelexporter.datagrip.model.TunnelTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SshCommandBuilderTest {
    private fun target(
        dbHost: String = "10.0.0.5", dbPort: Int = 5432,
        sshHost: String = "bastion.example.com", sshPort: Int = 22,
        sshUser: String = "deploy", keyPath: String? = null,
    ) = TunnelTarget(dbHost, dbPort, sshHost, sshPort, sshUser, keyPath, configuredLocalPort = null)

    @Test fun `basic foreground and background, default ssh port omitted`() {
        val c = SshCommandBuilder.build(target(), localPort = 5432)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c.foreground)
        assertEquals("ssh -fN -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c.background)
    }

    @Test fun `non-default ssh port adds -p`() {
        val c = SshCommandBuilder.build(target(sshPort = 2222), localPort = 6543)
        assertEquals("ssh -N -L 6543:10.0.0.5:5432 deploy@bastion.example.com -p 2222", c.foreground)
    }

    @Test fun `key path adds -i`() {
        val c = SshCommandBuilder.build(target(keyPath = "/home/u/.ssh/id_ed25519"), localPort = 5432)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -i /home/u/.ssh/id_ed25519", c.foreground)
    }

    @Test fun `key path with spaces is quoted`() {
        val c = SshCommandBuilder.build(target(keyPath = "/home/u/My Keys/id"), localPort = 5432)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -i \"/home/u/My Keys/id\"", c.foreground)
    }

    @Test fun `blank key path is ignored`() {
        val c = SshCommandBuilder.build(target(keyPath = "  "), localPort = 5432)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com", c.foreground)
    }

    @Test fun `non-default ssh port and key path appear together in order`() {
        val c = SshCommandBuilder.build(target(sshPort = 2222, keyPath = "/home/u/.ssh/id"), localPort = 5432)
        assertEquals("ssh -N -L 5432:10.0.0.5:5432 deploy@bastion.example.com -p 2222 -i /home/u/.ssh/id", c.foreground)
    }
}
