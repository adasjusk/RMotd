package com.adasjusk.rmotd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "rmotd", name = "RMotd", version = "2.0",
        description = "Random MOTD plugin for Velocity", authors = {"adasjusk"})
public class RMotd {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private int motdIndex = 0;
    private RMotdConfig config;

    @Inject
    public RMotd(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        loadConfig();
        var commandManager = server.getCommandManager();
        var randomMeta = commandManager.metaBuilder("randommotd")
                .plugin(this)
                .build();
        commandManager.register(randomMeta, new RandomMotdCommand());
        var motdMeta = commandManager.metaBuilder("motd")
                .plugin(this)
                .build();
        commandManager.register(motdMeta, new MotdCommand());
        logger.info("RMotd has been enabled!");
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        loadConfig();
        if (config == null || !config.randomMotdEnabled) return;

        try {
            String rawMotd = getNextMotd();
            if (rawMotd == null || rawMotd.isEmpty()) return;

            Component motd = miniMessage.deserialize(rawMotd);
            ServerPing original = event.getPing();
            ServerPing modified = original.asBuilder().description(motd).build();
            event.setPing(modified);
        } catch (Exception e) {
            logger.error("Failed to set MOTD: {}", e.getMessage());
        }
    }

    private String getNextMotd() {
        if (config == null || config.motds == null || config.motds.isEmpty()) return null;
        if (motdIndex >= config.motds.size()) motdIndex = 0;
        String m = config.motds.get(motdIndex);
        motdIndex = (motdIndex + 1) % Math.max(1, config.motds.size());
        return m;
    }

    private void loadConfig() {
        Path configFile = dataDirectory.resolve("config.json");
        if (!Files.exists(configFile)) {
            config = new RMotdConfig();
            saveConfig();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            config = gson.fromJson(reader, RMotdConfig.class);
        } catch (Exception e) {
            logger.error("Failed to load config: {}", e.getMessage());
            config = new RMotdConfig();
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                gson.toJson(config, writer);
            }
        } catch (Exception e) {
            logger.error("Failed to save config: {}", e.getMessage());
        }
    }

    private class RandomMotdCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            loadConfig();
            config.randomMotdEnabled = !config.randomMotdEnabled;
            saveConfig();
            String state = config.randomMotdEnabled ? config.messages.enabled : config.messages.disabled;
            String message = config.messages.settingUpdated.replace("<status>", state);
            source.sendMessage(miniMessage.deserialize(message));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("rmotd.randommotd");
        }
    }

    private class MotdCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /motd <create|reload>"));
                return;
            }

            String sub = args[0].toLowerCase();
            if ("create".equals(sub)) {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /motd create <name> <motd...>"));
                    return;
                }
                String name = args[1];
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) sb.append(' ');
                    sb.append(args[i]);
                }
                loadConfig();
                config.motds.add(sb.toString());
                saveConfig();
                source.sendMessage(Component.text("Created MOTD '" + name + "' and saved to config."));
            } else if ("reload".equals(sub)) {
                loadConfig();
                source.sendMessage(Component.text("Config reloaded."));
            } else {
                source.sendMessage(Component.text("Unknown subcommand: " + sub + ". Use create|reload"));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("rmotd.motd");
        }
    }

    static class RMotdConfig {
        boolean randomMotdEnabled = true;
        List<String> motds = new ArrayList<>(List.of(
                "<gradient:red:blue>Welcome to our server!</gradient>",
                "<yellow>Explore the world of Minecraft with us!</yellow>",
                "<green>Join us and have fun!</green>"
        ));
        Messages messages = new Messages();

        static class Messages {
            String invalidInput = "<red>Invalid input</red>";
            String settingUpdated = "<green>Random MOTD feature updated to <status></green>";
            String enabled = "enabled";
            String disabled = "disabled";
        }
    }
}