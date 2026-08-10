package nl.hauntedmc.paperhotreloader;

import nl.hauntedmc.paperhotreloader.commands.PhrCommand;
import nl.hauntedmc.paperhotreloader.managers.BukkitPluginLifecycleManager;
import nl.hauntedmc.paperhotreloader.tasks.PluginWatchManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/** Main Bukkit entrypoint for PaperHotReloader. */
public final class PaperHotReloader extends JavaPlugin {

    private BukkitPluginLifecycleManager lifecycleManager;
    private PluginWatchManager watchManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lifecycleManager = new BukkitPluginLifecycleManager(this);
        watchManager = new PluginWatchManager(this, lifecycleManager);

        registerCommands();
        getLogger().info("PaperHotReloader enabled. Use /phr help for commands.");
    }

    private void registerCommands() {
        PhrCommand command = new PhrCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    "paperhotreloader",
                    "Manage Bukkit plugin lifecycles.",
                    java.util.List.of("phr"),
                    command
            );
        });
    }

    @Override
    public void onDisable() {
        if (watchManager != null) {
            watchManager.close();
        }
    }

    public BukkitPluginLifecycleManager getPluginLifecycleManager() {
        return lifecycleManager;
    }

    public PluginWatchManager getWatchManager() {
        return watchManager;
    }

    public String prefix() {
        return getConfig().getString("prefix", "&8[&bPHR&8] &7");
    }
}
