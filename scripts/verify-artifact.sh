#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
version="$(sed -n 's/.*<revision>\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)<\/revision>.*/\1/p' pom.xml | head -n 1)"
[[ -n "$version" ]] || { echo 'Missing semantic Maven revision' >&2; exit 1; }
artifact="target/PaperHotReloader-$version.jar"
[[ -f "$artifact" ]] || { echo "Missing distributable $artifact" >&2; exit 1; }
entries="$(jar tf "$artifact")"
descriptor="$(unzip -p "$artifact" plugin.yml)"
grep -Fxq "version: '$version'" <<<"$descriptor"
grep -Fq 'nl/hauntedmc/paperhotreloader/PaperHotReloader.class' <<<"$entries"
if grep -Eq '^org/bukkit/|^io/papermc/paper/' <<<"$entries"; then
  echo 'Plugin jar contains provided Paper classes' >&2
  exit 1
fi
echo "Artifact audit passed: $artifact"
