package com.adasjusk.rmotd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RMotd implements DedicatedServerModInitializer {
    public static final String MOD_ID = "rmotd";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private int motdIndex = 0;
    private int tickCounter = 0;
    private static final int ROTATION_TICKS = 200;
    private RMotdConfig config;
    private Path configPath;
    @Override
    public void onInitializeServer() {
        LOGGER.info("RMotd initializing...");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            configPath = server.getRunDirectory().resolve("config").resolve("rmotd.json");
            loadConfig();
            applyMotd(server);
            LOGGER.info("RMotd has been enabled!");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config == null || !config.randomMotdEnabled) return;
            tickCounter++;
            if (tickCounter >= ROTATION_TICKS) {
                tickCounter = 0;
                applyMotd(server);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("randommotd")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .executes(context -> {
                        loadConfig();
                        config.randomMotdEnabled = !config.randomMotdEnabled;
                        saveConfig();
                        String state = config.randomMotdEnabled ? config.messages.enabled : config.messages.disabled;
                        String message = config.messages.settingUpdated.replace("<status>", state);
                        context.getSource().sendFeedback(() -> Text.literal(message), false);
                        if (config.randomMotdEnabled) applyMotd(context.getSource().getServer());
                        return 1;
                    }));

            dispatcher.register(CommandManager.literal("motd")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .then(CommandManager.literal("reload")
                            .executes(context -> {
                                loadConfig();
                                context.getSource().sendFeedback(() -> Text.literal("Config reloaded."), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .then(CommandManager.argument("motd", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                String motdText = StringArgumentType.getString(context, "motd");
                                                loadConfig();
                                                config.motds.add(motdText);
                                                saveConfig();
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("Created MOTD '" + name + "' and saved to config."),
                                                        false);
                                                return 1;
            })))));
        });
    }
    private void applyMotd(MinecraftServer server) {
        if (config == null || !config.randomMotdEnabled) return;
        String motd = getNextMotd();
        if (motd != null && !motd.isEmpty()) server.setMotd(translateColorCodes(motd));
    }

    private String getNextMotd() {
        if (config == null || config.motds == null || config.motds.isEmpty()) return null;
        if (motdIndex >= config.motds.size()) motdIndex = 0;
        String m = config.motds.get(motdIndex);
        motdIndex = (motdIndex + 1) % Math.max(1, config.motds.size());
        return m;
    }

    private String translateColorCodes(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789abcdefklmnor".indexOf(chars[i + 1]) > -1) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private void loadConfig() {
        if (configPath == null) return;
        if (!Files.exists(configPath)) { config = new RMotdConfig(); saveConfig(); return; }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            config = GSON.fromJson(reader, RMotdConfig.class);
        } catch (Exception e) { LOGGER.error("Failed to load config: {}", e.getMessage()); config = new RMotdConfig(); }
    }

    private void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) { GSON.toJson(config, writer); }
        } catch (Exception e) { LOGGER.error("Failed to save config: {}", e.getMessage()); }
    }

    static class RMotdConfig {
        boolean randomMotdEnabled = true;
        List<String> motds = new ArrayList<>(List.of(
                "&c&lWelcome &b&lto our server!",
                "&eExplore the world of Minecraft with us!",
                "&aJoin us and have fun!"
        ));
        Messages messages = new Messages();
        static class Messages {
            String invalidInput = "Invalid input";
            String settingUpdated = "Random MOTD feature updated to <status>";
            String enabled = "enabled";
            String disabled = "disabled";
        }
    }
}