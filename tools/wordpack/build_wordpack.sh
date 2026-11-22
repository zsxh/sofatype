#!/usr/bin/env bash
set -euo pipefail

VERSION_ARG="${1:-$(date +%Y%m%d).1}"
OUT_DIR="${2:-tools/wordpack/dist}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

export HOME="$PWD/.emulator-adbhome"
export GRADLE_USER_HOME="$PWD/.gradle-home"
export XDG_CACHE_HOME="$PWD/.nix-cache"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"

nix develop -c bash tools/wordpack/pipeline.sh "$VERSION_ARG" "$OUT_DIR"
