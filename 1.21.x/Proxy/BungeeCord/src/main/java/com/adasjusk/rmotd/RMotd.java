package com.adasjusk.rmotd;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RMotd extends Plugin implements Listener {
    private int motdIndex = 0;
    private Configuration config;
    @Override
    public void onEnable() {
        loadConfig();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new RandomMotdCmd());
        getProxy().getPluginManager().registerCommand(this, new MotdCmd());
        getLogger().info("RMotd has been enabled!");
    }

    @EventHandler
    public void onProxyPing(ProxyPingEvent event) {
        loadConfig();
        boolean randomMotdEnabled = config.getBoolean("random_motd_enabled", true);
        if (!randomMotdEnabled) return;
        try {
            String rawMotd = getNextMotd();
            if (rawMotd == null || rawMotd.isEmpty()) return;
            String colored = ChatColor.translateAlternateColorCodes('&', rawMotd);
            ServerPing response = event.getResponse();
            response.setDescriptionComponent(new TextComponent(colored));
            event.setResponse(response);
        } catch (Exception e) {
            getLogger().severe("Failed to set MOTD: " + e.getMessage());
        }
    }

    private String getNextMotd() {
        List<String> motds = config.getStringList("motds");
        if (motds == null || motds.isEmpty()) return null;
        if (motdIndex >= motds.size()) motdIndex = 0;
        String m = motds.get(motdIndex);
        motdIndex = (motdIndex + 1) % Math.max(1, motds.size());
        return m;
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    Files.copy(in, configFile.toPath());
                }
            }
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }

    private class RandomMotdCmd extends Command {
        RandomMotdCmd() { super("randommotd", "rmotd.randommotd"); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            loadConfig();
            boolean current = config.getBoolean("random_motd_enabled", true);
            config.set("random_motd_enabled", !current);
            saveConfig();
            String state = !current
                    ? config.getString("messages.enabled", "enabled")
                    : config.getString("messages.disabled", "disabled");
            String template = config.getString("messages.setting_updated", "Random MOTD updated to %s");
            sender.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', String.format(template, state))));
        }
    }

    private class MotdCmd extends Command {
        MotdCmd() { super("motd", "rmotd.motd"); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                sender.sendMessage(new TextComponent("Usage: /motd <create|reload>"));
                return;
            }
            String sub = args[0].toLowerCase();
            if ("create".equals(sub)) {
                if (args.length < 3) {
                    sender.sendMessage(new TextComponent("Usage: /motd create <name> <motd...>"));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) sb.append(' ');
                    sb.append(args[i]);
                }
                loadConfig();
                List<String> motds = config.getStringList("motds");
                if (motds == null) motds = new ArrayList<>();
                motds.add(sb.toString());
                config.set("motds", motds);
                saveConfig();
                sender.sendMessage(new TextComponent("Created MOTD '" + args[1] + "' and saved to config."));
            } else if ("reload".equals(sub)) {
                loadConfig();
                sender.sendMessage(new TextComponent("Config reloaded."));
            } else {
                sender.sendMessage(new TextComponent("Unknown subcommand. Use create|reload"));
            }
        }
    }
}