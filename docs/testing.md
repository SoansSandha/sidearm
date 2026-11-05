# Testing

How Sidearm is tested, from unit tests to the checks that need a person holding the phone. The design these test is in [plan.md](plan.md) §1–3 and [architecture.md](architecture.md).

## Automated

| Layer | What | Run |
|---|---|---|
| `:core` | Every reducer rule (`ReduceTest` and, for the clean launch mode, `ReduceCleanTest`: one test per row of plan §3.2) and whole press sequences on a fake clock, against a model of stock's tap counting (`ReduceTimingTest`), and with SystemUI's camera start 60 or 400 ms after press 2 in both launch modes (`ReduceCleanTimingTest`) | `./gradlew :core:test` |
| `:hook` | Resolvers against fixture classes that copy the framework's members (they find every target or refuse cleanly), the hookers against a fake `Chain` (stock runs exactly once; failures return stock's result; the activity-start hooker passes everything through untouched while not watching), the sequencer's dry-run latch, clean watch and logging, the held start's replay as its caller, the config reader, the unlock callback | `./gradlew :hook:testDebugUnitTest` |
| `:app` | The status card's priority order (`HeroTest`), the screen's ViewModel against fakes of Vector, the package manager and Settings (connect timeout, a binder that dies, writes that fail or race, a failing package manager), the double-press rule taken from `GestureLauncherService`, the provider's caller check, app search | `./gradlew :app:testDebugUnitTest` |
| Everything | Formatting, lint, all unit tests, the coverage gate on `:core` | `./gradlew check` |

## Device smoke test

`bash scripts/device-smoke.sh` injects a single press, a double, a triple, and a double whose third press comes 450 ms late, then checks the module's log for each.
- The phone needs a debug build with the feature armed (enabled with a target). A dry run is fine, and leaves the presses' effects to stock.
- It never injects more than 3 presses in a row, and waits between sequences. **Never script 4 or 5 presses**: 5 fast presses start Emergency SOS when it's on.
- Injected presses show up as `device=-1`; they go through the same `GestureLauncherService` path as the button.

## Device matrix (manual)

Fill it in on each release and after each OTA. ✅ passed, ⏳ not yet tested, n/a doesn't apply. "inj." means injected presses, "real" the physical button. Results are from CP3A.261005.005.

| Starting state ↓ / sequence → | 1 | 2 | 3 | slow 3rd (>300 ms) | long press | 5 (SOS) |
|---|---|---|---|---|---|---|
| Screen off, locked | ✅ inj.: stock | ✅ inj.: secure camera after 350 ms | ✅ real: fingerprint overlay after ~0.4 s, app opens once unlocked | ✅ inj. (dry run): camera, press left to stock | ⏳ | ✅ real: countdown starts, nothing covers it |
| Screen off, locked, face unlock | ⏳ | ⏳ | ✅ real: app opens | ⏳ | ⏳ | ⏳ manual |
| Screen on, locked | ⏳ | ⏳ | ✅ real: fingerprint overlay, then app | ⏳ | ⏳ | ⏳ manual |
| Screen on, unlocked | ✅ inj.: stock | ✅ inj.: normal camera at once, stays unlocked | ✅ inj.: app opens unlocked after a ~0.1 s camera flash | ✅ inj. (dry run) | ⏳ | ✅ real: countdown starts, nothing covers it |
| Screen on, unlocked, animations off (screen fully off before press 2) | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ manual |
| Target app already open, unlocked | ⏳ | ⏳ | ✅ real: app comes back as it was | ⏳ | ⏳ | ⏳ manual |
| Camera already open, unlocked | n/a | n/a | ✅ inj.: app opens over the camera | n/a | n/a | ⏳ manual |
| During a phone call | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ manual |
| **Clean launch mode**, screen off, locked | ✅ real: stock | ✅ real: secure camera after 350 ms, as classic | ✅ real: unlock prompt, app opens once unlocked | ⏳ | n/a | ⏳ manual |
| **Clean**, screen on, unlocked | ✅ real: stock | ✅ real: normal camera ~350 ms later, stays unlocked | ✅ real: app opens with no camera; no camera task in recents | ⏳ | n/a | ✅ real: app opens at press 3, the countdown shows over it (as classic) |
| **Clean**, unlocked, soon after a reboot (slow SystemUI) | n/a | ⏳ camera opens | ⏳ app opens and stays on top | n/a | n/a | n/a |

Clean, 2026-11-03, on CP3A.261005.005 with Vector 2.2 and a debug build: the hook logged `clean=available`. In a dry run first, it caught SystemUI's camera start 49–57 ms after press 2 in every unlocked triple, and press 3 came 137–182 ms after press 2. Live, the start came 38–59 ms after press 2; doubles replayed it at the end of the window with no error, and triples dropped it and opened the app directly. In one of four dry-run unlocked triples Android itself took the locked path (the keyguard was showing by press 3, and SystemUI started the secure camera, which the hook doesn't match); that's stock behaviour, and clean then behaves like classic: the unlock prompt, then the app.

The clean rows need `launch_mode` `clean` (Settings, or `--es launch_mode clean` on a debug build). Run them first as a dry run: the hook then logs `CameraStartRequested(time=…)` and what it would do, which shows how long SystemUI takes after press 2, while stock handles every press. If the hook logs `clean=unavailable` at boot, the clean mode reads as classic on that build, though the app still says "Clean launch".

Also checked on 2026-11-01: a long press still opens the power menu (real), and `scripts/device-smoke.sh` passes. The Phase 2 dry run with real presses: about 30 sequences of 1, 2 and 3 presses, every one classified exactly as stock counted it.

