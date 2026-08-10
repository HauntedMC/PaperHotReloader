package nl.hauntedmc.paperhotreloader.managers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import nl.hauntedmc.paperhotreloader.PaperHotReloader;
import nl.hauntedmc.paperhotreloader.utils.PluginDescription;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.UnknownDependencyException;

/**
 * Bukkit lifecycle operations. Bukkit intentionally has no unload API, so only the cleanup
 * boundary is reflective; command parsing and dependency validation remain API based.
 */
public final class BukkitPluginLifecycleManager {

    private final PaperHotReloader owner;
    private final PluginManager pluginManager;

    public BukkitPluginLifecycleManager(PaperHotReloader owner) {
        this.owner = owner;
        this.pluginManager = Bukkit.getPluginManager();
    }

    public File getPluginsFolder() {
        return owner.getDataFolder().getParentFile();
    }

    public List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>(List.of(pluginManager.getPlugins()));
        plugins.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
        return plugins;
    }

    public Optional<Plugin> getPlugin(String name) {
        return Optional.ofNullable(pluginManager.getPlugin(name));
    }

    public File[] getPluginJars() {
        File folder = getPluginsFolder();
        File[] jars = folder == null ? null : folder.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        return jars == null ? new File[0] : jars;
    }

    public Optional<File> getPluginFile(Plugin plugin) {
        try {
            if (plugin.getClass().getProtectionDomain().getCodeSource() == null) {
                return Optional.empty();
            }
            return Optional.of(new File(plugin.getClass().getProtectionDomain().getCodeSource()
                    .getLocation().toURI()));
        } catch (URISyntaxException ex) {
            owner.getLogger().log(Level.WARNING, "Cannot resolve jar for " + plugin.getName(), ex);
            return Optional.empty();
        }
    }

    public Optional<File> findPluginFile(String name) {
        for (File jar : getPluginJars()) {
            try {
                if (PluginDescription.read(jar).getName().equalsIgnoreCase(name)) {
                    return Optional.of(jar);
                }
            } catch (IOException | InvalidDescriptionException ignored) {
                // A non-plugin jar in plugins/ is not a candidate.
            }
        }
        return Optional.empty();
    }

    public List<Plugin> getDependents(String pluginName) {
        List<Plugin> dependents = new ArrayList<>();
        for (Plugin candidate : getPlugins()) {
            if (candidate.getName().equalsIgnoreCase(pluginName)) {
                continue;
            }
            for (String dependency : candidate.getDescription().getDepend()) {
                if (dependency.equalsIgnoreCase(pluginName)) {
                    dependents.add(candidate);
                    break;
                }
            }
        }
        return dependents;
    }

    public OperationResult loadPlugin(File file) {
        if (!file.isFile()) {
            return OperationResult.fail(file.getName(), "jar does not exist");
        }
        try {
            PluginDescriptionFile description = PluginDescription.read(file);
            if (getPlugin(description.getName()).isPresent()) {
                return OperationResult.fail(description.getName(), "already loaded");
            }
            Plugin plugin = pluginManager.loadPlugin(file);
            pluginManager.enablePlugin(plugin);
            return OperationResult.ok(plugin.getName(), "loaded and enabled");
        } catch (IOException | InvalidDescriptionException ex) {
            return OperationResult.fail(file.getName(), "invalid plugin description: " + ex.getMessage());
        } catch (InvalidPluginException | UnknownDependencyException ex) {
            return OperationResult.fail(file.getName(), "could not load: " + ex.getMessage());
        } catch (RuntimeException ex) {
            owner.getLogger().log(Level.WARNING, "Unable to load " + file, ex);
            return OperationResult.fail(file.getName(), "could not load; see server log");
        }
    }

    public List<OperationResult> loadPlugins(List<File> files) {
        Map<String, File> byName = new LinkedHashMap<>();
        Map<String, PluginDescriptionFile> descriptions = new HashMap<>();
        for (File file : files) {
            try {
                PluginDescriptionFile description = PluginDescription.read(file);
                String key = normalize(description.getName());
                if (byName.putIfAbsent(key, file) != null) {
                    return List.of(OperationResult.fail(file.getName(), "plugin was specified more than once"));
                }
                descriptions.put(key, description);
                if (getPlugin(description.getName()).isPresent()) {
                    return List.of(OperationResult.fail(description.getName(), "already loaded"));
                }
            } catch (IOException | InvalidDescriptionException ex) {
                return List.of(OperationResult.fail(file.getName(), "invalid plugin description: " + ex.getMessage()));
            }
        }

        List<String> order = dependencyOrder(descriptions);
        if (order == null) {
            return List.of(OperationResult.fail("plugins", "circular hard dependency detected"));
        }
        for (String key : order) {
            for (String dependency : PluginDescription.hardDependencies(descriptions.get(key))) {
                if (!descriptions.containsKey(normalize(dependency)) && getPlugin(dependency).isEmpty()) {
                    return List.of(OperationResult.fail(descriptions.get(key).getName(),
                            "missing hard dependency " + dependency));
                }
            }
        }

        List<OperationResult> results = new ArrayList<>();
        for (String key : order) {
            OperationResult result = loadPlugin(byName.get(key));
            results.add(result);
            if (!result.success()) {
                break;
            }
        }
        return results;
    }

    public List<OperationResult> disablePlugins(List<Plugin> plugins) {
        List<Plugin> ordered = orderLoadedPlugins(plugins);
        if (ordered == null) {
            return List.of(OperationResult.fail("plugins", "circular hard dependency detected"));
        }
        Collections.reverse(ordered);
        List<OperationResult> results = new ArrayList<>();
        for (Plugin plugin : ordered) {
            if (!plugin.isEnabled()) {
                results.add(OperationResult.fail(plugin.getName(), "already disabled"));
                continue;
            }
            pluginManager.disablePlugin(plugin);
            results.add(OperationResult.ok(plugin.getName(), "disabled"));
        }
        return results;
    }

    public List<OperationResult> unloadPlugins(List<Plugin> plugins) {
        List<Plugin> ordered = orderLoadedPlugins(plugins);
        if (ordered == null) {
            return List.of(OperationResult.fail("plugins", "circular hard dependency detected"));
        }
        Collections.reverse(ordered);
        List<OperationResult> results = new ArrayList<>();
        for (Plugin plugin : ordered) {
            if (plugin.isEnabled()) {
                results.add(OperationResult.fail(plugin.getName(), "must be disabled before unloading"));
                break;
            }
            results.add(unloadDisabledPlugin(plugin));
            if (!results.get(results.size() - 1).success()) {
                break;
            }
        }
        return results;
    }

    /** Disables enabled plugins and then removes their Bukkit registrations and class loaders. */
    public List<OperationResult> disableAndUnloadPlugins(List<Plugin> plugins) {
        List<OperationResult> disabled = disablePlugins(plugins);
        if (disabled.stream().anyMatch(result -> !result.success() && !result.message().equals("already disabled"))) {
            return disabled;
        }
        List<OperationResult> unloaded = unloadPlugins(plugins);
        List<OperationResult> results = new ArrayList<>(disabled.size() + unloaded.size());
        results.addAll(disabled);
        results.addAll(unloaded);
        return results;
    }

    public List<OperationResult> reloadPlugins(List<Plugin> plugins) {
        List<Plugin> ordered = orderLoadedPlugins(plugins);
        if (ordered == null) {
            return List.of(OperationResult.fail("plugins", "circular hard dependency detected"));
        }
        Map<String, File> files = new LinkedHashMap<>();
        for (Plugin plugin : ordered) {
            File file = getPluginFile(plugin).orElseGet(() -> findPluginFile(plugin.getName()).orElse(null));
            if (file == null || !file.isFile()) {
                return List.of(OperationResult.fail(plugin.getName(), "plugin jar no longer exists"));
            }
            files.put(normalize(plugin.getName()), file);
        }

        List<OperationResult> disabled = disablePlugins(ordered);
        if (disabled.stream().anyMatch(result -> !result.success() && !result.message().equals("already disabled"))) {
            return disabled;
        }
        List<OperationResult> unloaded = unloadPlugins(ordered);
        if (unloaded.stream().anyMatch(result -> !result.success())) {
            return unloaded;
        }
        return loadPlugins(new ArrayList<>(files.values()));
    }

    private OperationResult unloadDisabledPlugin(Plugin plugin) {
        try {
            HandlerList.unregisterAll(plugin);
            Bukkit.getScheduler().cancelTasks(plugin);
            Bukkit.getServicesManager().unregisterAll(plugin);
            unregisterPluginCommands(plugin);
            removeFromPluginManager(plugin);
            closeClassLoader(plugin);
            return OperationResult.ok(plugin.getName(), "unloaded");
        } catch (ReflectiveOperationException | IOException ex) {
            owner.getLogger().log(Level.SEVERE, "Could not fully unload " + plugin.getName(), ex);
            return OperationResult.fail(plugin.getName(), "unload cleanup failed; restart the server");
        }
    }

    @SuppressWarnings("unchecked")
    private void removeFromPluginManager(Plugin plugin) throws ReflectiveOperationException {
        removeFromPluginRegistry(pluginManager, plugin);

        // Paper 26.2 retains the legacy SimplePluginManager as Bukkit's facade but delegates dynamic
        // loads to PaperPluginManagerImpl. Both registries must forget the old instance before reload.
        Field paperPluginManagerField = findOptionalField(pluginManager.getClass(), "paperPluginManager");
        if (paperPluginManagerField == null) {
            return;
        }
        Object paperPluginManager = paperPluginManagerField.get(pluginManager);
        if (paperPluginManager == null || paperPluginManager == pluginManager) {
            return;
        }
        Field instanceManagerField = findOptionalField(paperPluginManager.getClass(), "instanceManager");
        removeFromPluginRegistry(instanceManagerField == null
                ? paperPluginManager
                : instanceManagerField.get(paperPluginManager), plugin);
    }

    @SuppressWarnings("unchecked")
    private static void removeFromPluginRegistry(Object registry, Plugin plugin) throws ReflectiveOperationException {
        Field pluginsField = findField(registry.getClass(), "plugins");
        Field namesField = findField(registry.getClass(), "lookupNames");
        List<Plugin> plugins = (List<Plugin>) pluginsField.get(registry);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) namesField.get(registry);
        plugins.remove(plugin);
        lookupNames.entrySet().removeIf(entry -> entry.getValue() == plugin);
    }

    @SuppressWarnings("unchecked")
    private void unregisterPluginCommands(Plugin plugin) throws ReflectiveOperationException {
        Field commandMapField = findField(pluginManager.getClass(), "commandMap");
        Object commandMap = commandMapField.get(pluginManager);
        Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
        Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
        Set<Command> ownedCommands = new HashSet<>();
        for (Command command : knownCommands.values()) {
            Method getPlugin = findOptionalMethod(command.getClass(), "getPlugin");
            if (getPlugin != null && getPlugin.invoke(command) == plugin) {
                ownedCommands.add(command);
            }
        }
        // Paper's Brigadier-backed map exposes an entry set whose iterator is intentionally immutable,
        // while Map.remove(key, value) performs the required dispatcher cleanup.
        for (Map.Entry<String, Command> entry : new ArrayList<>(knownCommands.entrySet())) {
            if (ownedCommands.contains(entry.getValue())) {
                knownCommands.remove(entry.getKey(), entry.getValue());
            }
        }
        for (Command command : ownedCommands) {
            command.unregister((org.bukkit.command.CommandMap) commandMap);
        }
    }

    private void closeClassLoader(Plugin plugin) throws IOException {
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (loader instanceof Closeable closeable) {
            closeable.close();
        }
    }

    private List<Plugin> orderLoadedPlugins(Collection<Plugin> plugins) {
        Map<String, Plugin> byName = new LinkedHashMap<>();
        Map<String, PluginDescriptionFile> descriptions = new HashMap<>();
        for (Plugin plugin : plugins) {
            String key = normalize(plugin.getName());
            byName.put(key, plugin);
            descriptions.put(key, plugin.getDescription());
        }
        List<String> order = dependencyOrder(descriptions);
        if (order == null) {
            return null;
        }
        List<Plugin> ordered = new ArrayList<>();
        for (String key : order) {
            ordered.add(byName.get(key));
        }
        return ordered;
    }

    static List<String> dependencyOrder(Map<String, PluginDescriptionFile> descriptions) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, PluginDescriptionFile> entry : descriptions.entrySet()) {
            Set<String> selectedDependencies = new HashSet<>();
            for (String dependency : PluginDescription.hardDependencies(entry.getValue())) {
                String key = normalize(dependency);
                if (descriptions.containsKey(key)) {
                    selectedDependencies.add(key);
                }
            }
            dependencies.put(entry.getKey(), selectedDependencies);
        }
        List<String> order = new ArrayList<>();
        while (!dependencies.isEmpty()) {
            List<String> ready = dependencies.entrySet().stream()
                    .filter(entry -> entry.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            if (ready.isEmpty()) {
                return null;
            }
            for (String key : ready) {
                dependencies.remove(key);
                dependencies.values().forEach(value -> value.remove(key));
                order.add(key);
            }
        }
        return order;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " on " + type.getName());
    }

    private static Field findOptionalField(Class<?> type, String name) {
        try {
            return findField(type, name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method findOptionalMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
