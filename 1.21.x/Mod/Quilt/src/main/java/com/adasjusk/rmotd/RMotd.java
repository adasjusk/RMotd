package com.adasjusk.rmotd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RMotd {
    private static final Logger LOGGER = LoggerFactory.getLogger("rmotd");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RMotd INSTANCE = new RMotd();
    private MinecraftServer server;
    private Path configPath;
    private RMotdConfig config;
    private int motdIndex = 0;
    private int tickCounter = 0;
    private static final int ROTATION_TICKS = 200;
    private RMotd() {}
    public static RMotd get() {
        return INSTANCE;
    }

    public static void init(MinecraftServer srv) {
        RMotd instance = get();
        instance.server = srv;
        instance.configPath = srv.getRunDirectory().resolve("config").resolve("rmotd.json");
        instance.loadConfig();
        instance.applyMotd();
        LOGGER.info("RMotd enabled.");
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (instance.config == null || !instance.config.randomMotdEnabled) return;

            instance.tickCounter++;
            if (instance.tickCounter >= ROTATION_TICKS) {
                instance.tickCounter = 0;
                instance.applyMotd();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("randommotd")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        instance.loadConfig();
                        instance.config.randomMotdEnabled = !instance.config.randomMotdEnabled;
                        instance.saveConfig();
                        String state = instance.config.randomMotdEnabled
                                ? instance.config.messages.enabled
                                : instance.config.messages.disabled;
                        String message = instance.config.messages.settingUpdated.replace("<status>", state);
                        context.getSource().sendFeedback(() -> Text.literal(message), false);
                        if (instance.config.randomMotdEnabled) {
                            instance.applyMotd();
                        }
                        return 1;
                    }));

            dispatcher.register(CommandManager.literal("motd")
                    .requires(source -> source.hasPermission(2))
                    .then(CommandManager.literal("reload")
                            .executes(context -> {
                                instance.loadConfig();
                                context.getSource().sendFeedback(() -> Text.literal("Config reloaded."), false);
                                return 1;
                            }))

                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .then(CommandManager.argument("motd", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                String motdText = StringArgumentType.getString(context, "motd");
                                                instance.loadConfig();
                                                instance.config.motds.add(motdText);
                                                instance.saveConfig();

                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("Created MOTD '" + name + "' and saved."),
                                                        false
                                                );
                                                return 1;
                                            })))));
        });
    }

    private void applyMotd() {
        if (config == null || !config.randomMotdEnabled || server == null) return;
        String motd = getNextMotd();
        if (motd != null && !motd.isEmpty()) {
            server.setMotd(translateColorCodes(motd));
        }
    }

    private String getNextMotd() {
        if (config == null || config.motds == null || config.motds.isEmpty()) return null;
        if (motdIndex >= config.motds.size()) {
            motdIndex = 0;
        }
        String motd = config.motds.get(motdIndex);
        motdIndex = (motdIndex + 1) % config.motds.size();
        return motd;
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
        if (!Files.exists(configPath)) {
            config = new RMotdConfig();
            saveConfig();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            config = GSON.fromJson(reader, RMotdConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load config: {}", e.getMessage());
            config = new RMotdConfig();
        }
    }

    private void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save config: {}", e.getMessage());
        }
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