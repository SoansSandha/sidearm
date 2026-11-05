# 0010. Two launch modes: a clean one that holds SystemUI's camera activity start, and classic as the fallback

- Status: Accepted. Builds on [0007](0007-hold-only-when-locked.md), whose alternatives listed this as the experiment to try if the camera flash bothered anyone, and corrects 0007's account of why holding locked the phone (below).
- Date: 2026-11-03
- How it's built: [architecture.md](../architecture.md), "A press, thread by thread"; the targets are in [hook-targets.md](../hook-targets.md), "Added in Phase 6"

## Context

- On an unlocked phone, classic opens the app over a camera that shows for about 0.1 s. And it races: SystemUI starts the camera some main-thread hops after press 2, so when SystemUI is slow (just after a reboot) the camera starts after press 3 has opened the app, and covers it.
- The owner wants a launch with no camera, and wants today's behaviour kept as a fallback in case an OTA breaks the new one.
- Holding the camera request in `GestureLauncherService` locks an unlocked phone (0007). On Android 17 the power double-press reaches SystemUI through the `oneway` `IStatusBar.onCameraLaunchGestureDetected`. SystemUI's `CommandQueue` calls `PowerInteractor.onCameraLaunchGestureDetected()` synchronously on the binder thread, and that is what keeps the phone unlocked through the wake ("launched from entered", which `SceneContainerStartable` uses to go back to Gone). Only later, on its main thread, does SystemUI start the camera with `IActivityTaskManager.startActivityAsUser`: calling package `com.android.systemui`, action `STILL_IMAGE_CAMERA`, no caller thread or result target, and its result ignored.
- **Correction to 0007.** With the scene container on, `TransitionInteractor.maybeHandleInsecurePowerGesture` returns early, so it wasn't what locked the phone. The held request reached SystemUI after `onFinishedWakingUp` had cleared the "launched" state, so SystemUI saw "launched from not entered" and showed the lock screen with the secure camera over it. 0007's decision is unchanged.

## Decision

- A config key, `launch_mode`: `classic` (the default, and what anything unknown reads as) or `clean`. The app sets it on a Settings screen (⋮ › Settings; the owner's design variant B).
- Clean, on a phone that was unlocked at press 1: the camera request goes to SystemUI at once, as in stock, and the hook on `ActivityTaskManagerService.startActivityAsUser` holds SystemUI's camera start when it arrives:
  - The hook sees every activity start on the phone, so it first reads one volatile field and passes everything through unless a clean sequence expects a start (up to 2 s after press 2).
  - It matches the call above and checks the calling uid against SystemUI's, because a held call returns before the framework would check that the package belongs to the caller. SystemUI's uid is looked up as system_server, not as the calling app (which might not be allowed to see SystemUI), and kept only once found.
  - A held start returns `START_SUCCESS`. Its arguments and a `Binder.clearCallingIdentity()` token are stored inside the sequencer's lock.
  - A third press opens the app and drops the start. If the app can't be opened, the held start is replayed instead.
  - At the end of the press window (300 + 50 ms after press 2), the held start is replayed on the main looper, through the `ORIGIN` invoker, with SystemUI's identity restored.
  - If the start comes after press 3 has already opened the app (a slow SystemUI), it's dropped, up to 2 s after press 2; a timer closes that window. If press 3's app can't be opened, the hook reports it and the late start runs instead, so press 3 still opens something.
- A locked phone behaves the same in both modes: the camera request is held in `GestureLauncherService` and press 3 goes through the unlock prompt (0008).
- The activity-start hook is installed apart from the others. If its targets are missing or it can't be hooked, the clean mode reads as classic, it's logged, and everything else installs as usual.
- Classic never consults the new hook, so it stays exactly as it was: a fallback that doesn't depend on the new code.

## Alternatives considered

| Option | Why not |
|---|---|
| Hold the camera request in `GestureLauncherService` on an unlocked phone too | Locks the phone (0007, and the correction above). |
| Hold at `ActivityStarter.execute` or `executeRequest` | Starters come from a pool of three and are recycled after `execute`; `executeRequest` runs under the window manager's global lock; replaying either skips `execute`'s own handling, and both read the caller from `Binder` themselves. The binder entry holds no lock and takes the caller from `Binder` alone. |
| Also use the new hook to drop a late camera start in classic, fixing its race | Classic would then depend on the new hook, and stop being a clean fallback. Kept as a possible later change. |
| Only one mode, clean | No way back without a new build if an OTA breaks it. |

## Consequences

- In the clean mode an unlocked double press opens the camera 350 ms later than stock, the same delay a locked phone already has. A clean triple still gets SystemUI's camera haptic and camera warm-up at press 2.
- The race in classic remains; clean avoids it.
- Every activity start on the phone passes through the hook: one volatile read and a comparison when no clean sequence is under way.
- The hook depends on SystemUI's camera start keeping its shape (package, action, no caller thread). If an update changes it, the start isn't matched and simply runs, so the camera opens as in classic and a triple opens the app over it.
- The config app can't see whether the clean hook installed; the hook logs `clean=available` or `unavailable` when it starts. The app's untested-build warning still applies.
