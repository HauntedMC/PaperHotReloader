# Development Notes

## Local Workflow

```bash
./gradlew compileJava
./gradlew test
./gradlew check
```

Use the platform acceptance task for changes to plugin loading, unloading, commands, or file watching:

```bash
./gradlew acceptanceTest
```

The acceptance task is intentionally isolated: it builds temporary sample plugins and boots a disposable
Paper server. Set `PHR_ACCEPTANCE_KEEP_WORK_DIRECTORY=true` to retain its files after a failure.

## Engineering Guidelines

- Keep Bukkit/Paper internals isolated in the lifecycle manager.
- Maintain dependency ordering and explicit operator-facing failure messages.
- Ensure watcher and scheduler resources close when PHR is disabled.
- Add a unit test for deterministic behavior and extend acceptance coverage for runtime behavior.
