# Architecture

How Sidearm works as built. The design reasoning is in [plan.md](plan.md) §3 and the ADRs in [decisions/](decisions/). The framework members it relies on, per Android build, are in [hook-targets.md](hook-targets.md).

## Processes

```
 app process (io.github.soanssandha.sidearm)        Vector daemon            system_server
 ┌──────────────────────────────────────┐   binder  ┌──────────────┐  push   ┌───────────────────────────────┐
 │ RemoteConfigStore ── XposedService ──┼──────────►│ remote prefs │────────►│ RemoteConfigReader            │
 │ (DevConfigActivity in debug builds)  │           │  "settings"  │         │   │ Config                    │
 └──────────────────────────────────────┘           └──────────────┘         │   ▼                           │
                                                                             │ Sequencer ◄── hookers ◄── GestureLauncherService
                                                                             │   │ effects                   │
                                                                             │   ▼                           │
                                                                             │ SystemEffectRunner ──► PhoneWindowManager
                                                                             └───────────────────────────────┘
```

- **system_server** runs the hook (`:hook`). Vector loads it from the APK at boot, with scope `system`.
- **The app** (`:app`) writes the config through Vector's service. It runs only while the user has it open. Until Phase 4 the only writer is a debug-build activity driven from adb.
- **Vector** stores the config as remote preferences in its own database, and pushes each change into system_server.

Nothing runs in the background: no service, receiver, job or wakelock. The only thread Sidearm starts reads the config once after boot and then ends (see below).

## Modules

| Module | Role | Key types |
|---|---|---|
| `:core` | Pure Kotlin decisions. Fully unit-tested. | `reduce()` and its `State`/`Event`/`Effect`; `Config`, `ConfigKeys`, `ConfigCodec` |
| `:hook` | Glue that runs in system_server. No libraries beyond `:core`. | See packages below |
| `:app` | The APK: `META-INF/xposed/*`, the config writer, and the debug dev tool | `data.RemoteConfigStore`, `DevConfigActivity` (debug only) |

`:hook` packages:

| Package | Contents |
|---|---|
| `hook` | `SidearmModule`: resolves targets, installs the hooks, wires one `Runtime` per generation, handles hot reload |
| `hook.target` | `GestureLauncherTargets`, `PhoneWindowManagerTargets`: reflection handles resolved once at boot, all-or-nothing |
| `hook.intercept` | `PowerKeyDownHooker`, `CameraGestureHooker`: turn hooked calls into sequencer events; `SequenceStart`: whether the phone was locked at press 1 |
| `hook.effect` | `Sequencer` (state, lock, dry-run latch), `SystemEffectRunner`, `HeldCamera`, `TargetLauncher`, `HookLog` |
| `hook.config` | `RemoteConfigReader`: follows the remote prefs and decodes them through `:core` |

## Boot and install

1. Vector calls `SidearmModule.onSystemServerStarting` just before `SystemServer.startBootstrapServices`.
2. Both `*Targets` classes resolve every method and field they need. If anything is missing or changed type, nothing is hooked, one warning is logged, and the power button stays stock.
3. The module builds a `Runtime`: a `Handler` on system_server's main looper, an `ORIGIN` invoker for `handleCameraGesture`, the effect runner, the sequencer and the config reader.
4. It hooks `interceptPowerKeyDown` and `handleCameraGesture` with `ExceptionMode.PROTECTIVE`, then deoptimizes `interceptPowerKeyDown`, so ART can't have inlined the camera call past the hook.
5. Reading the config is posted to the main looper, whose queue only starts running once boot has finished. The posted message starts a short-lived thread, "Sidearm config". Opening Vector's preferences is a blocking binder call, so it never runs on the input thread or on a looper the system_server watchdog monitors. The thread retries up to five times, 5 s apart, if Vector isn't ready, then ends. Until the config arrives, the feature is off.

## A press, thread by thread

All times are the key events' uptime clock.

1. **Input thread.** PhoneWindowManager calls `interceptPowerKeyDown`. `PowerKeyDownHooker` lets stock run exactly once, then checks `mLastPowerDown == eventTime` to see whether stock counted the press. Long presses and presses in the SOS cooldown aren't counted, and they pass through untouched. When stock counts a first press, `SequenceStart` records whether the keyguard is showing. That's a cached flag, read before stock starts turning the screen off (ADR 0007).
2. On press 2, stock calls `handleCameraGesture(false, POWER_DOUBLE_TAP)` from inside step 1. `CameraGestureHooker` reads press 2's time from `mLastPowerDown` and hands a `CameraRequested`, with press 1's lock state, to the sequencer.
   - **Locked at press 1:** when armed, it returns `true` without running the original, exactly what stock returns after a launch. Steps 3–6 follow.
   - **Unlocked at press 1:** the original runs at once and the camera opens as in stock. Holding would make SystemUI lock the phone. If stock then counts press 3 within the window, `interceptPowerKeyDown` returns `true` and `LaunchTarget` (with no camera fallback) opens the app over the camera, as in step 6.
