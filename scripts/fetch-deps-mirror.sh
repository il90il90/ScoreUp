#!/usr/bin/env bash
# Restores the CI-built offline dependency mirror (see .github/workflows/deps-mirror.yml)
# into .deps-mirror/ so Gradle can build without Google Maven access.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
git -C "$root" fetch -q origin deps-mirror
git -C "$root" archive origin/deps-mirror | tar -x -C "$tmp"
cat "$tmp"/mirror.tgz.part-* > "$tmp/mirror.tgz"
echo "$(cat "$tmp/mirror.sha256")  $tmp/mirror.tgz" | sha256sum -c -
rm -rf "$root/.deps-mirror" && mkdir -p "$root/.deps-mirror"
tar -xzf "$tmp/mirror.tgz" -C "$root/.deps-mirror"
rm -rf "$tmp"
echo "Mirror restored to $root/.deps-mirror (built from $(git -C "$root" show origin/deps-mirror:source-commit))"
