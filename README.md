# SSH Tunnel Exporter

A DataGrip plugin that turns any data source configured with an **SSH tunnel** into the
exact `ssh -L` command (and `~/.ssh/config` stanza) needed to open the same bridge
*outside* the IDE — for `psql`, `mysql`, a dump tool, or anything else.

## Features

- Reads a data source's SSH tunnel config (host, port, user, key) plus the database
  host/port, and builds the matching port-forward for you.
- Two entry points:
  - Right-click a data source in the **Database** tool window → **Copy SSH Tunnel Command**.
  - **Tools → Copy SSH Tunnel Command…** (pick from data sources that have a tunnel).
- A dialog that lets you tailor the command, with three copyable outputs in tabs:
  - **Open tunnel** — the `ssh -L` command, one flag per line with `\` continuations.
  - **Connect to DB** — the matching native client command (`psql`, `mysql`, `sqlcmd`,
    `mongosh`, `clickhouse-client`, `sqlplus`) to connect through the tunnel, shown when
    the DBMS is recognised.
  - **SSH config** — the equivalent `~/.ssh/config` `Host` stanza, with an editable alias.
- **Editable local port** and opt-in SSH option toggles — `-C`, `-v`, `IdentitiesOnly`,
  `ExitOnForwardFailure`, `ConnectTimeout`, and `ServerAliveInterval`/`ServerAliveCountMax`
  keep-alive (under *Advanced*). `-N`/`-f` toggle foreground/background; `-L` is always on
  (it is the tunnel itself).
- A "How it works" flow diagram and copy buttons with a "Copied" confirmation.
- Never emits passwords — key-pair auth adds `-i <key>`; password/agent/OpenSSH-config auth
  is left for `ssh` to resolve.

### Example output

**Open tunnel** (multi-line, paste-safe in bash/zsh):

```bash
ssh -N \
    -L 5432:db.internal:5432 \
    deploy@bastion.example.com \
    -p 2222 \
    -i ~/.ssh/id_ed25519
```

**SSH config** (`~/.ssh/config`):

```text
Host db-tunnel
    HostName bastion.example.com
    User deploy
    Port 2222
    IdentityFile ~/.ssh/id_ed25519
    LocalForward 5432 db.internal:5432
```

**Connect to DB** (once the tunnel is up):

```bash
psql -h localhost -p 5432 -U app shop
```

## Compatibility

- Compatible from **`sinceBuild = 231`** (DataGrip 2023.1) with an open-ended upper bound;
  developed against **DataGrip 2026.1.3**.
- Plugin Verifier verdict: **`Compatible`** — no internal/deprecated/experimental API usage.
- Works in other JetBrains IDEs that bundle the **Database Tools & SQL** plugin
  (`<depends>com.intellij.database</depends>`), but is targeted at DataGrip.

## Build & run

Requires **JDK 21** (auto-provisioned by the Foojay toolchain resolver if missing).
A `Makefile` wraps the common Gradle tasks:

```bash
make test      # run the unit tests
make run       # launch a sandbox DataGrip with the plugin loaded
make build     # produce build/distributions/<name>-<version>.zip
make verify    # JetBrains Plugin Verifier (compatibility / internal-API check)
make install   # build and install into your local DataGrip (restart to load)
```

`make help` lists every target; each is a thin wrapper over `./gradlew <task>`.

### Install from disk

`make install` copies the freshly built plugin into your local DataGrip's plugins
directory — restart DataGrip to load it. Or, manually: **Settings → Plugins → ⚙ →
Install Plugin from Disk…**, pick the zip from `build/distributions/`, then restart.

## License

[MIT](LICENSE) © 2026 Celso Dias
