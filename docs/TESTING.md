# Testing and Quality

## Unit Tests

Run deterministic tests with:

```bash
./gradlew test
```

The suite covers plugin metadata parsing, dependency ordering, and command completion helpers. JaCoCo reports
are written to `build/reports/jacoco/test/html/index.html`.

## Paper Platform Acceptance

Run the real-server test with:

```bash
./gradlew acceptanceTest
```

It downloads pinned Paper 26.2 build 65 and verifies PHR against temporary sample plugins. The test exercises
`help`, configuration reload, listing and metadata commands, load, dependency protection, reload, unload,
watch/unwatch, file-triggered reload, and PHR self-restart.

For troubleshooting, preserve the temporary server directory:

```bash
PHR_ACCEPTANCE_KEEP_WORK_DIRECTORY=true ./gradlew acceptanceTest
```
