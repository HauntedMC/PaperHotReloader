package nl.hauntedmc.paperhotreloader;

import nl.hauntedmc.paperhotreloader.commands.PhrCommand;
import nl.hauntedmc.paperhotreloader.managers.BukkitPluginLifecycleManager;
import nl.hauntedmc.paperhotreloader.tasks.PluginWatchManager;
import org.bukkit.command.PluginCommand;
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

        PluginCommand command = getCommand("paperhotreloader");
        if (command == null) {
            throw new IllegalStateException("paperhotreloader command is missing from plugin.yml");
        }
        PhrCommand executor = new PhrCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("PaperHotReloader enabled. Use /phr help for commands.");
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
