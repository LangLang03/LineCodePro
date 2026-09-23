#!/usr/bin/env bash
set -euo pipefail

: "${HUXERUI_HOME:?HUXERUI_HOME must point to the installed SDK}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
graph_file="$project_root/.huxerui/generated/libraries.json"

# The HuxerUI CLI also assembles a Debug APK. Gradle builds the requested
# variant later; this configure-only mode supplies its library graph.
mkdir -p "$(dirname "$graph_file")"
cmake -S "$project_root" \
  -B "$project_root/.huxerui/build/library-graph" \
  -G Ninja \
  -DHUXERUI_LIBRARY_GRAPH_ONLY=ON \
  -DHUXERUI_LIBRARY_GRAPH_OUTPUT="$graph_file" \
  -DHUXERUI_HOME="$HUXERUI_HOME"
test -s "$graph_file"
