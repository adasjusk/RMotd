package com.adasjusk.rmotd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RMotd extends JavaPlugin implements Listener {
    private int motdIndex = 0;
    private FileConfiguration config;
    private List<String> motdList;
    private MiniMessage miniMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.miniMessage = MiniMessage.miniMessage();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register commands
        if (getCommand("randommotd") != null) {
            getCommand("randommotd").setExecutor(new RandomMotdCommand(this));
        }
        if (getCommand("motd") != null) {
            getCommand("motd").setExecutor(new MotdCommand(this));
        }
    }

    // default.properties support removed; /motd handles create <name> <motd...> and reload only

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        File configFile = new File(getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);

    this.motdList = this.config.getStringList("motds");

        boolean randomMotdEnabled = this.config.getBoolean("random_motd_enabled");
        if (randomMotdEnabled) {
            try {
                String rawMotd = getNextMotd();
                if (rawMotd == null || rawMotd.isEmpty()) {
                    throw new IllegalStateException("MOTD is null or empty");
                }
                Component motd = miniMessage.deserialize(rawMotd);
                String legacyMotd = LegacyComponentSerializer.legacySection().serialize(motd);
                event.setMotd(legacyMotd);
            } catch (Exception e) {
                getLogger().severe("Failed to set MOTD: " + e.getMessage());
            }
        }
    }

    public String getNextMotd() {
        if (motdList == null || motdList.isEmpty()) return null;
        // simple round-robin
        if (motdIndex >= motdList.size()) motdIndex = 0;
        String m = motdList.get(motdIndex);
        motdIndex = (motdIndex + 1) % Math.max(1, motdList.size());
        return m;
    }
}

class RandomMotdCommand implements CommandExecutor {
    private final RMotd plugin;
    private final MiniMessage miniMessage;

    RandomMotdCommand(RMotd plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CompletableFuture.runAsync(() -> {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            boolean current = config.getBoolean("random_motd_enabled", true);

            if (args.length == 0) {
                boolean newVal = !current;
                config.set("random_motd_enabled", newVal);
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save config: " + e.getMessage());
                }
                String template = config.getString("messages.setting_updated", "<status>");
                String state = newVal ? config.getString("messages.enabled", "enabled") : config.getString("messages.disabled", "disabled");
                String message = template.replace("<status>", state);
                Component parsed = miniMessage.deserialize(message);
                sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(parsed));
            } else {
                String invalid = config.getString("messages.invalid_input", "Invalid input");
                sender.sendMessage(invalid);
            }
        });

        return true;
    }
}

class MotdCommand implements CommandExecutor {
    private final RMotd plugin;

    MotdCommand(RMotd plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only allow server operators to run these commands
        if (!(sender instanceof org.bukkit.command.CommandSender) || !sender.isOp()) {
            sender.sendMessage("You must be a server operator to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /motd <create|reload>");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("create".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage("Usage: /motd create <name> <motd...>");
                return true;
            }
            String name = args[1];
            // join remaining args as the motd text
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(' ');
                sb.append(args[i]);
            }
            String motdText = sb.toString();

            File cfgFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
            List<String> motds = cfg.getStringList("motds");
            if (motds == null) motds = new ArrayList<>();

            motds.add(motdText);
            cfg.set("motds", motds);
            try {
                cfg.save(cfgFile);
                sender.sendMessage("Created MOTD '" + name + "' and saved to config.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save config: " + e.getMessage());
                sender.sendMessage("Failed to save config: " + e.getMessage());
            }
            return true;
        } else if ("reload".equals(sub)) {
            plugin.reloadConfig();
            sender.sendMessage("Config reloaded.");
            return true;
        } else {
            sender.sendMessage("Unknown subcommand: " + sub + ". Use create|reload");
            return true;
        }
    }
}