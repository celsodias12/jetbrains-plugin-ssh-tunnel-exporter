# SSH Tunnel Exporter

A DataGrip plugin that generates the standalone `ssh -L` port-forwarding command for any
data source configured with an **SSH tunnel** — so you can open the exact same bridge
*outside* the IDE (for `psql`, `mysql`, a dump tool, or anything else).

## Features

- Read a data source's SSH tunnel configuration (host, port, user, key) plus the database
  host/port, and build the matching `ssh -L` command for you.
- Two entry points:
  - Right-click a data source in the **Database** tool window → **Copy SSH Tunnel Command**.
  - **Tools → Copy SSH Tunnel Command…** (pick from data sources that have a tunnel).
- A small dialog with an **editable local port**, a **Run in background** checkbox that
  toggles between `ssh -N` (foreground) and `ssh -fN` (background), and a **Copy** button.
- Never emits passwords — key-pair auth adds `-i <key>`; password/agent/OpenSSH-config auth
  is left for `ssh` to resolve.

### Example output

```bash
ssh -N -L 5432:db.internal:5432 deploy@bastion.example.com -p 2222 -i ~/.ssh/id_ed25519
```

## Compatibility

- Built and verified against **DataGrip 2026.1.3** (`sinceBuild = 261`).
- Plugin Verifier verdict: **`Compatible`** — no internal/deprecated/experimental API usage.
- Works in other JetBrains IDEs that bundle the **Database Tools & SQL** plugin
  (`<depends>com.intellij.database</depends>`), but is targeted at DataGrip.

## Build & run

Requires **JDK 21** (auto-provisioned by the Foojay toolchain resolver if missing).

```bash
./gradlew test          # run the unit tests
./gradlew runIde        # launch a sandbox DataGrip with the plugin loaded
./gradlew buildPlugin   # produce build/distributions/<name>-<version>.zip
./gradlew verifyPlugin  # JetBrains Plugin Verifier (compatibility / internal-API check)
```

### Install from disk

In DataGrip: **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip from
`build/distributions/`, then restart.

## License

[MIT](LICENSE) © 2026 Celso Dias
