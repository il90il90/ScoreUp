#!/usr/bin/env bash
# Restores an offline dependency mirror produced by scripts/build-deps-mirror.sh from a git ref
# (default origin/deps-mirror, directory deps-mirror/) into .deps-mirror/ (+ Android platform SDK).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
ref="${1:-origin/deps-mirror}"
tmp="$(mktemp -d)"
git -C "$root" archive "$ref" deps-mirror | tar -x -C "$tmp"
cat "$tmp"/deps-mirror/mirror.tgz.part-* > "$tmp/mirror.tgz"
echo "$(cat "$tmp/deps-mirror/mirror.sha256")  $tmp/mirror.tgz" | sha256sum -c -
rm -rf "$root/.deps-mirror" && mkdir -p "$root/.deps-mirror"
tar -xzf "$tmp/mirror.tgz" -C "$root/.deps-mirror"
rm -rf "$tmp"
echo "Mirror restored to $root/.deps-mirror"
