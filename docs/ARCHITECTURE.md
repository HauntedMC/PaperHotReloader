# Architecture Overview

PaperHotReloader provides a command-driven lifecycle boundary around Bukkit plugins.

## Components

- `PaperHotReloader`: plugin bootstrap, configuration, and Paper's Brigadier-backed command registration.
- `PhrCommand`: permission checks, parsing, operator feedback, completion, and command-tree visibility.
- `BukkitPluginLifecycleManager`: dependency ordering plus load, disable, unload, and reload operations.
- `PluginWatchManager`: asynchronous file watching, debounce, and main-thread reload scheduling.
- `PluginDescription`: reads `plugin.yml` metadata from jars without loading them.

## Lifecycle Flow

1. Commands resolve requested loaded plugins or jar files.
2. Hard dependencies are ordered before consumers and protected from unsafe operations.
3. Reload disables plugins, unregisters Bukkit/Paper state, closes class loaders, and loads jars again.
4. The watcher converts a jar change into one debounced main-thread reload.

## Compatibility Boundary

Bukkit has no public unload API. Cleanup deliberately isolates reflective Paper/Bukkit registry access in
`BukkitPluginLifecycleManager`. This is why the project maintains a real Paper acceptance test in addition to
unit tests.
