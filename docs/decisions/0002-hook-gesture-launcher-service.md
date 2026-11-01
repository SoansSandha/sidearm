# 0002. Hook `GestureLauncherService` and hold the camera for one press window

- Status: Accepted. Narrowed by [0007](0007-hold-only-when-locked.md): the camera is held only when the phone was locked at press 1.
- Date: 2026-10-29 (recorded 2026-10-31)
- Design: [plan.md](../plan.md) §3.1–3.3; how it's built: [architecture.md](../architecture.md)

## Context

- A triple press has to open an app without changing what 1, 2, 4 or 5 presses or a long press do. Press 5 is Emergency SOS.
- On Android 17, `GestureLauncherService.interceptPowerKeyDown` in system_server already counts fast presses, opens the camera on press 2 and fires SOS on press 5. PhoneWindowManager consumes a press when that method returns `true` or sets `outLaunched`.
- By the time a press 3 arrives, stock has already asked SystemUI to open the camera.
- The hook should run in one process, add no background work, and leave stock's own state alone.

## Decision

- Hook two methods of `GestureLauncherService` in system_server, and nothing in SystemUI:
  - `handleCameraGesture`: when the request comes from the power double-press and the feature is armed, skip the original and return `true`, as stock does after a launch. Replay it later through an `ORIGIN` invoker.
  - `interceptPowerKeyDown`: let stock run exactly once, then pass every press it counted to the sequencer. On a recognised press 3, return `true`, so PhoneWindowManager doesn't toggle the screen.
- Hold the camera for one stock window plus a grace period (300 + 50 ms). If press 3 doesn't arrive, the camera opens.
- Open the app through PhoneWindowManager's own launch path, the same one its double-press-to-app setting uses.
- Never write a `GestureLauncherService` field, never run `interceptPowerKeyDown` twice, and never touch the SOS gesture.

## Alternatives considered

| Option | Why not |
|---|---|
| PhoneWindowManager's own triple-press setting (`mTriplePressOnPowerBehavior`) | Every single press would wait for the multi-press timeout, which slows screen-off. PWM also resets its counter when `GestureLauncherService` consumes a press, so it would never reach 3. |
| Accessibility service, or reading `/dev/input` as root | Needs a background process. The power key also isn't reliably delivered to accessibility services. |
| Hooking SystemUI's camera launch | Needs a second scope and process, and system_server has already committed to the camera by then. |
| Toggling `mCameraDoubleTapPowerEnabled` | Changes stock state, and races with the settings observer. |
| Our own `IKeyguardDismissCallback` with hidden-API stubs | PhoneWindowManager's `dismissKeyguardToLaunch` already does this, with no extra module. |

## Consequences

- The camera opens about 350 ms after press 2 instead of at once. That's the cost of waiting to see whether press 3 comes.
- The triple press only works while the stock double-press is set to Camera.
- Every Android build has to be checked against the hooked members (`docs/hook-targets.md`). If one is missing, Sidearm installs nothing and the button stays stock.
- Re-running `interceptPowerKeyDown` would count a phantom tap, which could open the camera on press 1 and fire SOS a press early. The hookers are written so it runs once, and hooks use `ExceptionMode.PROTECTIVE`, which never re-runs it either.
