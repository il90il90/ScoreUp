# ScoreUp

A sports scoreboard for Android phones, tablets and foldables, with a **Wear OS watch as the scoring remote** and a live **TV scoreboard**.

> Releases are built, tested (JVM + Robolectric) and signed by the GitHub Actions **Release** workflow, and the phone app updates itself from this repo's releases. See [Status & limitations](#status--limitations).

## Features

| Area | What you get |
| --- | --- |
| Scoreboard (phone) | Home/Away panels in team colors, giant animated scores. Tap a team to score, long-press to set any score, **−** to take back that team's last point. Sound, haptics and a winner banner. |
| Points per tap | +1 / +2 / +3 / custom (1–100), shared live with the watch. Each sport has its own defaults (basketball defaults to +2). |
| Sports | Basketball (4×10′ countdown), Soccer (2×45′ count-up), Volleyball (sets to 25 / 15, win by 2, best of 5), Generic. All rules are editable. |
| Game control | Start / pause / reset clock, next period / end set, undo (any device), manual edit, finish / reopen, reset (archives to history), new match. |
| Watch remote | Two huge half-screen buttons on a round screen. Center strip: undo, the game clock (tap to start/pause, long-press to set the game time) over the time of day and live heart rate, and **restart** (asks first, then saves the score and starts over). Long-press a half to undo that team. Swipe for controls (save score, clock, reset clock, next period, heart rate on/off, sync) and for **saved scores**. Distinct haptics per point value. Works offline. |
| Heart rate | While ScoreUp is open the watch reads the wearer's heart rate (it asks for sensor access once; can be turned off in the watch controls). It shows on the watch, on the phone's scoreboard and on the TV, and every score saved on the watch keeps the average and peak. |
| Real-time sync | Event-sourced CRDT over the Wear Data Layer: no lost points, no double counting, offline scoring on both sides, auto-reconnect. See [docs/SYNC.md](docs/SYNC.md). |
| Watch install without Play | The phone app contains the watch APK and installs or updates it over **Wireless debugging**. It pairs with a 6-digit code, finds the watch on the network automatically, streams the install with progress, then launches the app. No computer needed. |
| TV | Arena-style board: each score is sized to fill its team panel, with the period and game clock, the time of day and the watch's heart rate along the top. (1) An external display (HDMI / USB-C / wireless display that offers a second screen) automatically shows it full screen. (2) Cast screen + **TV mode**, which works with any Chromecast or Google TV. (3) A native **Google Cast** custom receiver (`cast-receiver/index.html`); this needs a Cast App ID. |
| Layouts | Portrait (stacked), landscape (side-by-side + center controls), tablets/foldables ≥ 840dp (adds a recent-plays panel). |
| Languages | English and Hebrew, full RTL. Follows the system language or can be set in Settings. |
| History / saved scores | Every reset / restart / new match is saved, on the phone and on the watch, with final score, sets, duration and play log. **Save score** (phone menu, watch controls) saves the current score at any time; saving the same match again updates its entry. Rematch in one tap. |
| Design | Dark arcade theme by default (light/system optional), bold tabular digits, bump/slide score animations. |

## Install

