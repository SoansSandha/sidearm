# Hook targets per build

Everything Sidearm reaches into, checked against each supported build's own framework jars. Add a section for each new build fingerprint after an OTA, and keep the old ones.

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
| `…keyguard.KeyguardServiceDelegate` | `isShowing` / `dismissKeyguardToLaunch` | `()Z` / `(Landroid/content/Intent;)V` | Unlock-then-launch |
| `com.android.server.wm.WindowManagerService` | constructor | `LocalServices.addService(WindowManagerPolicy.class, mPolicy)` | Reaching the PWM instance |

### What the Phase 0 probe logs

The probe logs every press along with the device's double-press and SOS state (`cameraEnabled`, `walletEnabled`, `emergencyEnabled`). Read a device's settings from `adb logcat -s Sidearm` rather than recording them here.

Probe log for an injected triple (`input keyevent KEYCODE_POWER KEYCODE_POWER KEYCODE_POWER`) on a device with camera double-press and Emergency SOS enabled:

```
powerDown taps=1 result=false outLaunched=false
handleCameraGesture(useWakelock=false, source=1) -> true
powerDown taps=2 result=true  outLaunched=true
powerDown taps=3 result=true  outLaunched=false   (swallowed because SOS is enabled)
```

- Injected presses show `device=-1`; the physical button shows `device=2`.
- `input keyevent --delay <ms>` is available for spacing presses in scripts.
