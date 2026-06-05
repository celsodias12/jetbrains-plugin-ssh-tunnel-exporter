package dev.sshtunnelexporter.datagrip.ssh

import dev.sshtunnelexporter.datagrip.model.TunnelTarget

/**
 * The toggleable ssh options surfaced in the dialog.
 *
 * Numeric values ([connectTimeout], [serverAliveInterval], [serverAliveCountMax]) are omitted when
 * null or <= 0. [noShell] (-N) and [background] (-f) are *invocation* flags with no ~/.ssh/config
 * equivalent, so [sshConfig] ignores them; [verbose] maps to `LogLevel DEBUG` there.
 * [identitiesOnly] only applies when the target has a key (an `-i` / IdentityFile is present).
 */
data class SshOptions(
    val noShell: Boolean = true,
    val background: Boolean = false,
    val compression: Boolean = false,
    val verbose: Boolean = false,
    val identitiesOnly: Boolean = false,
    val exitOnForwardFailure: Boolean = false,
    val connectTimeout: Int? = null,
    val serverAliveInterval: Int? = null,
    val serverAliveCountMax: Int? = null,
)

object SshCommandBuilder {

    /** Ordered command segments — one per flag/option — joined for single- or multi-line rendering. */
    private fun commandParts(target: TunnelTarget, localPort: Int, opts: SshOptions): List<String> {
        val hasKey = !target.keyPath.isNullOrBlank()
        return buildList {
            val flags = buildString {
                if (opts.background) append('f')
                if (opts.noShell) append('N')
                if (opts.compression) append('C')
            }
            add("ssh" + (if (flags.isNotEmpty()) " -$flags" else "") + (if (opts.verbose) " -v" else ""))
            add("-L $localPort:${target.dbHost}:${target.dbPort}")
            add("${target.sshUser}@${target.sshHost}")
            if (target.sshPort != 22) add("-p ${target.sshPort}")
            if (hasKey) add("-i ${quoteIfNeeded(target.keyPath!!)}")
            opts.connectTimeout?.takeIf { it > 0 }?.let { add("-o ConnectTimeout=$it") }
            if (opts.identitiesOnly && hasKey) add("-o IdentitiesOnly=yes")
            if (opts.exitOnForwardFailure) add("-o ExitOnForwardFailure=yes")
            opts.serverAliveInterval?.takeIf { it > 0 }?.let { add("-o ServerAliveInterval=$it") }
            opts.serverAliveCountMax?.takeIf { it > 0 }?.let { add("-o ServerAliveCountMax=$it") }
        }
    }

    /** The single-line `ssh -L` command reflecting [opts]. */
    fun command(target: TunnelTarget, localPort: Int, opts: SshOptions): String =
        commandParts(target, localPort, opts).joinToString(" ")

    /** The same command, one flag per line with shell `\` continuations (paste-safe in bash/zsh). */
    fun commandMultiline(target: TunnelTarget, localPort: Int, opts: SshOptions): String =
        commandParts(target, localPort, opts).joinToString(" \\\n    ")

    /** The equivalent `~/.ssh/config` stanza for [alias]; -N/-f are invocation-only and not emitted. */
    fun sshConfig(target: TunnelTarget, localPort: Int, alias: String, opts: SshOptions): String {
        val hasKey = !target.keyPath.isNullOrBlank()
        val lines = buildList {
            add("Host ${aliasToken(alias)}")
            add("    HostName ${target.sshHost}")
            add("    User ${target.sshUser}")
            if (target.sshPort != 22) add("    Port ${target.sshPort}")
            if (hasKey) add("    IdentityFile ${quoteIfNeeded(target.keyPath!!)}")
            if (opts.identitiesOnly && hasKey) add("    IdentitiesOnly yes")
            add("    LocalForward $localPort ${target.dbHost}:${target.dbPort}")
            if (opts.compression) add("    Compression yes")
            opts.connectTimeout?.takeIf { it > 0 }?.let { add("    ConnectTimeout $it") }
            if (opts.exitOnForwardFailure) add("    ExitOnForwardFailure yes")
            opts.serverAliveInterval?.takeIf { it > 0 }?.let { add("    ServerAliveInterval $it") }
            opts.serverAliveCountMax?.takeIf { it > 0 }?.let { add("    ServerAliveCountMax $it") }
            if (opts.verbose) add("    LogLevel DEBUG")
        }
        return lines.joinToString("\n")
    }

    /** Sanitise a free-form name into a single ssh_config Host token (no whitespace). */
    fun aliasToken(raw: String): String =
        raw.trim().replace(Regex("\\s+"), "-").ifBlank { "db-tunnel" }

    private fun quoteIfNeeded(path: String): String =
        if (path.any { it.isWhitespace() }) "\"$path\"" else path
}
