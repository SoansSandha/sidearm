# Hook targets per build

Everything Sidearm reaches into, checked against each supported build's own framework jars. Add a section for each new build fingerprint after an OTA, and keep the old ones. The config app's `TestedBuilds` (`app/src/main/kotlin/.../data/TestedBuilds.kt`) lists the same builds; it warns on any other build, so add each new one there too. It needs the full `ro.build.fingerprint`, which ends in `:user/release-keys`; the headings below leave that off.

The config app reads the double-press setting the way `GestureLauncherService` does (`isCameraDoubleTapPowerSettingEnabled`): the framework integers `config_doubleTapPowerGestureMode` and `config_doubleTapPowerGestureMultiTargetDefaultAction`, and the `Settings.Secure` keys `double_tap_power_button_gesture_enabled`, `double_tap_power_button_gesture` and `camera_double_tap_power_gesture_disabled`. All three keys are `@Readable` on `android17-release`, so an app can read them. Its "Open gesture settings" button starts `android.settings.action.DOUBLE_TAP_POWER_SETTINGS` (`Settings$DoubleTapPowerSettingsActivity`), falling back to the Settings home.

## How to check a build

```bash
# Git Bash: MSYS_NO_PATHCONV stops /system/... being rewritten into a Windows path.
MSYS_NO_PATHCONV=1 adb pull /system/framework/services.jar .
MSYS_NO_PATHCONV=1 adb pull /system/framework/framework.jar .
unzip -o -q services.jar 'classes*.dex' -d svc
DD="$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/dexdump.exe"
# Disassemble one class, e.g. GestureLauncherService (it lives in svc/classes.dex on CP3A):
"$DD" -d svc/classes.dex | awk '/^Class #/{k=0} /Class descriptor/{k=/GestureLauncherService;/} k' > gls.txt
```

Then confirm every row in the tables below: the names and descriptors exist, and in `interceptPowerKeyDown` the `invoke-virtual … handleCameraGesture:(ZI)Z` call site is still there.

## google/komodo/komodo:17/CP3A.261005.005/16271449 (Android 17 QPR1, 2026-10-05 patch)

Checked 2026-10-29. The bytecode logic of `interceptPowerKeyDown` matches the `android17-release` source:
- 500 ms and 300 ms windows
- SOS at 5 taps
- camera at 2 taps with `handleCameraGesture(false, 1)`
- wallet via `handleWalletGesture()`

### How stock counts presses

From `interceptPowerKeyDown` in the `android17-release` source (L662–737), whose logic this build's bytecode matched. `:core`'s test fixture `StockTapCounter` models the fast-tap part.
- Long presses return `false` before anything is counted, so `mLastPowerDown` doesn't change. That's why the hook checks `mLastPowerDown == eventTime`.
- `powerTapInterval = eventTime - mLastPowerDown`. At 500 ms or more the count restarts at 1; at 300 ms or more (`>=`) it also restarts at 1; below 300 ms it goes up by one. A press exactly 300 ms after the previous one is therefore a new first press.
- The camera launches only when the count is exactly 2 and the interval is under 300 ms.
- With Emergency SOS on, 5 taps that come faster than `EMERGENCY_GESTURE_TAP_DETECTION_MIN_TIME_MS` restart the count at 1 instead of firing SOS.

### `com.android.server.GestureLauncherService` (services.jar, classes.dex)

| Kind | Name | Descriptor / value | Used for |
|---|---|---|---|
| method | `interceptPowerKeyDown` | `(Landroid/view/KeyEvent;ZLandroid/util/MutableBoolean;)Z` | Hook 1 |
| method | `handleCameraGesture` | `(ZI)Z`, called directly from `interceptPowerKeyDown` (not inlined) | Hook 2 |
| method | `handleWalletGesture` | `()Z` | Not hooked (wallet is out of scope) |
| method | `sendGestureTargetActivityPendingIntent` | **absent**: R8 removed it at platform build time | — |
| field | `mPowerButtonConsecutiveTaps` | `I` | Tap count |
| field | `mLastPowerDown` | `J` | "Did stock count this press" check |
| field | `mCameraDoubleTapPowerEnabled` / `mWalletDoubleTapPowerEnabled` / `mEmergencyGestureEnabled` | `Z` | Status / guards |
| field | `POWER_DOUBLE_TAP_MAX_TIME_MS` | `J` = 300 | Window `W` |

### Other classes

| Class | Member | Descriptor / value | Used for |
|---|---|---|---|
| `android.app.StatusBarManager` | `CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP` | `I` = 1 | Filter the camera hook to the power path |
| `android.os.PowerManager` | `wakeUp` | `(JILjava/lang/String;)V` | Wake on press 3 when not interactive |
| `android.os.PowerManager` | `WAKE_REASON_POWER_BUTTON` | `I` = 1 | Wake reason |
| `com.android.server.policy.PhoneWindowManager` | `mKeyguardDelegate` | `Lcom/android/server/policy/keyguard/KeyguardServiceDelegate;` | Keyguard path |
| `…keyguard.KeyguardServiceDelegate` | `isShowing` / `dismissKeyguardToLaunch` | `()Z` / `(Landroid/content/Intent;)V` | `isShowing`: the keyguard check. `dismissKeyguardToLaunch`: not used; SystemUI ignores it (see Phase 3) |
| `com.android.server.wm.WindowManagerService` | constructor | `LocalServices.addService(WindowManagerPolicy.class, mPolicy)` | Reaching the PWM instance |

