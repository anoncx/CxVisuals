#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
for file in versions/*.properties; do
  version="$(basename "$file" .properties)"
  ./gradlew assemble -Pmc="$version"
done
ls -1 build/libs/*.jar
