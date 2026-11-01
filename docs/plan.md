# Implementation plan: Sidearm (triple-press launcher)

App name **Sidearm**, applicationId `io.github.soanssandha.sidearm`. Written 2026-10-29. The ECC `architect` agent reviewed it against the Android 17 source the same day; review fixes are included. Phase 0 finished the same day, and its findings are folded in below (see also `docs/hook-targets.md`). Phase 1 finished 2026-10-30. Phase 2's code was written and reviewed on 2026-10-31; its on-device checks are still to do. How the built system works is in `docs/architecture.md`.

## 1. Behaviour contract

| Presses | Result | Changed? |
|---|---|---|
| 1 | Screen on/off | No |
| 2 | Camera. From an unlocked phone it opens at once, as in stock; from a locked one, about 350 ms after press 2 | Timing only, and only when locked |
| 3 | Chosen app. From an unlocked phone the camera shows for about a tenth of a second first. If the phone was locked, the unlock prompt shows first (Phase 3). | New |
| 4 | Same as stock: nothing if Emergency SOS is on, a screen toggle if it's off | No |
| 5 | Emergency SOS | No |
| Long press | Assistant or power menu | No |

Rhythm: each press must land within 300 ms of the previous one, which is Android's own `POWER_DOUBLE_TAP_MAX_TIME_MS`. Slower presses count as new single presses, exactly as in stock. The triple only works while the stock double-press is set to Camera. If double-press is off or set to Wallet, the module does nothing, and the config app says so.

```
press:             1 ──<300ms── 2 ──<300ms── 3
stock:             screen       camera       (screen toggle, or swallowed if SOS is on)
module, locked:    screen       hold camera  cancel camera → open chosen app (unlock prompt first)
                                └─ 350 ms (300 + 50 grace) with no press 3 → open camera
module, unlocked:  screen       camera       open chosen app over the camera
```

Why it depends on the lock (ADR 0007): holding the camera on an unlocked phone makes SystemUI lock it, because SystemUI only skips the lock after press 1's screen-off if it sees the camera request before the screen wakes. "Locked" means the keyguard was showing at press 1.

The module adds no background service, receiver, job or wakelock. The hook lives in `system_server`, which is always running anyway. The only extra work is one delayed message after a double-press.

## 2. Facts this plan rests on

