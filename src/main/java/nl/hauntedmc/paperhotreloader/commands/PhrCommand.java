package nl.hauntedmc.paperhotreloader.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.paperhotreloader.PaperHotReloader;
import nl.hauntedmc.paperhotreloader.managers.BukkitPluginLifecycleManager;
import nl.hauntedmc.paperhotreloader.managers.OperationResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

/** Command surface intentionally mirrors VelocityHotReloader's lifecycle workflow. */
public final class PhrCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "restart", "loadplugin", "unloadplugin", "reloadplugin",
            "watchplugin", "unwatchplugin", "plugininfo", "commandinfo", "plugins"
    );
    private static final Set<String> FORCE_FLAGS = Set.of("--force", "-f");
    private static final Set<String> ACCESS_PERMISSIONS = Set.of(
            "paperhotreloader.help",
            "paperhotreloader.reload",
            "paperhotreloader.restart",
            "paperhotreloader.loadplugin",
            "paperhotreloader.unloadplugin",
            "paperhotreloader.reloadplugin",
            "paperhotreloader.watchplugin",
            "paperhotreloader.plugininfo",
            "paperhotreloader.commandinfo",
            "paperhotreloader.plugins"
    );
    private final PaperHotReloader plugin;
    private final BukkitPluginLifecycleManager lifecycle;

    public PhrCommand(PaperHotReloader plugin) {
        this.plugin = plugin;
        this.lifecycle = plugin.getPluginLifecycleManager();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "help" -> help(sender);
            case "reload" -> reloadConfiguration(sender);
            case "restart" -> restart(sender, args);
            case "loadplugin" -> load(sender, args);
            case "unloadplugin" -> lifecycle(sender, args, "unload", lifecycle::disableAndUnloadPlugins);
            case "reloadplugin" -> lifecycle(sender, args, "reload", lifecycle::reloadPlugins);
            case "watchplugin" -> watch(sender, args);
            case "unwatchplugin" -> unwatch(sender, args);
            case "plugininfo" -> pluginInfo(sender, args);
            case "commandinfo" -> commandInfo(sender, args);
            case "plugins" -> plugins(sender, args);
            default -> send(plugin, sender, "&cUnknown subcommand. Use &f/phr help&c.");
        }
    }

    /**
     * Paper consults this predicate before it sends a command node to a player. Keeping the root hidden
     * here prevents both client-side discovery and the legacy no-permission feedback for unauthorized users.
     */
    @Override
    public boolean canUse(CommandSender sender) {
        return isDiscoverableBy(sender);
    }

    private void help(CommandSender sender) {
        if (!require(sender, "help")) return;
        send(plugin, sender, "&bPaperHotReloader commands");
        sendHelp(sender, "reload", "/phr reload &7- reload PHR configuration");
        sendHelp(sender, "restart", "/phr restart [--force] &7- reload PHR itself");
        sendHelp(sender, "loadplugin", "/phr loadplugin <jar...> &7- load plugin jars");
        sendHelp(sender, "unloadplugin", "/phr unloadplugin <plugin...> [--force] &7- disable and unload");
        sendHelp(sender, "reloadplugin", "/phr reloadplugin <plugin...> [--force] &7- reload plugins");
        sendHelp(sender, "watchplugin", "/phr watchplugin <plugin...> [--force] &7- watch jar changes");
        sendHelp(sender, "watchplugin", "/phr unwatchplugin <plugin> &7- stop watching");
        sendHelp(sender, "plugininfo", "/phr plugininfo <plugin> &7- show plugin metadata");
        sendHelp(sender, "commandinfo", "/phr commandinfo <command> &7- show command owner");
        sendHelp(sender, "plugins", "/phr plugins [-v] &7- list plugins");
    }

    private void sendHelp(CommandSender sender, String action, String message) {
        if (hasPermission(sender, action)) send(plugin, sender, "&f" + message);
    }

    private void reloadConfiguration(CommandSender sender) {
        if (!require(sender, "reload")) return;
        plugin.reloadConfig();
        send(plugin, sender, "&aConfiguration reloaded.");
    }

    private void restart(CommandSender sender, String[] args) {
        if (!require(sender, "restart")) return;
        boolean force = hasForce(args, 1);
        if (!checkDependents(sender, List.of(plugin), force)) return;
        send(plugin, sender, "&7Reloading PaperHotReloader on the next server tick.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (OperationResult result : lifecycle.reloadPlugins(List.of(plugin))) {
                sendResult(plugin, sender, result);
            }
        });
    }

    private void load(CommandSender sender, String[] args) {
        if (!require(sender, "loadplugin")) return;
        if (args.length < 2) {
            send(plugin, sender, "&cSpecify one or more jar names from the plugins directory.");
            return;
        }
        List<File> files = new ArrayList<>();
        File pluginsFolder = lifecycle.getPluginsFolder();
        for (int index = 1; index < args.length; index++) {
            String name = args[index];
            File candidate = new File(pluginsFolder, name);
            if (name.contains("/") || name.contains("\\") || !candidate.isFile()
                    || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                send(plugin, sender, "&cInvalid plugin jar: &f" + name);
                return;
            }
            files.add(candidate);
        }
        sendResults(sender, lifecycle.loadPlugins(files));
    }

    private void lifecycle(
            CommandSender sender,
            String[] args,
            String permission,
            Function<List<Plugin>, List<OperationResult>> action
    ) {
        if (!require(sender, permission + "plugin")) return;
        List<Plugin> targets = parsePlugins(sender, args);
        if (targets.isEmpty()) return;
        if (!checkDependents(sender, targets, hasForce(args, 1))) return;
        sendResults(sender, action.apply(targets));
    }

    private void watch(CommandSender sender, String[] args) {
        if (!require(sender, "watchplugin")) return;
        List<Plugin> targets = parsePlugins(sender, args);
        if (targets.isEmpty()) return;
        if (!checkDependents(sender, targets, hasForce(args, 1))) return;
        for (Plugin target : targets) {
            sendResult(plugin, sender, plugin.getWatchManager().watch(target, sender));
        }
    }

    private void unwatch(CommandSender sender, String[] args) {
        if (!require(sender, "watchplugin")) return;
        if (args.length != 2) {
            send(plugin, sender, "&cUsage: /phr unwatchplugin <plugin>");
            return;
        }
        sendResult(plugin, sender, plugin.getWatchManager().unwatch(args[1]));
    }

    private void pluginInfo(CommandSender sender, String[] args) {
        if (!require(sender, "plugininfo") || args.length != 2) {
            if (args.length != 2) send(plugin, sender, "&cUsage: /phr plugininfo <plugin>");
            return;
        }
        Plugin target = lifecycle.getPlugin(args[1]).orElse(null);
        if (target == null) {
            send(plugin, sender, "&cPlugin not loaded: &f" + args[1]);
            return;
        }
        var description = target.getDescription();
        send(plugin, sender, "&b" + target.getName() + " &7v" + description.getVersion()
                + (target.isEnabled() ? " &a(enabled)" : " &c(disabled)"));
        send(plugin, sender, "&7Main: &f" + description.getMain());
        send(plugin, sender, "&7Authors: &f" + String.join(", ", description.getAuthors()));
        send(plugin, sender, "&7Hard dependencies: &f" + String.join(", ", description.getDepend()));
        lifecycle.getPluginFile(target).ifPresent(file -> send(plugin, sender, "&7Jar: &f" + file.getName()));
    }

    private void commandInfo(CommandSender sender, String[] args) {
        if (!require(sender, "commandinfo") || args.length != 2) {
            if (args.length != 2) send(plugin, sender, "&cUsage: /phr commandinfo <command>");
            return;
        }
        if (args[1].equalsIgnoreCase("paperhotreloader") || args[1].equalsIgnoreCase("phr")) {
            send(plugin, sender, "&7Command &f/" + args[1] + " &7is owned by &f" + plugin.getName());
            return;
        }
        PluginCommand command = Bukkit.getPluginCommand(args[1]);
        if (command == null) {
            send(plugin, sender, "&cNo plugin.yml command named &f" + args[1] + "&c is registered.");
            return;
        }
        send(plugin, sender, "&7Command &f/" + command.getName() + " &7is owned by &f" + command.getPlugin().getName());
    }

    private void plugins(CommandSender sender, String[] args) {
        if (!require(sender, "plugins")) return;
        boolean versions = Arrays.stream(args).skip(1).anyMatch(value -> value.equals("-v") || value.equals("--version"));
        List<Plugin> loaded = lifecycle.getPlugins();
        List<String> entries = new ArrayList<>();
        for (Plugin target : loaded) {
            entries.add((target.isEnabled() ? "&a" : "&c") + target.getName()
                    + (versions ? " &7v" + target.getDescription().getVersion() : ""));
        }
        send(plugin, sender, "&7Plugins (" + loaded.size() + "): " + String.join("&7, ", entries));
    }

    private List<Plugin> parsePlugins(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(plugin, sender, "&cSpecify one or more loaded plugins.");
            return List.of();
        }
        List<Plugin> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 1; index < args.length; index++) {
            if (FORCE_FLAGS.contains(args[index])) continue;
            Plugin target = lifecycle.getPlugin(args[index]).orElse(null);
            if (target == null) {
                send(plugin, sender, "&cPlugin not loaded: &f" + args[index]);
                return List.of();
            }
            if (seen.add(target.getName().toLowerCase(Locale.ROOT))) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) send(plugin, sender, "&cSpecify one or more loaded plugins.");
        return targets;
    }

    private boolean checkDependents(CommandSender sender, Collection<Plugin> targets, boolean force) {
        Set<String> selected = targets.stream().map(target -> target.getName().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<String> dependents = new ArrayList<>();
        for (Plugin target : targets) {
            for (Plugin dependent : lifecycle.getDependents(target.getName())) {
                if (!selected.contains(dependent.getName().toLowerCase(Locale.ROOT))) {
                    dependents.add(dependent.getName() + " depends on " + target.getName());
                }
            }
        }
        if (!dependents.isEmpty() && !force) {
            send(plugin, sender, "&cBlocked: " + String.join("; ", dependents) + ". Use &f--force&c to override.");
            return false;
        }
        return true;
    }

    private boolean require(CommandSender sender, String action) {
        if (hasPermission(sender, action)) return true;
        String permission = permission(action);
        send(plugin, sender, "&cYou do not have permission: &f" + permission);
        return false;
    }

    private static boolean hasPermission(CommandSender sender, String action) {
        return sender.hasPermission(permission(action));
    }

    private static String permission(String action) {
        return "paperhotreloader." + action;
    }

    private static boolean hasForce(String[] args, int start) {
        return Arrays.stream(args).skip(start).anyMatch(FORCE_FLAGS::contains);
    }

    private void sendResults(CommandSender sender, List<OperationResult> results) {
        for (OperationResult result : results) sendResult(plugin, sender, result);
    }

    public static void sendResult(PaperHotReloader plugin, CommandSender sender, OperationResult result) {
        send(plugin, sender, (result.success() ? "&a" : "&c") + result.target() + "&7: " + result.message());
    }

    public static void send(PaperHotReloader plugin, CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.prefix() + message));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            return matching(args[0], SUBCOMMANDS.stream()
                    .filter(subcommand -> hasPermission(sender, permissionFor(subcommand)))
                    .toList());
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, permissionFor(subcommand))) return List.of();
        if (subcommand.equals("loadplugin")) {
            List<String> jars = Arrays.stream(lifecycle.getPluginJars()).map(File::getName).sorted().toList();
            return matching(args[args.length - 1], jars);
        }
        if (List.of("unloadplugin", "reloadplugin", "watchplugin", "unwatchplugin", "plugininfo").contains(subcommand)) {
            List<String> values = new ArrayList<>(lifecycle.getPlugins().stream().map(Plugin::getName).toList());
            if (!subcommand.equals("unwatchplugin") && !subcommand.equals("plugininfo")) {
                values.addAll(FORCE_FLAGS);
            }
            return matching(args[args.length - 1], values);
        }
        if (List.of("unloadplugin", "reloadplugin", "watchplugin", "restart").contains(subcommand)) {
            return matching(args[args.length - 1], FORCE_FLAGS);
        }
        if (subcommand.equals("plugins")) return matching(args[args.length - 1], List.of("-v", "--version"));
        return List.of();
    }

    static boolean isDiscoverableBy(CommandSender sender) {
        return !(sender instanceof org.bukkit.entity.Player)
                || sender.hasPermission("paperhotreloader.*")
                || ACCESS_PERMISSIONS.stream().anyMatch(sender::hasPermission);
    }

    private static String permissionFor(String subcommand) {
        return subcommand.equals("unwatchplugin") ? "watchplugin" : subcommand;
    }

    static List<String> matching(String prefix, Collection<String> values) {
        return values.stream().filter(value -> value.regionMatches(true, 0, prefix, 0, prefix.length()))
                .sorted(Comparator.naturalOrder()).toList();
    }
}
