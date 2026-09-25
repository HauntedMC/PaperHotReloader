#!/usr/bin/env bash
set -euo pipefail

root_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
artifact="${PHR_ARTIFACT:-}"
paper_api_classpath="$(cat "${PHR_PAPER_API_CLASSPATH:?Missing classpath file}")"
paper_version="${PHR_RUNTIME_VERSION:?Missing Paper runtime version}"
paper_build="${PHR_RUNTIME_BUILD:?Missing Paper runtime build}"
paper_sha256="${PHR_RUNTIME_SHA256:?Missing Paper runtime checksum}"
paper_url="https://fill-data.papermc.io/v1/objects/$paper_sha256/paper-$paper_version-$paper_build.jar"
work_directory="${PHR_ACCEPTANCE_WORK_DIRECTORY:-$(mktemp -d)}"
created_work_directory="${PHR_ACCEPTANCE_WORK_DIRECTORY:+false}"
created_work_directory="${created_work_directory:-true}"
keep_work_directory="${PHR_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
paper_pid=""
paper_input_fd=""

fail() {
    echo "PaperHotReloader acceptance failure: $*" >&2
    exit 1
}

require() {
    command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

log_count() {
    grep -Ec -- "$2" "$1" 2>/dev/null || true
}

wait_for_log() {
    local file=$1 expected=$2 deadline=$((SECONDS + 120))
    while ((SECONDS < deadline)); do
        grep -Eq -- "$expected" "$file" && return
        if grep -Eq 'Exception in thread|Could not load|Error occurred while enabling|PHR_ACCEPTANCE_FAIL' "$file"; then
            fail "Paper reported a boot failure while waiting for: $expected"
        fi
        sleep 1
    done
    fail "Timed out waiting for: $expected"
}

wait_for_new_log() {
    local file=$1 expected=$2 previous_count=$3 current_count deadline=$((SECONDS + 60))
    while ((SECONDS < deadline)); do
        current_count=$(log_count "$file" "$expected")
        [[ "$current_count" -gt "$previous_count" ]] && return
        if grep -Eq 'Exception in thread|Could not load|Error occurred while enabling|PHR_ACCEPTANCE_FAIL' "$file"; then
            fail "Paper reported a runtime failure while waiting for: $expected"
        fi
        sleep 1
    done
    fail "Timed out waiting for new log entry: $expected"
}

send_command() {
    printf '%s\n' "$1" >&"$paper_input_fd"
}

cleanup() {
    local exit_code=$?
    if [[ -n "$paper_input_fd" ]]; then
        if ! printf 'stop\n' >&"$paper_input_fd"; then
            : # The Paper console may already have closed its FIFO.
        fi
    fi
    if [[ -n "$paper_pid" ]]; then
        local deadline=$((SECONDS + 30))
        while kill -0 "$paper_pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 1; done
        if kill -0 "$paper_pid" 2>/dev/null; then
            kill "$paper_pid" 2>/dev/null || true
        fi
        wait "$paper_pid" 2>/dev/null || true
    fi
    if [[ -f "$work_directory/paper/paper.log" && $exit_code -ne 0 ]]; then
        tail -n 250 "$work_directory/paper/paper.log" >&2 || true
    fi
    if [[ "$keep_work_directory" == "true" || "$created_work_directory" != "true" ]]; then
        echo "PaperHotReloader acceptance logs retained in $work_directory" >&2
    else
        rm -rf "$work_directory"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

for command in curl java javac jar sha256sum; do require "$command"; done
[[ -f "$artifact" ]] || fail "Missing PHR artifact: $artifact"
[[ -n "$paper_api_classpath" ]] || fail "Missing Paper API compilation classpath"
mkdir -p "$work_directory/paper/plugins" "$work_directory/sample/classes" "$work_directory/sample/resources"

curl --fail --silent --show-error --location --output "$work_directory/paper/paper.jar" "$paper_url"
[[ "$(sha256sum "$work_directory/paper/paper.jar" | awk '{print $1}')" == "$paper_sha256" ]] \
    || fail "Downloaded Paper runtime checksum mismatch"
cp "$artifact" "$work_directory/paper/plugins/PaperHotReloader.jar"

sample_source="$root_directory/src/acceptance/sample-plugin/AcceptancePlugin.java"
"${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/javac" --release 25 -cp "$paper_api_classpath" -d "$work_directory/sample/classes" "$sample_source"

build_sample_plugin() {
    local destination=$1 descriptor=$2 marker=$3
    rm -rf "$work_directory/sample/resources"
    mkdir -p "$work_directory/sample/resources"
    cp "$descriptor" "$work_directory/sample/resources/plugin.yml"
    sed -i "s/\${marker}/$marker/" "$work_directory/sample/resources/plugin.yml"
    printf '%s\n' "$marker" >"$work_directory/sample/resources/marker.txt"
    jar --create --file "$destination" -C "$work_directory/sample/classes" . -C "$work_directory/sample/resources" .
}

build_sample_plugin \
    "$work_directory/sample/PhrAcceptanceSample.jar" \
    "$root_directory/src/acceptance/sample-plugin/sample-plugin.yml" \
    "v1"
build_sample_plugin \
    "$work_directory/sample/PhrAcceptanceConsumer.jar" \
    "$root_directory/src/acceptance/sample-plugin/consumer-plugin.yml" \
    "consumer"
printf '%s\n' 'eula=true' >"$work_directory/paper/eula.txt"
printf '%s\n' 'server-port=0' 'level-type=minecraft:flat' >"$work_directory/paper/server.properties"
mkfifo "$work_directory/paper/console.in"
(cd "$work_directory/paper" && exec "${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/java" -Xms512M -Xmx1G -jar paper.jar --nogui <console.in >paper.log 2>&1) &
paper_pid=$!
exec {paper_input_fd}>"$work_directory/paper/console.in"
paper_log="$work_directory/paper/paper.log"

wait_for_log "$paper_log" 'PaperHotReloader enabled'

# Keep the sample jars outside plugins/ until PHR itself has started; their first lifecycle transition must
# therefore happen through /phr loadplugin rather than Paper's boot loader.
cp "$work_directory/sample/PhrAcceptanceSample.jar" "$work_directory/paper/plugins/PhrAcceptanceSample.jar"
cp "$work_directory/sample/PhrAcceptanceConsumer.jar" "$work_directory/paper/plugins/PhrAcceptanceConsumer.jar"

for check in \
    'phr help|PaperHotReloader commands' \
    'phr reload|Configuration reloaded' \
    'phr plugins -v|Plugins \(' \
    'phr plugininfo PaperHotReloader|Main:' \
    'phr commandinfo paperhotreloader|owned by'; do
    command=${check%%|*}
    expected=${check#*|}
    before=$(log_count "$paper_log" "$expected")
    send_command "$command"
    wait_for_new_log "$paper_log" "$expected" "$before"
done

sample_enabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v1')
consumer_enabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceConsumer marker=consumer')
send_command 'phr loadplugin PhrAcceptanceConsumer.jar PhrAcceptanceSample.jar'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v1' "$sample_enabled_before"
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceConsumer marker=consumer' "$consumer_enabled_before"

for check in \
    'phr plugininfo PhrAcceptanceSample|PhrAcceptanceSample' \
    'phr commandinfo acceptanceprobe|owned by.*PhrAcceptanceSample' \
    'phr plugins --version|PhrAcceptanceConsumer' \
    'acceptanceprobe|PHR_SAMPLE_COMMAND name=PhrAcceptanceSample marker=v1'; do
    command=${check%%|*}
    expected=${check#*|}
    before=$(log_count "$paper_log" "$expected")
    send_command "$command"
    wait_for_new_log "$paper_log" "$expected" "$before"
done

blocked_before=$(log_count "$paper_log" 'Blocked: PhrAcceptanceConsumer depends on PhrAcceptanceSample')
send_command 'phr unloadplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'Blocked: PhrAcceptanceConsumer depends on PhrAcceptanceSample' "$blocked_before"

consumer_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceConsumer')
send_command 'phr reloadplugin PhrAcceptanceConsumer'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceConsumer' "$consumer_disabled_before"
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceConsumer marker=consumer' "$consumer_enabled_before"

consumer_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceConsumer')
sample_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample')
send_command 'phr unloadplugin PhrAcceptanceConsumer'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceConsumer' "$consumer_disabled_before"
send_command 'phr unloadplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample' "$sample_disabled_before"

sample_enabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v1')
send_command 'phr loadplugin PhrAcceptanceSample.jar'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v1' "$sample_enabled_before"

watch_before=$(log_count "$paper_log" 'watching PhrAcceptanceSample.jar')
send_command 'phr watchplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'watching PhrAcceptanceSample.jar' "$watch_before"
build_sample_plugin \
    "$work_directory/PhrAcceptanceSample-v2.jar" \
    "$root_directory/src/acceptance/sample-plugin/sample-plugin.yml" \
    "v2"
sample_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample')
sample_v2_enabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v2')
mv "$work_directory/PhrAcceptanceSample-v2.jar" "$work_directory/paper/plugins/PhrAcceptanceSample.jar"
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample' "$sample_disabled_before"
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v2' "$sample_v2_enabled_before"

unwatch_before=$(log_count "$paper_log" 'stopped watching')
send_command 'phr unwatchplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'stopped watching' "$unwatch_before"
sample_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample')
sample_v2_enabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v2')
send_command 'phr reloadplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample' "$sample_disabled_before"
wait_for_new_log "$paper_log" 'PHR_SAMPLE_ENABLED name=PhrAcceptanceSample marker=v2' "$sample_v2_enabled_before"

sample_disabled_before=$(log_count "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample')
send_command 'phr unloadplugin PhrAcceptanceSample'
wait_for_new_log "$paper_log" 'PHR_SAMPLE_DISABLED name=PhrAcceptanceSample' "$sample_disabled_before"

phr_enabled_before=$(log_count "$paper_log" 'PaperHotReloader enabled')
send_command 'phr restart --force'
wait_for_new_log "$paper_log" 'PaperHotReloader enabled' "$phr_enabled_before"
plugins_before=$(log_count "$paper_log" 'Plugins \(')
send_command 'phr plugins'
wait_for_new_log "$paper_log" 'Plugins \(' "$plugins_before"

echo 'PaperHotReloader platform acceptance passed.'
