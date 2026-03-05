package com.adasjusk.rmotd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.plugin.builtin.jvm.Plugin;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin("rmotd")
public class RMotd {
    private final Logger logger;
    private final Path configDir;
    private final PluginContainer pluginContainer;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private int motdIndex = 0;
    private RMotdConfig config;
    @Inject
    public RMotd(Logger logger, @ConfigDir(sharedRoot = false) Path configDir, PluginContainer pluginContainer) {
        this.logger = logger;
        this.configDir = configDir;
        this.pluginContainer = pluginContainer;
    }
    @Listener
    public void onServerStarted(StartedEngineEvent<Server> event) {
        loadConfig();
        logger.info("RMotd has been enabled!");
    }
    @Listener
    public void onClientPing(ClientPingServerEvent event) {
        loadConfig();
        if (config == null || !config.randomMotdEnabled) return;
        try {
            String rawMotd = getNextMotd();
            if (rawMotd == null || rawMotd.isEmpty()) return;
            Component motd = miniMessage.deserialize(rawMotd);
            event.response().setDescription(motd);
        } catch (Exception e) {
            logger.error("Failed to set MOTD: {}", e.getMessage());
        }
    }
    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Parameterized> event) {
        event.register(pluginContainer, Command.builder()
                .executor(ctx -> {
                    loadConfig();
                    config.randomMotdEnabled = !config.randomMotdEnabled;
                    saveConfig();
                    String state = config.randomMotdEnabled ? config.messages.enabled : config.messages.disabled;
                    String message = config.messages.settingUpdated.replace("<status>", state);
                    ctx.cause().audience().sendMessage(miniMessage.deserialize(message));
                    return CommandResult.success();
                })
                .build(), "randommotd");
        Parameter.Value<String> subcommandParam = Parameter.string().key("subcommand").build();
        Parameter.Value<String> remainingParam = Parameter.remainingJoinedStrings().key("args").optional().build();
        event.register(pluginContainer, Command.builder()
                .addParameter(subcommandParam)
                .addParameter(remainingParam)
                .executor(ctx -> {
                    String sub = ctx.requireOne(subcommandParam).toLowerCase();
                    if ("reload".equals(sub)) {
                        loadConfig();
                        ctx.cause().audience().sendMessage(Component.text("Config reloaded."));
                        return CommandResult.success();
                    } else if ("create".equals(sub)) {
                        String argsStr = ctx.one(remainingParam).orElse("");
                        String[] parts = argsStr.split(" ", 2);
                        if (parts.length < 2) {
                            ctx.cause().audience().sendMessage(Component.text("Usage: /motd create <name> <motd...>"));
                            return CommandResult.success();
                        }
                        loadConfig();
                        config.motds.add(parts[1]);
                        saveConfig();
                        ctx.cause().audience().sendMessage(Component.text("Created MOTD '" + parts[0] + "' and saved to config."));
                        return CommandResult.success();
                    } else {
                        ctx.cause().audience().sendMessage(Component.text("Usage: /motd <create|reload>"));
                        return CommandResult.success();
                    }
                })
                .build(), "motd");
    }
    private String getNextMotd() {
        if (config == null || config.motds == null || config.motds.isEmpty()) return null;
        if (motdIndex >= config.motds.size()) motdIndex = 0;
        String m = config.motds.get(motdIndex);
        motdIndex = (motdIndex + 1) % Math.max(1, config.motds.size());
        return m;
    }
    private void loadConfig() {
        Path configFile = configDir.resolve("config.json");
        if (!Files.exists(configFile)) { config = new RMotdConfig(); saveConfig(); return; }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            config = gson.fromJson(reader, RMotdConfig.class);
        } catch (Exception e) { logger.error("Failed to load config: {}", e.getMessage()); config = new RMotdConfig(); }
    }
    private void saveConfig() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configDir.resolve("config.json"))) { gson.toJson(config, writer); }
        } catch (Exception e) { logger.error("Failed to save config: {}", e.getMessage()); }
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