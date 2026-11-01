# Testing

How Sidearm is tested, from unit tests to the checks that need a person holding the phone. The design these test is in [plan.md](plan.md) §1–3 and [architecture.md](architecture.md).

## Automated

| Layer | What | Run |
|---|---|---|
| `:core` | Every reducer rule (`ReduceTest`, one test per row of plan §3.2) and whole press sequences on a fake clock, against a model of stock's tap counting (`ReduceTimingTest`) | `./gradlew :core:test` |
| `:hook` | Resolvers against fixture classes that copy the framework's members (they find every target or refuse cleanly), the hookers against a fake `Chain` (stock runs exactly once; failures return stock's result), the sequencer's dry-run latch and logging, the config reader, the unlock callback | `./gradlew :hook:testDebugUnitTest` |
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

Also checked on 2026-11-01: a long press still opens the power menu (real), and `scripts/device-smoke.sh` passes. The Phase 2 dry run with real presses: about 30 sequences of 1, 2 and 3 presses, every one classified exactly as stock counted it.

## Config app (manual, every release)

Run with `adb logcat -s Sidearm` open, since each config change the hook takes is logged there. Results are from CP3A.261005.005 with Vector 2.2, on 2026-11-01 (✅ passed, ⏳ not yet tested).

| Check | Expected | Result |
|---|---|---|
| Open the app with everything in place | Status card "Active", every row in Checks ticked, the chosen app named in "Opens" and in the press map | ✅ (dark theme, wallpaper colours) |
| `XposedProvider` log on opening | `binder received`; no "Ignored a call from uid" line from `Sidearm` | ✅ Vector calls as uid 1000 |
| Turn **Triple press** off, then on | The hook logs each change at once, with no reboot; a triple press does nothing new while off | ✅ both logged live |
| Choose another app in the picker, then triple-press | The hook logs the new target, and the new app opens | ✅ real presses, unlocked |
| Search the picker by name and by package | Only matching apps are listed | ⏳ |
| Set double-press to Wallet in Settings, come back | Status card "Paused"; "Open gesture settings" opens the double-press page | ⏳ |
| Uninstall or disable the chosen app | Status card "App not found", with "Choose app" | ⏳ |
| Disable Sidearm in the Vector manager, reopen the app | "Not connected" after about 3 seconds | ⏳ |
| `adb shell dumpsys package io.github.soanssandha.sidearm` | One activity and Sidearm's provider (plus AndroidX's non-exported startup provider); no services or receivers | ✅ |
| Rotate the screen, switch dark theme on and off | State is kept and nothing reloads visibly | ⏳ |

## Emergency SOS check (manual, every release and OTA)

1. Settings › Safety & emergency › Emergency SOS: make sure it's on, with the countdown.
2. Lock the phone and leave the screen off.
3. Press the power button 5 times quickly. The countdown must start.
4. Cancel it during the countdown.
5. Watch whether the chosen app, the camera or the unlock prompt ever covers the countdown. On a locked phone the unlock prompt waits 400 ms after press 3 and is called off by press 4, so it shouldn't appear (ADR 0008). On an unlocked phone the app opens at press 3, before presses 4 and 5; if it covers the countdown, build the SOS guard (ADR 0004).
6. Repeat once with the phone unlocked.

## After every monthly OTA

1. Re-root, and check that Vector and Zygisk are running.
2. `adb logcat -s Sidearm` right after boot: the module logs "Active on …". If it logs "Not installed", a target changed: check the new build's jars as `docs/hook-targets.md` describes, and fix the targets before anything else.
3. Add the new build fingerprint to `docs/hook-targets.md`, with every target checked, and to `TestedBuilds` in `app/src/main/kotlin/.../data/TestedBuilds.kt`, so the config app stops warning about it.
4. Run `bash scripts/device-smoke.sh` (dry run is fine).
5. Do the Emergency SOS check above.
6. Fill in the matrix for the new build.
