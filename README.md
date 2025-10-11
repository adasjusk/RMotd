RMotd
=====

A small Minecraft server plugin to serve rotating MOTDs (Message of the Day) and let server operators manage MOTDs at runtime.

Features
--------
- Rotating MOTDs from `config.yml` (round-robin).
- `/randommotd` command: toggle random-rotation on/off.
- `/motd create <name> <motd...>` command: add a new MOTD to `config.yml` at runtime.
- `/motd reload` command: reloads the `config.yml`.
- Operator-only management (commands restricted to server ops by default).

Configuration (`config.yml`)
----------------------------
Located at `plugins/RMotd/config.yml` (or in the JAR default in `src/main/resources/config.yml`). Example:

```yaml
random_motd_enabled: true
motds:
  - "<gradient:red:blue>Welcome to our server!</gradient>"
  - "<yellow>Explore the world of Minecraft with us!</yellow>"
  - "<green>Join us and have fun!</green>"

messages:
  invalid_input: "<red>Invalid input, Please use 'true' or 'false'</red>"
  setting_updated: "<green>Random MOTD feature updated to <status></green>"
```

Commands
--------
- `/randommotd` — Toggle random MOTD rotation (reads/writes `random_motd_enabled` in `config.yml`).
  - Permission: `rmod.randommotd` (declared in `plugin.yml`).

- `/motd create <name> <motd...>` — Add a MOTD entry to `motds` in `config.yml` and save it. The `<name>` is used only in the confirmation message currently. Provide the MOTD content as remaining args.
  - Example: `/motd create welcome <green>Welcome to our server!</green>`
  - Operator-only by default (only ops or console can run it). If you prefer permission-based control, see Permissions below.

- `/motd reload` — Reloads `config.yml` from disk. Operator-only.

Permissions
-----------
- `rmod.randommotd` — used for the `randommotd` command.
- `rmod.motd` — reserved for `/motd` (plugin.yml documents it). Currently the plugin enforces operator-only usage for `/motd`; if you prefer permissions, change the check to `sender.hasPermission("rmod.motd")` in `MotdCommand`.


License
-------
MIT
