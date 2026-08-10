# PaperHotReloader

[![CI Quality](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci-tests-and-coverage.yml)
[![Paper Acceptance](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/PaperHotReloader/actions/workflows/ci-tests-and-coverage.yml)
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

## Quick Start

1. Download `PaperHotReloader-<version>.jar` from the latest release.
2. Place it in the server's `plugins/` directory.
3. Start Paper once, then run `/phr help` from console or as an operator.
4. Grant the relevant `paperhotreloader.*` permission nodes to server operators.

## Requirements

- Java 25
- Paper 26.2 or a compatible Bukkit/Paper fork

## Build From Source

Build with Java 25 and the Paper 26.2 development bundle:

```bash
./gradlew clean build
```

Output jar: `build/libs/PaperHotReloader-<version>.jar`

## Release Workflow

Create a semantic-version bump, commit, and release tag locally:

```bash
scripts/bump-version.sh patch
```

Add `--push` to publish the branch and tag after the repository is reviewed:

```bash
scripts/bump-version.sh minor --push
```

Run the fast unit suite with `./gradlew test`. `./gradlew acceptanceTest` follows the ServerFeatures
acceptance pattern: it downloads the pinned Paper 26.2 build, starts a disposable server, builds temporary
sample plugins, and verifies every PHR command plus dependency protection, dynamic reload, file watching,
and PHR's own restart.

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
