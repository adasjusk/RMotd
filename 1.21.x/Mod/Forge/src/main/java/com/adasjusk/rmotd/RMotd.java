package com.adasjusk.rmotd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod("rmotd")
public class RMotd {
    private static final Logger LOGGER = LoggerFactory.getLogger("rmotd");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private RMotdConfig config;
    private Path configPath;
    public RMotd() {
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("randommotd")
                        .executes(context -> {
                            loadConfig();
                            config.randomMotdEnabled = !config.randomMotdEnabled;
                            saveConfig();
                            String state = config.randomMotdEnabled
                                    ? config.messages.enabled
                                    : config.messages.disabled;
                            String message = config.messages.settingUpdated
                                    .replace("<status>", state);
                            context.getSource().sendSuccess(
                                    () -> Component.literal(message),
                                    false
                            );
                            return 1;
                        })
        );

        event.getDispatcher().register(
                Commands.literal("motd")
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    loadConfig();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("RMotd config reloaded."),
                                            false
                                    );
                                    return 1;
                                }))
                        .then(Commands.literal("create")
                                .then(Commands.argument("motd", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String motdText = StringArgumentType.getString(context, "motd");
                                            loadConfig();
                                            config.motds.add(motdText);
                                            saveConfig();
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("New MOTD added."),
                                                    false
                                            );
                                            return 1;
            })))
        );
    }
    private void loadConfig() {
        if (configPath == null) {
            configPath = Path.of("config", "rmotd.json");
        }
        try {
            if (!Files.exists(configPath)) {
                config = new RMotdConfig();
                saveConfig();
                return;
            }
            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = GSON.fromJson(reader, RMotdConfig.class);
            }
            if (config == null) config = new RMotdConfig();
            if (config.motds == null) config.motds = new ArrayList<>();
            if (config.messages == null) config.messages = new RMotdConfig.Messages();
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

    // I just hate forge, also forgegradle 7 and their dump imports i accept that neo is just better 10x.
    static class RMotdConfig {
        boolean randomMotdEnabled = true;
        List<String> motds = new ArrayList<>(List.of(
                "&c&lWelcome &b&lto our server!",
                "&eExplore the world of Minecraft with us!",
                "&aJoin us and have fun!"
        ));
        Messages messages = new Messages();
        static class Messages {
            String settingUpdated = "Random MOTD feature updated to <status>";
            String enabled = "enabled";
            String disabled = "disabled";
        }
    }
}