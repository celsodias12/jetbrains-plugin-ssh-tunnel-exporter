package dev.sshtunnelexporter.datagrip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelTargetMapperTest {
    private fun raw(
        enabled: Boolean = true,
        dbHost: String? = "10.0.0.5", dbPort: Int? = 5432,
        sshHost: String? = "bastion", sshPort: Int = 22, sshUser: String? = "deploy",
        authKind: AuthKind = AuthKind.PASSWORD, keyPath: String? = null,
        localPort: Int = 0,
    ) = RawTunnelData("ds1", enabled, dbHost, dbPort, sshHost, sshPort, sshUser, authKind, keyPath, localPort)

    @Test fun `disabled tunnel maps to NoTunnel`() {
        assertTrue(TunnelTargetMapper.map(raw(enabled = false)) is ReadResult.NoTunnel)
    }

    @Test fun `happy path maps all fields`() {
        val r = TunnelTargetMapper.map(raw()) as ReadResult.Ok
        assertEquals("10.0.0.5", r.target.dbHost)
        assertEquals(5432, r.target.dbPort)
        assertEquals("bastion", r.target.sshHost)
        assertEquals("deploy", r.target.sshUser)
        assertEquals(null, r.target.configuredLocalPort)
    }

    @Test fun `keyPath kept only for KEY_PAIR auth`() {
        val pw = TunnelTargetMapper.map(raw(authKind = AuthKind.PASSWORD, keyPath = "/k")) as ReadResult.Ok
        assertEquals(null, pw.target.keyPath)
        val kp = TunnelTargetMapper.map(raw(authKind = AuthKind.KEY_PAIR, keyPath = "/k")) as ReadResult.Ok
        assertEquals("/k", kp.target.keyPath)
    }

    @Test fun `configured local port over zero is preserved`() {
        val r = TunnelTargetMapper.map(raw(localPort = 7777)) as ReadResult.Ok
        assertEquals(7777, r.target.configuredLocalPort)
    }

    @Test fun `missing db host fails`() {
        assertTrue(TunnelTargetMapper.map(raw(dbHost = null)) is ReadResult.Failure)
    }

    @Test fun `blank ssh user fails`() {
        assertTrue(TunnelTargetMapper.map(raw(sshUser = "  ")) is ReadResult.Failure)
    }

    @Test fun `non-positive ssh port falls back to 22`() {
        val r = TunnelTargetMapper.map(raw(sshPort = 0)) as ReadResult.Ok
        assertEquals(22, r.target.sshPort)
    }
}
