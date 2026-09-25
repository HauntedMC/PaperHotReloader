# PaperHotReloader

[![CI](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/PaperHotReloader?sort=semver)](https://github.com/HauntedMC/PaperHotReloader/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/PaperHotReloader)](LICENSE)

Hot-load, unload, reload, and watch Bukkit plugins without restarting a Paper server.

## Features

- Load plugin jars from the server `plugins/` directory.
- Disable, unload, and reload plugins at runtime.
- Preserve dependency order and block dependent-plugin operations unless `--force` (or `-f`) is supplied.
- Watch plugin jars and debounce filesystem changes into reloads on the server thread.
- Inspect plugin metadata, command ownership, and loaded plugins.
- Use Paper's Brigadier command lifecycle so `/phr` is invisible to players without a PHR permission.

## Quick Start

1. Download `PaperHotReloader-<version>.jar` from the latest release.
2. Place it in the server's `plugins/` directory.
3. Start Paper once, then run `/phr help` from console or as an operator.
4. Grant the relevant `paperhotreloader.*` permission nodes to server operators.

## Requirements

- Java 25
- Paper 26.2 or a compatible Bukkit/Paper fork

## Build From Source

Use Java 25. HauntedPlatform is resolved from GitHub Packages; set `PACKAGES_USER` and `PACKAGES_TOKEN` (with `read:packages`) for a fresh local Maven cache. The committed `.mvn/settings.xml` reads these variables.

```bash
./mvnw -B -ntp verify
```

Output jar: `target/PaperHotReloader-<version>.jar`

## Release workflow

From clean `main`, run `./tools/release/update-version patch --pr` to open a reviewed version PR. CI tests the PR; after merge, GitHub Actions publishes the Maven package, verifies that it resolves, and creates the tag and downloadable release jar with a SHA-256 checksum. See [release tooling](tools/release/README.md).

## Commands

- `/phr reload` — reload PaperHotReloader's configuration.
- `/phr restart [--force|-f]` — reload PaperHotReloader itself.
- `/phr loadplugin <jarFiles...>` — load and enable jars from `plugins/`.
- `/phr unloadplugin <plugins...> [--force|-f]` — disable and unload plugins.
- `/phr reloadplugin <plugins...> [--force|-f]` — reload plugins.
- `/phr watchplugin <plugins...> [--force|-f]` — reload watched jars when they change.
- `/phr unwatchplugin <plugin>` — stop watching a plugin.
- `/phr plugininfo <plugin>` — display plugin metadata.
- `/phr commandinfo <command>` — display the owning plugin, when available.
- `/phr plugins [--version|-v]` — list loaded plugins.

## Important compatibility note

Bukkit does not expose plugin unloading as a public API. Unload uses a contained reflection bridge to remove a disabled plugin from Bukkit's registries, event handlers, scheduler, services, commands, and class loader. This is inherently dependent on server internals: test a plugin in staging before relying on hot reload in production, and prefer a full server restart after reloading plugins with static global state or native resources.

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Changelog](CHANGELOG.md)