Known issue (2026-11-01, real presses, in use after a reboot): an unlocked triple sometimes opens the chosen app and then the camera comes up over it. Not yet traced in the log. The likely cause is a race: press 2's camera launch goes through SystemUI, which can start the camera after press 3 has already opened the app. Left as is for now. Phase 6's clean launch avoids it, because the camera activity never starts (plan §3.7, §7).

## Config app (manual, every release)

Run with `adb logcat -s Sidearm` open, since each config change the hook takes is logged there. Results are from CP3A.261005.005 with Vector 2.2, on 2026-11-01 (✅ passed, ⏳ not yet tested).

| Check | Expected | Result |
|---|---|---|
| Open the app with everything in place | Status card "Active", every row in Checks ticked, the chosen app named in "Opens" and in the press map | ✅ (dark theme, wallpaper colours) |
| `XposedProvider` log on opening | `binder received`; no "Ignored a call from uid" line from `Sidearm` | ✅ Vector calls as uid 1000 |
| Turn **Triple press** off, then on | The hook logs each change at once, with no reboot; a triple press does nothing new while off | ✅ both logged live |
| Choose another app in the picker, then triple-press | The hook logs the new target, and the new app opens | ✅ real presses, unlocked |
| Search the picker by name and by package | Only matching apps are listed | ✅ both |
| Set double-press to Wallet in Settings, come back | Status card "Paused"; "Open gesture settings" opens the double-press page | ✅ |
| Uninstall or disable the chosen app | Status card "App not found", with "Choose app" | ✅ |
| Disable Sidearm in the Vector manager, reopen the app | "Not connected" after about 3 seconds | ✅ "Vector hasn't connected"; re-enabling asks for a reboot, and after it everything works |
| `adb shell dumpsys package io.github.soanssandha.sidearm` | One activity and Sidearm's provider (plus AndroidX's non-exported startup provider); no services or receivers | ✅ |
| Rotate the screen, switch dark theme on and off | State is kept and nothing reloads visibly | ✅ auto-rotate on and off, dark theme on and off |
| ⋮ › Settings, choose Clean, then Classic | The hook logs each change at once (`launchMode=CLEAN`, then `CLASSIC`); the status card's footer says "Clean launch" only while Clean is chosen; back returns to the main screen where it was | ✅ 2026-11-03: the footer follows each switch; then a day of daily use in Clean with no problems |
| Settings with Vector disconnected | Both choices greyed out, with the note that Vector isn't connected | ⏳ |

## Release builds

Each release is checked on the phone with its signed APK, from a Release run with "publish" off (CONTRIBUTING.md, Release), before it's published. Moving from a debug build means uninstalling it first, so the config and Vector's switch for Sidearm start over.

| Release | Build, Vector | Checked | Result |
|---|---|---|---|
| v0.5.0 (2026-11-03) | CP3A.261005.005, Vector 2.2 | Install over adb after uninstalling the debug build; enable in Vector, restart; set up in the app; real triples; Emergency SOS check | ✅ Triples work. The hook's wake on a triple from screen off shows in Android's own log (below). SOS countdown starts and cancels. The published APK is byte-identical to the one tested (SHA-256 `e7430d5c…5712f`), because the release build is reproducible. |
| v0.6.0 (2026-11-04) | CP3A.261005.005, Vector 2.2 | The same install and setup, in the Clean mode: unlocked triples and doubles, a locked double and triple, Emergency SOS | ✅ Unlocked triples open the app with no camera (SystemUI's start held 54–84 ms after press 2); unlocked doubles open the camera at the end of the window; locked sequences as before; in SOS, presses 4 and 5 reach stock. Signed with the same certificate as v0.5.0, so v0.5.0 updates in place. |

If `adb logcat -s Sidearm` shows nothing at all, Vector may be muting module logs: it can drop everything below error level, which leaves Sidearm's info and warning lines out. Turn its logging back on in the Vector manager. Android's own log still shows a triple from screen off, whatever Vector's setting: `PowerManagerService: Waking up from Dozing (… details=sidearm:TRIPLE_PRESS)`.

## Emergency SOS check (manual, every release and OTA)

1. Settings › Safety & emergency › Emergency SOS: make sure it's on, with the countdown.
2. Lock the phone and leave the screen off.
3. Press the power button 5 times quickly. The countdown must start.
4. Cancel it during the countdown.
5. Watch whether the chosen app, the camera or the unlock prompt ever covers the countdown. On a locked phone the unlock prompt waits 400 ms after press 3 and is called off by press 4, so it shouldn't appear (ADR 0008). On an unlocked phone the app opens at press 3, before presses 4 and 5; if it covers the countdown, build the SOS guard (ADR 0004).
6. Repeat once with the phone unlocked.

## After every monthly OTA

1. Re-root, and check that Vector and Zygisk are running.
2. `adb logcat -s Sidearm` right after boot: the module logs "Active on …". If it logs "Not installed", a target changed: check the new build's jars as `docs/hook-targets.md` describes, and fix the targets before anything else. If it logs nothing at all, check Vector's logging setting first (Release builds, above).
3. Add the new build fingerprint to `docs/hook-targets.md`, with every target checked, and to `TestedBuilds` in `app/src/main/kotlin/.../data/TestedBuilds.kt`, so the config app stops warning about it.
4. Run `bash scripts/device-smoke.sh` (dry run is fine).
5. Do the Emergency SOS check above.
6. Fill in the matrix for the new build.