### Added in Phase 2

Phase 2's launch path also needs these. Checked against this build's jars on 2026-10-31. They match `android17-release` (PWM L446, L4090, `LocalServices` L46, `UserHandle` L81), except that the platform's R8 made `mContext` and `startActivityAsUser` public. The resolver looks members up with `getDeclaredMethod`/`getDeclaredField` and makes them accessible, so their visibility doesn't matter. `PhoneWindowManager` and `KeyguardServiceDelegate` are in `services.jar` (`classes2.dex`), `WindowManagerPolicy` too (`classes3.dex`); `LocalServices` and `UserHandle` are in `framework.jar`.

| Class | Member | Descriptor / value | Used for |
|---|---|---|---|
| `com.android.server.LocalServices` | `getService` | `(Ljava/lang/Class;)Ljava/lang/Object;`, public static | Looking up the PWM instance at launch time |
| `com.android.server.policy.WindowManagerPolicy` | (interface) | implemented by `PhoneWindowManager` | The `LocalServices` key |
| `com.android.server.policy.PhoneWindowManager` | `mContext` | `Landroid/content/Context;` (public on this build) | Checking the target is a launcher activity |
| `com.android.server.policy.PhoneWindowManager` | `startActivityAsUser` | `(Landroid/content/Intent;Landroid/os/UserHandle;)V` (public final on this build; private in source) | Opening the app (skips while setup is incomplete) |
| `android.os.UserHandle` | `CURRENT_OR_SELF` | `Landroid/os/UserHandle;`, public static final | The user PWM's own launches use |

### Added in Phase 3

Checked against this build's jars on 2026-11-01. `IKeyguardDismissCallback` and its `Stub` are in `framework.jar` (`classes5.dex`). `PowerManager.wakeUp` and `WAKE_REASON_POWER_BUTTON` are in the table above, checked in Phase 0.

| Class | Member | Descriptor / value | Used for |
|---|---|---|---|
| `com.android.server.policy.PhoneWindowManager` | `dismissKeyguardLw` | `(Lcom/android/internal/policy/IKeyguardDismissCallback;Ljava/lang/CharSequence;)V`, public | Asking SystemUI to unlock before opening the app (ADR 0008) |
| `com.android.internal.policy.IKeyguardDismissCallback` | `onDismissError` / `onDismissSucceeded` / `onDismissCancelled` | `()V`; a `oneway` interface | The unlock result |
| `…IKeyguardDismissCallback$Stub` | `DESCRIPTOR` | `Ljava/lang/String;` | The callback `Binder`'s descriptor |
| `…IKeyguardDismissCallback$Stub` | `TRANSACTION_onDismissError` / `…Succeeded` / `…Cancelled` | `I` | Which result a transaction carries |
| `…IKeyguardDismissCallback$Stub` | `asInterface` | `(Landroid/os/IBinder;)Lcom/android/internal/policy/IKeyguardDismissCallback;` | Wrapping the callback `Binder` for `dismissKeyguardLw` |

On this build SystemUI ignores `KeyguardServiceDelegate.dismissKeyguardToLaunch` (it only logs "Ignoring dismissKeyguardToLaunch"), so that member isn't used even though it exists.

### What the hook logs

While the feature is armed, it logs every press stock counted and every sequencer decision; a dry run marks its decisions and only checks the target. While it's off, it logs nothing per press. Read a device's double-press and SOS settings from Settings, not from here.

Expected lines for an injected triple with the feature armed (shortened). From an unlocked phone, the camera isn't held (ADR 0007):

```
PowerDown(eventTime=1000, taps=1, interactive=true) -> stock
CameraRequested(pressTime=1150, …, startedLocked=false) -> RunCameraNow
PowerDown(eventTime=1150, taps=2, interactive=false) -> stock
PowerDown(eventTime=1300, taps=3, interactive=true) -> ConsumePress, LaunchTarget(…, fallback=null)
Asked PhoneWindowManager to open com.example.app/com.example.app.MainActivity
```

From a locked phone, the camera is held; until Phase 3, press 3 then opens the camera:

```
PowerDown(eventTime=1000, taps=1, interactive=true) -> stock
CameraRequested(pressTime=1150, …, startedLocked=true) -> ScheduleTimer(deadline=1500, generation=1), HoldCamera
PowerDown(eventTime=1150, taps=2, interactive=false) -> stock
PowerDown(eventTime=1300, taps=3, interactive=true) -> CancelTimer(generation=1), ConsumePress, LaunchTarget(…)
Not opening com.example.app/…, because the keyguard is showing (…); opening the camera instead
```

### What the Phase 0 probe logged

Phase 2 replaced the probe. It logged every press along with the device's double-press and SOS state (`cameraEnabled`, `walletEnabled`, `emergencyEnabled`).

Probe log for an injected triple (`input keyevent KEYCODE_POWER KEYCODE_POWER KEYCODE_POWER`) on a device with camera double-press and Emergency SOS enabled:

```
powerDown taps=1 result=false outLaunched=false
handleCameraGesture(useWakelock=false, source=1) -> true
powerDown taps=2 result=true  outLaunched=true
powerDown taps=3 result=true  outLaunched=false   (swallowed because SOS is enabled)
```

- Injected presses show `device=-1`; the physical button shows `device=2`.
- `input keyevent --delay <ms>` is available for spacing presses in scripts.
