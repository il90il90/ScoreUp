#!/usr/bin/env bash
# Publishes built APKs to the PUBLIC releases repo (APKs + latest.json only, never source):
#   ScoreUp.apk            – phone app (contains the matching watch app)  ← the one link users need
#   ScoreUp-watch.apk      – standalone watch APK (for manual adb installs)
#   apk/<version>/...      – versioned copies
#   latest.json            – read by the in-app updater
# Usage: scripts/publish-release.sh <releases-repo-checkout> <phone.apk> <watch.apk> <versionName> <versionCode> [notes]
set -euo pipefail
repo="$1"; phone="$2"; watch="$3"; name="$4"; code="$5"; notes="${6:-}"
slug="il90il90/scoreup-releases"
cd "$repo"
mkdir -p "apk/$name"
cp "$phone" ScoreUp.apk; cp "$watch" ScoreUp-watch.apk
cp "$phone" "apk/$name/ScoreUp-phone-$name.apk"; cp "$watch" "apk/$name/ScoreUp-watch-$name.apk"
sha256sum ScoreUp.apk ScoreUp-watch.apk > SHA256SUMS.txt
cat > latest.json <<JSON
{"versionCode": $code, "versionName": "$name",
 "phoneApkUrl": "https://github.com/$slug/raw/main/apk/$name/ScoreUp-phone-$name.apk",
 "watchApkUrl": "https://github.com/$slug/raw/main/apk/$name/ScoreUp-watch-$name.apk"}
JSON
python3 - "$name" "$notes" <<'PY'
import sys
name, notes = sys.argv[1], sys.argv[2]
open("README.md", "w").write(f"""# ScoreUp — downloads

## ⬇️ [Download ScoreUp for Android (v{name})](https://github.com/il90il90/scoreup-releases/raw/main/ScoreUp.apk)

Open the link on your phone, tap the downloaded file and allow "Install unknown apps" if asked.

**Watch:** open ScoreUp on the phone → tap ⌚ → follow the 4 steps. The phone installs the watch app
for you over Wi‑Fi — no computer, no Play Store.

Updates: ScoreUp checks this page by itself and offers new versions in-app.

<details><summary>Release notes</summary>

{notes}
</details>

Checksums: [SHA256SUMS.txt](SHA256SUMS.txt) · Older versions: [apk/](apk/)
""")
PY
git add -A
git commit -qm "ScoreUp $name"
echo "Committed ScoreUp $name in $repo — push it to publish."