3. The sequencer runs `reduce()` under its lock and releases the lock before running effects. `ScheduleTimer` posts to the main looper at `press 2 + W + G` (300 + 50 ms).
4. **Main looper.** If the timer fires first, the held call is replayed through the `ORIGIN` invoker (skipping every hook, ours included), and the camera opens. A press 3 that stock still counts within the window then opens the app over it.
5. **Input thread.** If stock counts press 3 first, the sequencer cancels the timer, `interceptPowerKeyDown` returns `true` (so the screen doesn't toggle), and `LaunchTarget` is posted to the main looper.
6. **Main looper.** `TargetLauncher` gets PhoneWindowManager from `LocalServices`. It opens the app only if the keyguard isn't showing and the target is one of its package's enabled, exported `MAIN`/`LAUNCHER` activities. That check matters because system_server could otherwise start any activity, even an unexported one. It starts the app through PWM's own `startActivityAsUser(intent, CURRENT_OR_SELF)`, with `NEW_TASK | RESET_TASK_IF_NEEDED`. Otherwise it replays the held camera if there is one, so press 3 still does something.

The launcher check queries system_server's own user (user 0), and the launch goes to the current user, as PhoneWindowManager's own launches do. On a phone with one user those are the same; more than one user is out of scope for v1.

Opening the app while the phone is locked (`dismissKeyguardToLaunch`), and waking it for a press 3 that came while it wasn't interactive, arrive in Phase 3. Until then those cases open the camera, and `WakeDevice` only logs.

## Threads and locks

| Thread | Enters through | Holds |
|---|---|---|
| Input (InputReader) | both hookers | the sequencer lock, briefly |
| system_server main looper | timers, launches, config changes | the sequencer lock, briefly |
| Binder (Vector's callback) | `RemoteConfigReader` listener | Vector's listener lock → the reader's lock; it only posts the new config to the main looper |
| "Sidearm config" (short-lived) | the first config read | the reader's lock, briefly |
| Hot reload | `onHotReloading` | the reader's lock, then the sequencer lock, never both |

- The sequencer's lock only ever covers `reduce()`. Effects, `proceed()` and the original method always run outside it.
- `RemoteConfigReader` never registers or unregisters its listener while holding its own lock, because Vector calls the listener while holding Vector's. It does read the values under its own lock, which can't deadlock, because Vector's `getAll()` takes no lock.
- A timer that loses a race carries an old generation number, so the reducer ignores it.
- Every runnable Sidearm posts catches what it throws, and so does the config thread. An uncaught exception on any system_server thread restarts system_server.

## Config and dry run

| Key (group `settings`) | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | Boolean | false | Feature on |
| `target_component` | String | none | `ComponentName.flattenToString()` of the app to open |
| `dry_run` | Boolean | false | Run the sequencer and log what it would do, but leave every press to stock |

- Anything missing or malformed decodes to "off" (`ConfigCodec`). Vector calls the listener once per changed key, and only real changes reach the sequencer.
- During a dry run, stock opens the camera on press 2 as usual. Timers still run, so the sequencer's state tracks a real sequence, and press 3 only logs whether the target would open.
- The mode is latched when a sequence's camera request arrives. A hold that began as a dry run never replays the camera, because stock has already opened it. A live hold always runs its camera or launch, even if dry run is switched on mid-hold. See [ADR 0006](decisions/0006-dry-run-latched-per-hold.md).

## Logging

`adb logcat -s Sidearm` shows:
- one line at install: active, or why not
- each config change
- while the feature is armed (dry run or live), every press stock counted and every sequencer decision
- launches, fallbacks and failures

While the feature is off, presses and stock's own camera launches aren't logged, so the log doesn't record button use. Log lines can include the chosen app's component name. Only adb and privileged apps can read system_server's log, but a bug report includes it.

## Hot reload

`scripts/dev-install.sh` installs with a fresh versionCode, and Vector hot-reloads the module in system_server:
1. **Old generation**, in `onHotReloading`: stops following the config (unregisters the listener), retires the sequencer (runs any held camera, then ignores every later call), and drops its pending timer. A launch that's already queued still runs, so a consumed press 3 still opens something. Then it hands over the system_server class loader.
2. **New generation**, in `onHotReloaded`: resolves targets again and swaps its hookers into the old hook handles by id, then starts with fresh state.

If the new generation fails to install, it unhooks everything, so the button is stock until the next install.

## Developing against a phone

```bash
bash scripts/dev-install.sh               # build, install, hot reload
adb logcat -s Sidearm SidearmDev          # hook lines, dev-tool lines

# Debug builds only: set the config (extras are the config keys; unset ones keep their value).
adb shell am start -n io.github.soanssandha.sidearm/.DevConfigActivity \
    --ez enabled true --es target_component <package>/<activity> --ez dry_run true
adb shell am start -n io.github.soanssandha.sidearm/.DevConfigActivity    # report the stored config
adb shell am start -n io.github.soanssandha.sidearm/.DevConfigActivity --ez dry_run false
```

- `DevConfigActivity` exists only in debug builds. It's exported but needs `android.permission.DUMP`, which the adb shell holds and third-party apps can't get. It's invisible, logs its result, shows a short toast and finishes, so every `am start` gets a fresh instance with its own extras.
- Find a launcher activity with `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <package>`.
- Inject presses with `adb shell input keyevent --delay 150 KEYCODE_POWER KEYCODE_POWER KEYCODE_POWER`. **Never inject 4 or more**, because 5 triggers Emergency SOS when it's on.
