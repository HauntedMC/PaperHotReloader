# Contributing

Thanks for taking the time to contribute to PaperHotReloader.

## Before You Start

- Use Java 25 and Maven.
- Run the unit suite before opening a pull request.
- Run the Paper acceptance suite for lifecycle, command, or watcher changes.

## Setup

```bash
git clone https://github.com/HauntedMC/PaperHotReloader.git
cd PaperHotReloader
./mvnw -B -ntp -DskipTests compile
```

## Local Validation

Minimum checks:

```bash
./mvnw -B -ntp test
shellcheck src/acceptance/run-acceptance.sh scripts/verify-artifact.sh tools/release/prepare-version.sh
```

Run the full Paper acceptance suite:

```bash
./mvnw -B -ntp -Pplatform-acceptance verify
```

The acceptance task downloads the pinned Paper runtime, starts a temporary server, and builds temporary
plugins. It requires `curl`, `java`, `javac`, `jar`, and `sha256sum`.

## Pull Request Expectations

1. Create a focused branch from `main`.
2. Include tests with behavior changes.
3. Update documentation for operator-visible changes.
4. Describe lifecycle, configuration, or compatibility impact in the pull request.
5. Do not include generated `target/` output.

## Security

Do not report vulnerabilities in public issues. See [SECURITY.md](SECURITY.md).
