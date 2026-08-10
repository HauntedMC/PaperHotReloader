# Configuration Guide

PaperHotReloader creates `plugins/PaperHotReloader/config.yml` on first boot.

```yaml
prefix: '&8[&bPHR&8] &7'
```

The prefix accepts Bukkit legacy color codes. Apply a configuration change with `/phr reload`.

## Permission Nodes

`paperhotreloader.*` grants all commands and defaults to operators. Individual nodes are available for:

- `paperhotreloader.help`
- `paperhotreloader.reload`
- `paperhotreloader.restart`
- `paperhotreloader.loadplugin`
- `paperhotreloader.unloadplugin`
- `paperhotreloader.reloadplugin`
- `paperhotreloader.watchplugin`
- `paperhotreloader.plugininfo`
- `paperhotreloader.commandinfo`
- `paperhotreloader.plugins`

## Operational Guidance

- Keep plugin jars directly in the server `plugins/` directory when using `/phr loadplugin`.
- Hard dependencies block unload, reload, and watch operations unless `--force` or `-f` is supplied.
- Test hot reload in staging first. Plugins with static global state, native resources, or unmanaged threads may still require a full server restart.
