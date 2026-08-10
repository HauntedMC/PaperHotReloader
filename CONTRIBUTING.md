# Contributing

Thanks for taking the time to contribute to PaperHotReloader.

## Before You Start

- Use Java 25 and Gradle.
- Run the unit suite before opening a pull request.
- Run the Paper acceptance suite for lifecycle, command, or watcher changes.

## Setup

```bash
git clone https://github.com/HauntedMC/PaperHotReloader.git
cd PaperHotReloader
./gradlew compileJava
```

## Local Validation

Minimum checks:

```bash
./gradlew test
shellcheck src/acceptance/run-acceptance.sh scripts/bump-version.sh
```

Run the full Paper acceptance suite:

```bash
./gradlew acceptanceTest
```

The acceptance task downloads the pinned Paper runtime, starts a temporary server, and builds temporary
plugins. It requires `curl`, `java`, `javac`, `jar`, and `sha256sum`.

## Pull Request Expectations

1. Create a focused branch from `main`.
2. Include tests with behavior changes.
3. Update documentation for operator-visible changes.
4. Describe lifecycle, configuration, or compatibility impact in the pull request.
5. Do not include generated `build/` output.

## Security

Do not report vulnerabilities in public issues. See [SECURITY.md](SECURITY.md).
