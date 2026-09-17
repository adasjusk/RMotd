# RMotd plugin

A small Minecraft server plugin to show random MOTDs (Message(Text) of the Day) and let server operators manage MOTDs at configuration file.

# Commands
- Rotating MOTDs from `config.yml` (round-robin).
- `/randommotd` command: toggle random-rotation on/off. Requires rmod.randommotd permission
- `/motd create <name> <motd...>` command: add a new MOTD to `config.yml` at run time. 
- `/motd reload` command: reloads the `config.yml`. Requires rmod.motd permission

# Configuration

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

# Support
- Minecraft version: 1.21.x-26.3
- Loaders: Bukkit BungeeCord Fabric Folia Forge NeoForge Paper Purpur Quilt Spigot Sponge Velocity
- Made by human and then patched by ai ✨
