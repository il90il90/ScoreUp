#!/usr/bin/env bash
# Builds an offline dependency mirror (Maven layout + Android platform) for this project and
# commits it as split tarball parts into the current branch's `deps-mirror/` directory.
# Needs: JDK 17+, network access to dl.google.com / maven.google.com.
# Usage: scripts/build-deps-mirror.sh <output-dir>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:?output dir}"
work="$(mktemp -d)"
export ANDROID_HOME="${ANDROID_HOME:-$work/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

echo "== probe"
curl -sS -o /dev/null -w "maven.google.com %{http_code}\n" https://dl.google.com/dl/android/maven2/androidx/core/core/1.15.0/core-1.15.0.pom

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "== android sdk"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sSL -o "$work/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$work/clt.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;34.0.0" "platform-tools" > /dev/null
echo "sdk.dir=$ANDROID_HOME" > "$root/local.properties"

echo "== resolve + build"
export GRADLE_USER_HOME="$work/gradle-home"
mkdir -p "$GRADLE_USER_HOME/init.d"
# Maven Central often rate-limits shared egress IPs; Google's official Central mirror first.
cat > "$GRADLE_USER_HOME/init.d/central-mirror.init.gradle.kts" <<'INIT'
val mirrorUrl = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.addMirror() { if (none { it.name == "centralMirror" }) maven { name = "centralMirror"; url = uri(mirrorUrl) } }
beforeSettings { pluginManagement.repositories.addMirror(); dependencyResolutionManagement.repositories.addMirror() }
INIT
export ROBOLECTRIC_REPO="https://maven-central.storage-download.googleapis.com/maven2"
cd "$root"
chmod +x gradlew
set +e
./gradlew --no-daemon --continue resolveAll assembleDebug assembleDebugAndroidTest testDebugUnitTest > "$work/build.log" 2>&1
status=$?
set -e
tail -n 80 "$work/build.log"
echo "gradle exit: $status"

echo "== mirror"
m2="$work/mirror/m2"; mkdir -p "$m2"
src="$GRADLE_USER_HOME/caches/modules-2/files-2.1"
find "$src" -type f | while read -r f; do
  rel="${f#$src/}"; g="$(echo "$rel" | cut -d/ -f1)"; a="$(echo "$rel" | cut -d/ -f2)"; v="$(echo "$rel" | cut -d/ -f3)"
  d="$m2/$(echo "$g" | tr . /)/$a/$v"; mkdir -p "$d"; cp "$f" "$d/"
done
[ -d "$HOME/.m2/repository" ] && cp -r "$HOME/.m2/repository/." "$m2/"
mkdir -p "$work/mirror/sdk/platforms" "$work/mirror/sdk/build-tools"
cp -r "$ANDROID_HOME/platforms/android-35" "$work/mirror/sdk/platforms/"
cp -r "$ANDROID_HOME/build-tools/34.0.0" "$work/mirror/sdk/build-tools/"
cp "$work/build.log" "$work/mirror/build.log"
du -sh "$work/mirror"
mkdir -p "$out"
tar -C "$work/mirror" -czf "$work/mirror.tgz" .
( cd "$out" && rm -f mirror.tgz.part-* && split -b 90m "$work/mirror.tgz" mirror.tgz.part- && sha256sum "$work/mirror.tgz" | cut -d' ' -f1 > mirror.sha256 )
git -C "$root" rev-parse HEAD > "$out/source-commit"
ls -la "$out"
