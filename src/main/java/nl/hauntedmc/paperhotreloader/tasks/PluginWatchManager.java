package nl.hauntedmc.paperhotreloader.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import nl.hauntedmc.paperhotreloader.PaperHotReloader;
import nl.hauntedmc.paperhotreloader.commands.PhrCommand;
import nl.hauntedmc.paperhotreloader.managers.BukkitPluginLifecycleManager;
import nl.hauntedmc.paperhotreloader.managers.OperationResult;
import nl.hauntedmc.paperhotreloader.utils.PluginDescription;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;

/** A single asynchronous watcher with main-thread, debounced lifecycle actions. */
public final class PluginWatchManager implements Runnable {

    private static final WatchEvent.Kind<?>[] EVENTS = {
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE
    };

    private final PaperHotReloader owner;
    private final BukkitPluginLifecycleManager lifecycleManager;
    private final Map<String, WatchEntry> entries = new ConcurrentHashMap<>();
    private final Set<String> pendingReloads = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private volatile WatchService watchService;

    public PluginWatchManager(PaperHotReloader owner, BukkitPluginLifecycleManager lifecycleManager) {
        this.owner = owner;
        this.lifecycleManager = lifecycleManager;
    }

    public OperationResult watch(Plugin plugin, CommandSender sender) {
        File file = lifecycleManager.getPluginFile(plugin).orElse(null);
        if (file == null || !file.isFile()) {
            return OperationResult.fail(plugin.getName(), "cannot find its plugin jar");
        }
        String key = normalize(plugin.getName());
        entries.put(key, new WatchEntry(plugin.getName(), file, sender));
        start();
        return OperationResult.ok(plugin.getName(), "watching " + file.getName());
    }

    public OperationResult unwatch(String pluginName) {
        WatchEntry removed = entries.remove(normalize(pluginName));
        return removed == null
                ? OperationResult.fail(pluginName, "is not being watched")
                : OperationResult.ok(pluginName, "stopped watching");
    }

    private synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Bukkit.getScheduler().runTaskAsynchronously(owner, this);
    }

    @Override
    public void run() {
        Path pluginsDirectory = lifecycleManager.getPluginsFolder().toPath();
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            watchService = service;
            pluginsDirectory.register(service, EVENTS);
            while (running) {
                WatchKey key = service.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        scheduleAll();
                        continue;
                    }
                    Path changed = pluginsDirectory.resolve((Path) event.context());
                    if (!Files.isDirectory(changed)) {
                        handleChange(changed.toFile());
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            // close() intentionally wakes the blocking watcher.
        } catch (IOException ex) {
            owner.getLogger().log(Level.SEVERE, "Plugin file watcher failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            watchService = null;
            running = false;
        }
    }

    private void handleChange(File changedFile) {
        String changedName = changedFile.getName();
        for (Map.Entry<String, WatchEntry> entry : entries.entrySet()) {
            if (entry.getValue().file.getName().equalsIgnoreCase(changedName)) {
                schedule(entry.getKey());
                return;
            }
        }
        if (!changedName.toLowerCase(Locale.ROOT).endsWith(".jar") || !changedFile.isFile()) {
            return;
        }
        try {
            String key = normalize(PluginDescription.read(changedFile).getName());
            if (entries.containsKey(key)) {
                schedule(key);
            }
        } catch (IOException | InvalidDescriptionException ignored) {
            // The jar may still be in the middle of a replace operation.
        }
    }

    private void scheduleAll() {
        for (String key : entries.keySet()) {
            schedule(key);
        }
    }

    private void schedule(String key) {
        if (!pendingReloads.add(key)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(owner, () -> reloadIfChanged(key), 10L);
    }

    private void reloadIfChanged(String key) {
        pendingReloads.remove(key);
        WatchEntry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        File currentFile = lifecycleManager.findPluginFile(entry.pluginName).orElse(null);
        if (currentFile == null || !currentFile.isFile()) {
            PhrCommand.send(owner, entry.sender, "&eWatched jar for " + entry.pluginName + " was removed; waiting for it to return.");
            return;
        }
        String hash = hash(currentFile);
        if (hash.equals(entry.hash)) {
            return;
        }
        entry.file = currentFile;
        entry.hash = hash;
        Plugin loadedPlugin = lifecycleManager.getPlugin(entry.pluginName).orElse(null);
        if (loadedPlugin == null) {
            entries.remove(key);
            PhrCommand.send(owner, entry.sender, "&eStopped watching " + entry.pluginName + ": it is no longer loaded.");
            return;
        }
        PhrCommand.send(owner, entry.sender, "&7Detected change in &f" + currentFile.getName() + "&7; reloading.");
        for (OperationResult result : lifecycleManager.reloadPlugins(List.of(loadedPlugin))) {
            PhrCommand.sendResult(owner, entry.sender, result);
        }
    }

    public synchronized void close() {
        running = false;
        entries.clear();
        pendingReloads.clear();
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ex) {
                owner.getLogger().log(Level.FINE, "Could not close plugin watcher", ex);
            }
        }
    }

    private static String hash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file.toPath())));
        } catch (IOException | NoSuchAlgorithmException ex) {
            return file.length() + ":" + file.lastModified();
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class WatchEntry {
        private final String pluginName;
        private final CommandSender sender;
        private volatile File file;
        private volatile String hash;

        private WatchEntry(String pluginName, File file, CommandSender sender) {
            this.pluginName = pluginName;
            this.file = file;
            this.sender = sender;
            this.hash = hash(file);
        }
    }
}
