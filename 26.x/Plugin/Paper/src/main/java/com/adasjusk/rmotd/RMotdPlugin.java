// cmon paper, just make your docs better. Paper needs ins own .JavaPlugin import in my opinion if it's truthly a paper plugin and not bukkit
package com.adasjusk.rmotd;
import org.bukkit.event.server.ServerListPingEvent;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
public final class RMotdPlugin extends JavaPlugin implements Listener {
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final AtomicBoolean randomEnabled = new AtomicBoolean(true);
    private volatile List<String> motds = new ArrayList<>();
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadIntoMemory();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
    }
    private void loadIntoMemory() {
        FileConfiguration cfg = getConfig();
        randomEnabled.set(cfg.getBoolean("random_motd_enabled", true));
        motds = new ArrayList<>(cfg.getStringList("motds"));
    }
    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!randomEnabled.get()) return;
        if (motds.isEmpty()) return;
        String motd = motds.get(ThreadLocalRandom.current().nextInt(motds.size()));
        event.motd(mini.deserialize(motd));
}
        private void registerCommands() {
            getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    Commands commands = event.registrar();
                    commands.register(
                        Commands.literal("randommotd")
                            .then(
                                Commands.argument("state", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String input = ctx.getArgument("state", String.class);
                                        if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) {
                                            sendMini(ctx.getSource().getSender(),
                                                    getConfig().getString("messages.invalid_input"));
                                            return 0;
                                        }
                                        boolean newState = Boolean.parseBoolean(input);
                                        randomEnabled.set(newState);
                                        getConfig().set("random_motd_enabled", newState);
                                        saveConfigAsync();
                                        String msg = getConfig()
                                                .getString("messages.setting_updated")
                                                .replace("<status>", String.valueOf(newState));

                                        sendMini(ctx.getSource().getSender(), msg);
                                        return 1;
                                    })
                            )
                            .build()
                    );
                    commands.register(
                        Commands.literal("motd")
                            .then(
                                Commands.literal("reload")
                                    .executes(ctx -> {
                                        reloadConfig();
                                        loadIntoMemory();
                                        ctx.getSource().getSender()
                                                .sendMessage(Component.text("Config reloaded."));
                                        return 1;
                                    })
                            )
                            .then(
                                Commands.literal("add")
                                    .then(
                                        Commands.argument("text", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String text = ctx.getArgument("text", String.class);
                                                List<String> copy = new ArrayList<>(motds);
                                                copy.add(text);
                                                motds = copy;
                                                getConfig().set("motds", copy);
                                                saveConfigAsync();
                                                ctx.getSource().getSender()
                                                        .sendMessage(Component.text("MOTD added."));
                                                return 1;
                                            })
                                    )
                            )
                            .build()
                    );
                }
            );
        }


        // so it would work, if the user isn't dump and doesn't delete things but if so then..
    private void saveConfigAsync() {
        getServer().getAsyncScheduler().runNow(this, task -> {
            try {
                getConfig().save(new File(getDataFolder(), "config.yml"));
            } catch (IOException e) {
                getLogger().severe("Failed to save config: " + e.getMessage());
            }
        });
    }
    private void sendMini(org.bukkit.command.CommandSender sender, String message) {
        if (message == null) return;
        sender.sendMessage(mini.deserialize(message));
    }
}