1. On your phone open **[the latest release](https://github.com/il90il90/ScoreUp/releases/latest)**, download `ScoreUp-phone-<version>.apk` and install it (allow "install unknown apps" for your browser).
   After that ScoreUp updates itself: it checks this repo's releases on launch (and via Settings → Check for updates). The phone app carries the matching watch app; when the watch is behind, the scoreboard shows an "Update watch" banner and the watch shows a notice — one tap on the ⌚ screen updates it.
2. Open **ScoreUp → ⌚ → Install on your watch** and follow the 4 steps:
   1. On the watch: Settings → System → About → Versions → tap **Build number** 7×.
   2. Settings → Developer options → enable **ADB debugging** and **Wireless debugging**. Watch and phone must be on the same Wi-Fi.
   3. Tap **Pair new device** on the watch and type its 6-digit code into ScoreUp. The address is detected automatically.
   4. Tap **Install on watch**. Later updates use the same button; no code is needed again.
3. Turn Wireless debugging off afterwards to save battery.

Phone requirements: Android 8.0+ (wireless pairing needs Android 11+). Watch: Wear OS 3+ (API 30+). Sync between phone and watch uses the standard Wear OS pairing (Galaxy Wearable / Pixel Watch / Wear OS app).

### Native Google Cast (optional)
Google Cast only lets registered receivers render custom UI. To enable it:
1. Host `cast-receiver/index.html` on HTTPS (for example GitHub Pages).
2. Register it as a *Custom Receiver* in the [Google Cast SDK Developer Console](https://cast.google.com/publish).
3. Enter the App ID in **ScoreUp → Settings → Cast receiver App ID**, or build with `SCOREUP_CAST_APP_ID`.

Without this, use Cast screen + TV mode, which works with every Chromecast.

## Build

```bash
./gradlew :app:assembleDebug        # phone APK (embeds the matching watch APK)
./gradlew :wear:assembleDebug       # watch APK on its own
./gradlew test                      # all JVM / Robolectric tests
gradle -p scripts/core-only :core:test :adb:test   # engine + ADB tests, no Android SDK needed
```
Requires JDK 17+ and the Android SDK (platform 35, build-tools 34). Phone and watch share `applicationId` **and** signing key, which the Data Layer requires. Builds are signed with `keystore/scoreup-dev.jks` unless `SCOREUP_KEYSTORE_PATH` / `_PASSWORD` / `SCOREUP_KEY_ALIAS` / `SCOREUP_KEY_PASSWORD` are set.

Sandboxed machines without Google Maven can use an offline mirror: `scripts/build-deps-mirror.sh` (run where Google Maven is reachable) and `scripts/fetch-deps-mirror.sh`.

## Architecture

```
core/    pure Kotlin: Model, MatchEvent, MatchReducer (deterministic fold), MatchLog (CRDT),
         ScoreEngine (persist → broadcast), SyncProtocol, History
adb/     pure Kotlin: SPAKE2 (BoringSSL-compatible) pairing, TLS/STLS, ADB transport, streamed install
shared/  Android lib: FileSessionStore (AtomicFile), WearSync (MessageClient + CapabilityClient, heart-rate relay),
         ScoreRuntime, HistoryRepository
app/     phone: Compose UI, history, settings, installer (NSD + :adb), TV (Presentation, Cast sender)
wear/    watch: Wear Compose UI, haptics, heart-rate sensor, saved scores
cast-receiver/  Google Cast web receiver
```

## Testing

| Suite | Where | Status |
| --- | --- | --- |
| Engine: reducer, sports rules, undo, persistence, history, heart-rate stats | `core/src/test` | ✅ passing |
| Sync: duplicates, drops, reordering, offline merge, concurrent undo, new-match handover, 30-seed chaos | `core/src/test/SyncTest` (10) | ✅ passing |
| ADB: RFC 8032 / RFC 5869 vectors, BoringSSL SPAKE2 constants, pairing + TLS install against a fake adbd, wrong code, unpaired rejection, legacy AUTH | `adb/src/test` (16) | ✅ passing |
| Phone UI (Robolectric + Compose): scoring, points/tap, undo, edit, reset→history, save score, setup, clock, winner, TV mode, installer; landscape, tablet, Hebrew RTL; Cast payload; TV board (scores fill the panels, heart rate, time) | `app/src/test` | ✅ passing |
| Watch UI (Robolectric, round screen): big buttons, long-press undo, clock, restart → saved, time + heart rate, save score, saved scores page, 25 rapid taps | `wear/src/test` | ✅ passing |
| Persistence (Robolectric) | `shared/src/test` | ✅ passing |
| Emulator E2E: rotation/recreation persistence, 40 rapid taps, TV mode; Wear emulator scoring | `*/src/androidTest`, `.github/workflows/android-ci.yml` | ⚠️ written, not yet run |

## Status & limitations

- **Heart rate** uses the watch's standard heart-rate sensor (`BODY_SENSORS`). It is covered by JVM/Robolectric tests, not yet by a test on a real watch.
- **Watch installer** is checked against a protocol-faithful fake `adbd` only. It has not been tried on a real watch.
- **Phone⇄watch sync** in a real paired phone + watch setup has not been tested yet. The protocol itself is covered by the JVM tests.
- **Native Cast** needs a registered App ID, which is Google policy for custom receivers.
- The default signing key is a committed development key. Before a public release, add your own key as the `SCOREUP_KEYSTORE_*` secrets. Changing keys later requires reinstalling the app.

---

Made with ❤️ from [Israel](https://t.me/israelcohen)
