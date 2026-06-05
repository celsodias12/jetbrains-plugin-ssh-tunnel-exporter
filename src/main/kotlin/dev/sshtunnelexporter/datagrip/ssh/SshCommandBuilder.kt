package dev.sshtunnelexporter.datagrip.ssh

import dev.sshtunnelexporter.datagrip.model.TunnelTarget

data class SshCommands(val foreground: String, val background: String)

object SshCommandBuilder {
    fun build(target: TunnelTarget, localPort: Int): SshCommands {
        val core = buildString {
            append("-L ").append(localPort).append(':')
                .append(target.dbHost).append(':').append(target.dbPort)
            append(' ').append(target.sshUser).append('@').append(target.sshHost)
            if (target.sshPort != 22) append(" -p ").append(target.sshPort)
            target.keyPath?.takeIf { it.isNotBlank() }?.let { append(" -i ").append(quoteIfNeeded(it)) }
        }
        return SshCommands(foreground = "ssh -N $core", background = "ssh -fN $core")
    }

    private fun quoteIfNeeded(path: String): String =
        if (path.any { it.isWhitespace() }) "\"$path\"" else path
}