| Fact | Source | Confidence |
|---|---|---|
| Vector 2.2 (4 Aug 2026) implements **libxposed API 102** and supports Android 17 (added in 2.1). It still runs legacy-API modules too. | [Vector releases](https://github.com/JingMatrix/Vector/releases) | Verified (release notes) |
| API 102 hooks look like `hook(executable).intercept { chain -> … }`. Return without calling `chain.proceed()` to skip the original. Run the original later with `getInvoker(m).setType(Invoker.Type.ORIGIN).invoke(…)`. `deoptimize(executable)` stops a method from running inlined copies of its callees. | [libxposed/api](https://github.com/libxposed/api), [libxposed/example](https://github.com/libxposed/example) | Verified (source) |
| The system_server entry callback is `onSystemServerStarting(SystemServerStartingParam)`, which provides `getClassLoader()`. | libxposed/api `XposedModuleInterface.java` | Verified (source) |
| Module metadata lives in `META-INF/xposed/{java_init.list,module.prop,scope.list}`. The example `module.prop` is `minApiVersion=101`, `targetApiVersion=102`, `staticScope=true`, `autoHotReload=true`. | libxposed/example | Verified |
| The config app reaches Vector through `XposedServiceHelper.registerListener` → `XposedService.getRemotePreferences(group)`. Those prefs are read-only in the hooked process. | libxposed/service | Verified (source) |
| On Maven Central: `io.github.libxposed:api:102.0.0` and `service:102.0.0`. The service README mentions 102.0.1, but that version isn't published. Vector mirrored libxposed on 1 Oct after "upstream was suspended". | Maven Central metadata, Vector canary-3112 notes | Verified |
| The `scope.list` name for system_server is **`system`**. Vector maps that row to `system_server` (uid 1000). | Vector `daemon/.../ConfigCache.kt` | Verified (source + on device) |
| With `ExceptionMode.PROTECTIVE` (the default), a hooker that throws *after* `proceed()` gets the already-proceeded result returned. The original is never re-run. | libxposed `XposedInterface.java` | Verified (source) |
| On the QPR1 build CP3A.261005.005, `services.jar` matches `android17-release` for every target, and the `handleCameraGesture` call site is present (not inlined). | dexdump of that build's framework jars, `docs/hook-targets.md` | Verified (device) |
| Vector hot-reloads a module in system_server only when its **versionCode changes**. | Vector `FrameworkService.staleHotReloadTargets`, on device | Verified (device) |
| In Android 17, `GestureLauncherService.interceptPowerKeyDown` counts taps in `mPowerButtonConsecutiveTaps`. Press 2 calls `handleCameraGesture(false, CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP)`; press 5 calls `handleEmergencyGesture()`. The sensor gestures call `handleCameraGesture(true, <other source>)`. | `android17-release` frameworks/base, GLS L647–785, L850, L988, L1073 | Verified (source) |
| PhoneWindowManager treats the press as consumed (no screen toggle, no wake) when GestureLauncherService returns `true` **or** sets `outLaunched`. When `outLaunched` is set, PhoneWindowManager also wakes early if the screen is going to sleep, and tells the keyguard that a launch gesture fired, which stops it from locking. | PWM L4917–4952, L1081–1087, L5409, L5454 | Verified (source) |
| PhoneWindowManager already has a "launch, or unlock then launch" path: `mKeyguardDelegate.isShowing() ? mKeyguardDelegate.dismissKeyguardToLaunch(intent) : startActivityAsUser(…)`. | PWM L1436–1446 | Verified (source) |
| **Correction (2026-11-01):** on Android 17, SystemUI ignores `dismissKeyguardToLaunch`: `KeyguardService.dismissKeyguardToLaunch` only logs "Ignoring dismissKeyguardToLaunch". The unlock route that still works is `PhoneWindowManager.dismissKeyguardLw(IKeyguardDismissCallback, CharSequence)` → `KeyguardViewMediator.dismiss` → `StatusBarKeyguardViewManager.dismissAndCollapse()` → `ActivityStarter.executeRunnableDismissingKeyguard`. That's the flow the Wallet tile uses, and it shows the fingerprint overlay (alternate bouncer) when fingerprint is enrolled. | SystemUI `KeyguardService` L698, `KeyguardViewMediator` L2694, `StatusBarKeyguardViewManager`; on CP3A, `dismissKeyguardLw` and `IKeyguardDismissCallback$Stub` (`DESCRIPTOR`, `TRANSACTION_on*`, `asInterface`) dexdump-verified | Verified (source + device jars) |
| Starting in 2026, Google publishes AOSP source only in Q2 and Q4. Android 17 QPR1 builds such as CP3A.261005.005 can differ from `android17-release`. **The `services.jar` of the target build is the source of truth.** | [Android Authority](https://androidauthority.com/aosp-source-code-schedule-3630018/) | Verified |

## 3. Design

### 3.1 Where the hook goes

There are two hooks, both in `com.android.server.GestureLauncherService`. Nothing is hooked in SystemUI, so the module needs only one scope and runs in one process.

| Method (Android 17) | What our interceptor does |
|---|---|
| `boolean interceptPowerKeyDown(KeyEvent, boolean interactive, MutableBoolean outLaunched)` | 1. Calls `chain.proceed()` **exactly once**, outside any try block, and keeps the result. (The camera hook learns press 2's time from `mLastPowerDown`, which stock sets before it asks for the camera.) 2. Runs our own logic inside `try/catch`, and any failure returns the kept result. 3. If stock actually counted this press (checked with `mLastPowerDown == event.eventTime`), it passes the press and `mPowerButtonConsecutiveTaps` to the state machine. 4. On a recognised third press it returns `true`, so PhoneWindowManager consumes the press and the screen doesn't toggle. It leaves `outLaunched` alone, so the keyguard isn't told a launch gesture fired. If `!interactive`, it also wakes the device with `WAKE_REASON_POWER_BUTTON`. Don't use `WAKE_REASON_GESTURE`, because that blocks the next power press from turning the screen off (PWM L1366–1376). |
| `boolean handleCameraGesture(boolean useWakelock, int source)` | Only acts when `source == CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP`; other sources, such as camera-lift, pass straight through. If the feature is armed and the phone was locked at press 1, it skips the original, schedules it for later, and returns `true`. PhoneWindowManager then sees exactly what it sees in stock: "launched", so it wakes early. On an unlocked phone it runs the original at once (ADR 0007). |

Why a held press 2 still reports "launched": PhoneWindowManager uses that report to wake the screen early. On a locked phone the keyguard ends up the same either way, so the only thing that moves is when SystemUI is told to open the camera. On an unlocked phone that's not enough, and holding would lock it (ADR 0007), so the camera isn't held there.

"Locked at press 1": the power hook records `KeyguardServiceDelegate.isShowing()` (a cached flag) whenever stock counts a first press. By press 2, press 1's screen-off already makes the keyguard report itself showing, even on a phone that was unlocked.

Why press 3 has to return `true`: with Emergency SOS off, stock returns `false` on press 3, and PhoneWindowManager would treat it as a normal press and turn the screen off.

**Never re-run the original `interceptPowerKeyDown`.** It updates tap counts every time it runs. A second run sees a 0 ms interval and counts a phantom tap, which means camera on press 1 and **SOS one press early**. That rules out a catch block that calls `proceed()`. `ExceptionMode.PROTECTIVE` is safe: after `proceed()` it returns the already-computed result and never re-runs the original. Register every hook with it explicitly.

Inlining: ART may have compiled `handleCameraGesture` inline into `interceptPowerKeyDown`, in which case our hook would never fire. Call `deoptimize(interceptPowerKeyDown)` after hooking. In Phase 0, confirm in jadx that the call site still exists in the QPR1 `services.jar`, not just the method.

### 3.2 State machine (pure Kotlin, in `:core`)

All decisions live in a pure reducer, `reduce(state, event, config, timing) -> Transition(state, effects)` in `:core` (`core.sequencer`), which uses no Android or Xposed types. `config` is the current settings snapshot and `timing` holds `W` and `G`. The hook layer only turns calls into events and runs the effects. `ReduceTest` has one test per row below; `ReduceTimingTest` plays whole press sequences on a fake clock, with a fake of stock's tap counting.

States: `Idle(lastCameraPress)`, `HoldingCamera(camera, pressTime, deadline, generation)`. "Armed" means enabled with a valid target. `CameraRequested` carries `startedLocked`, whether the keyguard was showing at press 1.

| State | Event | Condition | Next | Effects |
|---|---|---|---|---|
| Idle | CameraRequested(t2) | armed, started locked | HoldingCamera | Schedule(t2 + W + G); tell stock "launched" |
| Idle | CameraRequested(t2) | armed, started unlocked | Idle(lastCameraPress = t2) | Run stock now (ADR 0007) |
| Idle | CameraRequested | not armed | Idle | Run stock now |
| Idle | PowerDown(taps = 3) | less than W after `lastCameraPress`, armed | Idle | ConsumePress; WakeDevice if not interactive; LaunchTarget with no fallback. The camera is already opening (the phone was unlocked, or the timer won the race), so the target opens over it. |
| Idle | PowerDown(taps = 3) | less than W after `lastCameraPress`, not armed | Idle | ConsumePress only: the camera is opening (e.g. the feature was turned off mid-hold), so this just stops a screen toggle |
| Idle | anything else | | Idle | none: stock handles the press |
| HoldingCamera | PowerDown of press 2 itself | event time = `pressTime` | unchanged | none. Stock asks for the camera *during* press 2, so press 2 reaches the reducer after the hold began. |
| HoldingCamera | PowerDown(taps = 3) | still armed | Idle | Cancel timer; ConsumePress; WakeDevice if not interactive; LaunchTarget (with the held camera as fallback) |
| HoldingCamera | Timer(gen matches) | | Idle(t2) | RunHeldCamera |
| HoldingCamera | PowerDown(anything else), or taps = 3 when no longer armed | late or unexpected press | Idle(t2) | Cancel timer; RunHeldCamera; stock handles this press |
| HoldingCamera | ConfigChanged(not armed) / Released (unload, hot reload) | | Idle(t2) | Cancel timer; RunHeldCamera |
| HoldingCamera | ConfigChanged(still armed) | | unchanged | none. Press 3 opens whatever target is set at that moment. |
| HoldingCamera | CameraRequested | shouldn't happen | Idle(t2) | Cancel timer; RunHeldCamera; run stock now |
| any | Timer(stale gen) | | unchanged | none |

- `W` is read from stock's `POWER_DOUBLE_TAP_MAX_TIME_MS` by reflection. If it can't be read, nothing is installed, the same as for any other missing target. `G` is a 50 ms grace period.
- Dry run (Phase 2): the hook-side sequencer latches the config's `dry_run` when a sequence's camera request arrives, and every effect of that sequence follows it. During a dry run, stock handles every press and the sequencer only logs (ADR 0006).
- Stock counts press 3 by its *event time*, but our hook only sees the event a few milliseconds later. Without `G`, a press at t2+295 that we handle at t2+303 would lose to the timer.
- `lastCameraPress` records *which* press 2 the camera came from, not when it ran. A counted press 3 always has an event time before the deadline, so comparing it with the flush time could never match; comparing it with press 2 works however late the hook sees press 3.
- Deadlines use the key event's uptime clock: `Handler.postAtTime(r, token, deadline)`.

Concurrency: key events arrive on the input thread and timers fire on a handler thread. The reducer runs under one lock and is cheap. Generation numbers make a timer that lost a race into a no-op. Effects run *after* the lock is released. Never hold it while calling `proceed()` or the original method.

Fail-open: anything unexpected runs the held camera. The camera only fails to open when a recognised third press cancelled it.

SOS guard: an option to hold the app launch for another W after press 3, so presses 4–5 never flash the app or the unlock prompt. **Not built by default.** Build it only if the Phase 3 test shows the app or unlock prompt covering the SOS countdown. See decision 2.

### 3.3 Launching the chosen app

1. Run on an existing system_server looper (the main looper), never on the input thread. Starting an activity inside `interceptKeyBeforeQueueing` risks input lag and lock-order deadlocks. The wake in §3.1 is the exception: PhoneWindowManager already wakes from the input thread. The only thread Sidearm creates reads the config once after boot and ends (§3.4).
2. Build a launcher intent (`MAIN`/`LAUNCHER`) for the stored component with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`, the same flags PhoneWindowManager uses. Open it only if it's one of its package's enabled, exported launcher activities, because system_server can start any activity, even an unexported one. If it isn't, run the camera instead so the press still does something, and log it.
3. Reuse PhoneWindowManager's own path. Get the PWM instance with `LocalServices.getService(WindowManagerPolicy.class)` (WindowManagerService registers it; verified on device), then read its `mKeyguardDelegate`. Check `isShowing()` **at launch time**, not at press time. If the keyguard is showing, ask SystemUI to unlock and then start the app. `dismissKeyguardToLaunch(intent)` was the plan, but Android 17's SystemUI ignores it (§2 correction). Phase 3 instead calls `dismissKeyguardLw(callback, null)`, 400 ms after press 3 so SystemUI has finished waking and can offer the fingerprint overlay, and not at all if another press followed (ADR 0008). That shows the fingerprint overlay or bouncer the way the Wallet tile does (face unlock can satisfy it too), and starts the app from `onDismissSucceeded`. If the unlock is cancelled, nothing opens, the same as Wallet. The callback is a plain `Binder` that handles the `TRANSACTION_on*` codes, read by reflection at boot and wrapped with `IKeyguardDismissCallback.Stub.asInterface`, so no hidden-API stubs are needed. If it isn't showing, call PWM's private `startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF)`, as its double-press-to-app setting does. Checking late covers the case where face unlock finishes between press 1 and press 3.
4. system_server is exempt from background-activity-start limits, so no special permission is needed.

### 3.4 Config

| Key (RemotePreferences group `settings`) | Type | Default |
|---|---|---|
| `enabled` | Boolean | false |
| `target_component` | String (`ComponentName.flattenToString()`) | none |
| `dry_run` | Boolean | false (added in Phase 2, ADR 0006) |

- The hook reads prefs once after boot, on a short-lived thread, because opening them is a blocking binder call to Vector. It retries a few times if Vector isn't ready. An `OnSharedPreferenceChangeListener` then hands each new snapshot to the main looper, so changes apply without a reboot. Values are validated, and anything invalid counts as `enabled = false`.
- Until the config app exists, debug builds carry `DevConfigActivity`, which writes the config from `adb shell am start` extras. It needs `android.permission.DUMP`, so only adb can start it (ADR 0006, `docs/architecture.md`). The config writer, `data.RemoteConfigStore`, arrived in Phase 2 for the same reason.
- The config app is one Compose screen with:
  - A status line showing:
    - whether Vector is connected, with its version and API
    - whether system_server is in scope
    - whether stock double-press is set to Camera
  - An on/off switch.
  - A searchable list of launcher activities with icons. It uses a `<queries>` entry for `MAIN/LAUNCHER`, so `QUERY_ALL_PACKAGES` isn't needed.

  The app has no services, receivers or WorkManager, and needs no root.

### 3.5 Safety nets

| Failure | What happens |
|---|---|
| A method, field, or the call site is missing at boot (an OTA changed it) | No hooks are installed and one log line is written. Stock camera, SOS and everything else keep working. The config app shows "inactive on this build". |
| Our interceptor logic throws | The `try/catch` returns the result `proceed()` already produced. The original is never run twice (see §3.1). |
| Target app uninstalled or not launchable | Press 3 opens the camera instead. |
| Timer never fires (shouldn't happen) | The next power press runs the held camera. |
| Hot reload, or the feature is disabled mid-sequence | The held camera runs immediately. |
| Bootloop | See [Recovery](#10-recovery). Hooks are installed only inside `onSystemServerStarting`, behind feature detection, and that code never throws. |
| Emergency SOS | We never write any GestureLauncherService field, never re-run `interceptPowerKeyDown`, and never touch `handleEmergencyGesture`. Stock counts every press exactly as before. |

### 3.6 Alternatives rejected

| Option | Why not |
|---|---|
| PhoneWindowManager's own triple-press setting (`mTriplePressOnPowerBehavior`) | `getMaxMultiPressPowerCount()` becomes 3, so every single press waits for the multi-press timeout and screen-off gets slower. PWM also resets its press counter when GestureLauncherService consumes a press, so it would never reach 3. |
| Accessibility service, or reading `/dev/input` as root | Needs a background process, which goes against the battery goal. The power key also isn't reliably delivered to accessibility services. |
| Hooking SystemUI's camera launch | A second scope and process, and system_server has already committed to the camera by then. |
| Toggling `mCameraDoubleTapPowerEnabled` | Mutates stock state and races with the settings observer. |
| ~~Our own `IKeyguardDismissCallback` with hidden-API stubs~~ | Was rejected because PhoneWindowManager's `dismissKeyguardToLaunch` did the same with no extra module. **Reversed 2026-11-01:** Android 17's SystemUI ignores `dismissKeyguardToLaunch`, so Phase 3 implements the callback after all, as a plain `Binder` with no stubs (§3.3). |
| Legacy Xposed API | Works in Vector, but API 102 gives live remote prefs, hot reload and proper invokers. |

## 4. Repo structure

The module split follows the usual pattern for LSPosed modules:
- Hide My Applist splits into `:app`, `:common` and `:xposed`.
- AM++ (libxposed API 102) splits into `:app`, `:core` and hook modules, with `META-INF/xposed` under `app/src/main/resources`.

The build conventions follow Google's Now in Android sample: a `build-logic/` directory of convention plugins, Spotless with ktlint, `.editorconfig`, `docs/`, and CI. Day-to-day conventions (packages, dependency rules, where files go) are in `.claude/rules/project-structure.md`.

Target layout. A phase in brackets means the item doesn't exist yet.

```
sidearm/
├─ README.md                  # what it is, safety warning, requirements, install, recovery, build
├─ CONTRIBUTING.md            # branches, commit messages, PRs, releases, and a playbook per situation
├─ CHANGELOG.md               # [Phase 5] Keep a Changelog, one entry per tagged release
├─ LICENSE                    # GPL-3.0-or-later (see §9)
├─ THIRD_PARTY_NOTICES.md     # licenses of copied or referenced third-party material (ECC's MIT notice)
├─ CLAUDE.md                  # Claude's project instructions (imports .claude/private/environment.md)
├─ .editorconfig              # LF, indentation, Kotlin style (ktlint reads it)
├─ .gitattributes .gitignore .gitmodules
├─ settings.gradle.kts        # module list; includeBuild("build-logic")
├─ build.gradle.kts           # root: plugin declarations and Spotless only
├─ gradle.properties
├─ gradle/libs.versions.toml  # every version, including SDK levels and the JVM target
├─ build-logic/               # convention plugins: sidearm.android.application,
│                             #   sidearm.android.library, sidearm.jvm.library
├─ core/                      # pure Kotlin: press sequencer + config contract
├─ hook/                      # system_server code: entry, intercept/, target/, effect/, config/
├─ app/                       # the APK: packaging, META-INF/xposed, data/ (config writer), debug-only dev tool;
│                             #   [Phase 4] config UI (ui/)
├─ docs/
│  ├─ plan.md                 # roadmap and status; design sections move out as phases finish
│  ├─ architecture.md         # how it works now: processes, modules, data flow
│  ├─ decisions/              # ADRs: 0001 branching model, 0002–0005 from §3.6 and §9, 0006 dry run, 0007 hold only when locked
│  ├─ hook-targets.md         # verified framework signatures, one section per build
│  └─ testing.md              # device matrix, SOS procedure, post-OTA checklist (moved from §6)
├─ scripts/                   # dev-install, setup-repo, start-branch, check-repo-hygiene, check-conventions,
│                             #   sync-ecc, device-smoke
├─ .githooks/                 # pre-commit, commit-msg, pre-push → check-repo-hygiene.sh, check-conventions.sh
├─ .github/                   # [Phase 5] workflows/ci.yml, workflows/release.yml, dependabot.yml,
│                             #   PULL_REQUEST_TEMPLATE.md (pre-flight checklist)
├─ .claude/                   # rules (ours + ecc/), agents, skills, settings.json; private/ is git-ignored
└─ third_party/ecc            # pinned submodule (dev tooling only)
```

Module dependencies run one way: `app → core ← hook`, plus `app ⇢ hook` as `runtimeOnly`. The hook classes get packaged into the APK, but UI code can't import them. That matters because the two run in different processes. `:hook` takes no libraries at all, because everything it depends on gets loaded into system_server.

Structure work by phase. It's spread out so each piece arrives when it first pays off.

| When | Structure work |
|---|---|
| Before the first push ✅ | `README.md`, `.editorconfig`, `.claude/rules/project-structure.md` |
| Before going public ✅ | `LICENSE`, `THIRD_PARTY_NOTICES.md`, the README safety warning, the public-repo rules (fork PRs, untrusted contributions, GitHub settings) |
| Phase 1 ✅ | `build-logic` with the three convention plugins; `:core`; Spotless + ktlint; Kover; SDK levels in the catalog; `./gradlew check` running everything; `:app` takes `runtimeOnly(project(":hook"))` (moved up from Phase 2) |
| After Phase 1 ✅ | The branching model: a `develop` branch, `CONTRIBUTING.md`, ADR 0001 (so `docs/decisions/` starts early), `scripts/check-conventions.sh` in the commit-msg and pre-push hooks, and `scripts/start-branch.sh` for starting work from the latest `develop` |
| Phase 2 (code done 2026-10-31) | Reshape `:hook` into `intercept/`, `target/`, `effect/` and `config/` as the probe is replaced; `docs/architecture.md`; first ADRs extracted from §3.6 and §9 (0002–0005), plus 0006 and 0007; local unit tests for `:hook` in the library convention plugin; `:app` `data/RemoteConfigStore` (moved up from Phase 4) and the debug-only `app/src/debug/` dev tool |
| Phase 3 ✅ | `docs/testing.md` (§6 moves there); `scripts/device-smoke.sh` |
| Phase 4 | `:app` `ui/` and `data/` packages; Compose configured in the application convention plugin |
| Phase 5 | `.github/` (CI running `./gradlew check` and the hygiene check, release workflow, Dependabot for Gradle and Actions, but not the ECC submodule, PR template); CI also checks PR titles against Conventional Commits; Dependabot targets `develop` with `build(deps)` commit prefixes; the release workflow runs on `vX.Y.Z` tags on `main`; `CHANGELOG.md`; release versioning |
| Public-repo settings ✅ (2026-10-31; the repo was already public) | The GitHub settings in the rules' §9: secret scanning, push protection, Dependabot alerts, private vulnerability reporting, and rulesets on `main`, `develop` and `v*` tags |

Deliberately left out, because the project is too small to need them: feature or `:core:*` sub-modules, a DI framework, product flavors, instrumented tests, and a hidden-API stub module (reflection is centralised in `hook/target/` instead; add one only if that outgrows itself).

Toolchain (pinned in Phase 0, all in `gradle/libs.versions.toml`):
- AGP 9.5.0, which is supported by Android Studio Rabbit 2 and supports API 37. Its Kotlin is built in, so don't apply `org.jetbrains.kotlin.android`.
- Kotlin 2.4.21, Gradle 9.8.2 (wrapper checksum-pinned), build-tools 36.0.0.
- `compileSdk`, `targetSdk` and `minSdk` are all 37, because the hooks depend on Android 17 internals.
- Pin the CI JDK to the same major version as Android Studio's bundled JBR (25).
- Use the R8 keep rules from the libxposed README, and check that `java_init.list` names the real compiled entry class.
- Hidden calls such as `PowerManager.wakeUp(long, int, String)`, `LocalServices` and `mKeyguardDelegate` use cached reflection, resolved once at boot as part of feature detection.

## 5. Phases

After each phase, run the ECC `code-reviewer` and `kotlin-reviewer` agents on the diff. Phases 2–3 also get `security-reviewer`, since system_server is the most privileged process on the phone.

| # | Phase | Deliverables | Exit criteria |
|---|---|---|---|
| 0 ✅ | Decide and verify (done 2026-10-29) | Sidearm / `io.github.soanssandha.sidearm`. Device jars checked with `dexdump` (no jadx needed) and recorded in `docs/hook-targets.md`. Scope `system`, the PWM route, and `ExceptionMode` all confirmed. A log-only probe in `:hook` verified them on a device. | Met: the probe logs taps 1/2/3 and `handleCameraGesture(false, 1)` from inside system_server. Hot reload works when versionCode changes (`scripts/dev-install.sh`). `input keyevent` presses do go through GestureLauncherService (`device=-1`). |
| 1 ✅ | Build conventions + core reducer, test-first (done 2026-10-30) | First, the `build-logic` convention plugins, Spotless + ktlint, Kover and `./gradlew check` (§4). Then `:core`, with every row of §3.2 as a test, plus timing edges (295/299/300/349/351 ms), timer races, flush paths, and the config contract and its validation. | Met: `./gradlew check` is green (formatting, lint on `:app` and `:hook`, tests, Kover gate). `:core` has 58 tests and 100% line and branch coverage; both gates are 80%. |
| 2 (built 2026-10-31; owner presses and SOS check pending) | Hook glue: dry run, then camera | `:hook` reshaped into its target packages (§4). Target resolvers with feature detection. The interceptors first run in **dry-run** mode, logging what they would do and always proceeding with stock, then for real with unlocked-phone launches only. `docs/architecture.md` and the first ADRs. Built: all of that, with `dry_run` as a config key switched from adb through the debug `DevConfigActivity`; 103 unit tests across `:core` and `:hook`; reviewed by `code-reviewer`, `kotlin-reviewer` and `security-reviewer`. On the phone (2026-10-31): Phase 2 targets dexdump-verified; install and hot reload work; config changes apply live. **Dry run met its criterion**: about 30 real 1/2/3 sequences were classified exactly as stock counted them. The first live runs found that holding the camera locks a phone that was unlocked (§7, keyguard row). ADR 0007 fixed this by holding only on a locked phone. **Live runs after the fix** (injected presses): from an unlocked phone, a double opens the normal camera at once and stays unlocked, and a triple opens the app unlocked, over a camera flash of about 0.1 s. From a locked phone, a double opens the secure camera 350 ms later, and a triple consumes press 3 and opens the camera (unlock-then-open is Phase 3). Still to do: the owner's own live presses, and a manual SOS check. | A few dozen real 1/2/3 presses match the dry-run logs. On real runs: triple opens the app unlocked, double opens the camera about 350 ms later, and single is unchanged. |
| 3 (built 2026-11-01) | Lock screen and SOS | Unlock-then-open through `dismissKeyguardLw` and a dismiss callback (§3.3; the Wallet-tile flow), wake on press 3, the full matrix. `docs/testing.md` and `scripts/device-smoke.sh`. Built: unlock-then-open 400 ms after press 3, with the fingerprint overlay and an SOS guard (ADR 0008), the wake on press 3, the smoke script, `docs/testing.md`; reviewed by `security-reviewer`. On the phone: a locked triple shows the fingerprint overlay and opens the app once unlocked; face unlock, the lock screen, an app already open and long press work; the Emergency SOS check passed locked and unlocked. Rows not yet run (animations off, a phone call) are marked in `docs/testing.md`. | Matrix passes, including the manual SOS check with the phone locked and face unlock on |
| 4 | Config app | **Design first:** study a few well-regarded Android apps (system settings, utilities, and other root or module managers) to work out what makes a good Android app, then present **3 distinct design variants** to the owner. Build only the variant the owner picks. Then: Compose screen in `ui/`, service client and app listing in `data/`, remote prefs through the `:core` config contract (`RemoteConfigStore` exists), status line, app picker. Replace libxposed's exported `XposedProvider` with a subclass that only accepts a binder from Vector's uid, because as shipped any app can hand the app process a fake service (security review, 2026-10-31; this can't reach the hook) | The owner has picked a design variant. Toggling the feature or changing the target takes effect live with no reboot. `dumpsys` shows no background components. |
| 5 | CI and release | GitHub Actions on push and PR runs `./gradlew check`, the hygiene check and `assembleRelease`, and uploads a signed APK artifact. A tag creates a release with notes from `CHANGELOG.md`. Dependabot for Gradle and Actions; a PR template with the pre-flight checklist. The keystore lives in Actions secrets and a password manager. | A green run on `main` produces an installable signed APK |
| 6 | Soak | Daily use for 1–2 weeks, plus the post-OTA checklist (`docs/testing.md`) | No missed or false triggers, and no SOS regression |

## 6. Testing

Moved to [testing.md](testing.md) in Phase 3: the automated tests, the device smoke test, the device matrix, the Emergency SOS check and the post-OTA checklist.

## 7. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| QPR1 or a later OTA changes GestureLauncherService | Medium | Module goes inactive; camera keeps working | Phase 0 decompile; feature detection; status line |
| `handleCameraGesture` is inlined or removed by R8/ART | Medium | Hook never fires | `deoptimize` at install (a failure is logged as a warning); the call site is checked with `dexdump` for every build (`docs/hook-targets.md`), since checking it at runtime would mean parsing dex |
| SystemUI or keyguard state gets confused when press 2 reports "launched" but the camera never opens | **Happened** (2026-10-31, CP3A) | A phone that was unlocked locks: the held double opens the secure camera instead of the normal one, and press 3 finds the keyguard showing | Cause: when it wakes from the press-1 screen-off, SystemUI's keyguard transition (`FromDozingTransitionInteractor` → `TransitionInteractor.maybeHandleInsecurePowerGesture`) only goes to GONE if the keyguard is dismissible or it has already seen an insecure camera launch (`occludingActivityWillDismissKeyguard`). Stock's camera request reaches SystemUI before the wake; a held one arrives 350 ms later, after it has chosen OCCLUDED/LOCKSCREEN. PWM's `CAMERA_GESTURE_PREVENT_LOCK` path runs either way. The old fallback (report not-launched) would lock too. Locked-phone sequences aren't affected. **Resolved** by ADR 0007: the camera is held only when the phone was locked at press 1. |
| A phantom tap from re-running the original | Low (designed out) | False SOS call | `proceed()` once, outside the try; Phase 0 check of `ExceptionMode`; reducer tests can't catch this, so the matrix covers it |
| libxposed upstream availability (Vector had to mirror it) | Medium | Can't fetch the dependency | It's `compileOnly`; pin 102.0.0 from Maven Central, and mirror the AAR privately if it disappears |
| A Vector update breaks the API (2.1 → 2.2 broke manager/daemon compatibility) | Medium | Rebuild needed | Pin versions; read release notes before updating Vector |
| SOS regression | Low | Safety-critical | Never write stock state; manual SOS test every release and every OTA |
| Bootloop | Low | Phone unusable until recovered | Feature detection, nothing that can throw during boot, §10 |

## 8. Out of scope (v1)

Wallet as the double-press action, different apps for different presses, per-user settings, shortcuts or deep links as targets, a haptic on triple (stretch goal), and any device other than the Pixel 9 Pro XL on Android 17.

## 9. Open decisions

1. ~~Name and `applicationId`~~. **Decided:** Sidearm, `io.github.soanssandha.sidearm`.
2. ~~SOS guard~~. **Decided:** don't build it unless the Phase 3 SOS test shows the app or the unlock prompt covering the SOS countdown. SOS fires either way. With the guard, nothing flashes, but every triple gains 300 ms.
3. **Cancelled unlock prompt.** Recommended: do nothing; this is SystemUI's default with `dismissKeyguardToLaunch`.
4. ~~License~~. **Decided:** GPL-3.0-or-later (`LICENSE`), the norm for LSPosed modules (Vector itself is GPL-3.0). Third-party notices, including ECC's MIT notice for the copies in `.claude/`, are in `THIRD_PARTY_NOTICES.md`.

## 10. Recovery

- Instant: turn the feature off in the config app (applies live).
- Disable the module in the Vector manager and reboot. If Magisk grants root to adb, the same works from the PC with `su -c '/data/adb/modules/zygisk_vector/cli modules disable io.github.soanssandha.sidearm'`.
- Bootloop: boot into Android safe mode by holding Volume Down as the boot animation starts; Magisk disables all modules in safe mode. This is the primary route because it needs nothing from adb. `adb wait-for-device shell magisk --remove-modules` also works, but only if Magisk grants root to adb.
