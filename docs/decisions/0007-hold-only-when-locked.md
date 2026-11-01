# 0007. Hold the camera only when the phone was locked at press 1

- Status: Accepted. Narrows [0002](0002-hook-gesture-launcher-service.md), which held the camera on every double press.
- Date: 2026-11-01
- Evidence: [plan.md](../plan.md) §7 (keyguard row); how it's built: [architecture.md](../architecture.md)

## Context

- The Phase 2 live runs (Android 17 QPR1, CP3A) showed that holding the camera locks a phone that was unlocked:
  - From an unlocked phone, stock's double press opens the normal camera and the phone stays unlocked. With the hold, it opened the secure (lock-screen) camera.
  - On a triple, the keyguard was already showing by press 3, so the app couldn't open without an unlock.
- The cause is in SystemUI. Press 1 starts turning the screen off, and press 2 wakes it, as in stock. On that wake, SystemUI's keyguard transition (`FromDozingTransitionInteractor` → `TransitionInteractor.maybeHandleInsecurePowerGesture`) goes back to unlocked only if the keyguard is dismissible, or it has already seen an insecure camera launch. With "power button instantly locks" (Android's default for a secure lock screen), only the camera launch qualifies. Stock's camera request reaches SystemUI just before the wake; a held one arrives 350 ms later, after SystemUI has chosen the lock screen.
- Only SystemUI's own camera handler can produce that signal, so a hook in system_server can't keep the phone unlocked while it holds the camera.
- On a locked phone, the hold changes nothing about the keyguard: stock opens the secure camera too.

## Decision

- Hold the camera only if the keyguard was showing at press 1 of the sequence. Otherwise let stock open the camera at once.
- Read the keyguard state at press 1, not at press 2. Press 1 is a key down that arrives before stock starts turning the screen off. By press 2 the screen-off already makes the keyguard report itself showing, even on a phone that was unlocked. The power hook records `KeyguardServiceDelegate.isShowing()`, a cached flag, whenever stock counts a first press. If it can't be read, the phone counts as locked.
- A third press within the window after a camera that has already run (let through on an unlocked phone, or released by a timer that won the race) opens the target over the camera, with no camera fallback.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep holding on every double press, and rely on Phase 3's unlock prompt | Every triple would ask for an unlock, even straight after using the phone, and an unlocked double press would open the secure camera. That makes the most common case worse than stock. |
| Let SystemUI get the camera request on time, but hold the camera activity's launch in system_server (on ActivityTaskManager's start path), replaying SystemUI's launch later | Faithful, with no camera flash, but it needs a new, OTA-sensitive hook, and replaying SystemUI's launch, options and animation as another caller. Worth an experiment only if the camera flash bothers anyone. |
| Report "not launched" on press 2 and wake the device ourselves (the plan's original fallback) | The keyguard's pending lock then goes ahead, so the phone locks anyway. |
| Hook SystemUI | A second scope and process, already rejected in 0002. |

## Consequences

- From an unlocked phone, a double press is exactly stock, with no added delay.
- From an unlocked phone, a triple shows the camera for about a tenth of a second before the app opens over it, and the camera app stays in recents.
- From a locked phone, nothing changes from 0002: the camera opens about 350 ms after press 2, and a triple goes to the app through the unlock prompt (Phase 3).
- The reducer's `CameraRequested` carries `startedLocked`, and `Idle.lastCameraPress` covers both "let through" and "released from a hold".
