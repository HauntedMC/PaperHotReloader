# Testing and Quality

## Unit Tests

Run deterministic tests with:

```bash
./mvnw -B -ntp test
```

The suite covers plugin metadata parsing, dependency ordering, and command completion helpers. JaCoCo reports
are written to `target/site/jacoco/index.html`.

## Paper Platform Acceptance

Run the real-server test with:

```bash
./mvnw -B -ntp -Pplatform-acceptance verify
```

It downloads pinned the HauntedPlatform-pinned Paper runtime and verifies PHR against temporary sample plugins. The test exercises
`help`, configuration reload, listing and metadata commands, load, dependency protection, reload, unload,
watch/unwatch, file-triggered reload, and PHR self-restart.

For troubleshooting, preserve the temporary server directory:

```bash
PHR_ACCEPTANCE_KEEP_WORK_DIRECTORY=true ./mvnw -B -ntp -Pplatform-acceptance verify
```
