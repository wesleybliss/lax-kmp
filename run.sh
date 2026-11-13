#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${JAVA_HOME:-}" && -x /usr/lib/jvm/java-25-openjdk/bin/java ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
fi

if (($#)); then
  args=()
  for a in "$@"; do
    args+=("$(printf '%q' "$a")")
  done
  exec ./gradlew :desktopApp:run --args="${args[*]}"
else
  exec ./gradlew :desktopApp:run
fi
