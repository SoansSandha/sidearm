# 0008. Open the app on a locked phone through SystemUI's unlock flow, 400 ms after press 3

- Status: Accepted. Replaces the `dismissKeyguardToLaunch` route in [plan.md](../plan.md) §3.3, and adds a partial SOS guard to [0004](0004-no-sos-guard-by-default.md) for locked phones.
- Date: 2026-11-01
- How it's built: [architecture.md](../architecture.md), "A press, thread by thread"

## Context

- A triple press on a locked phone should work like the Wallet tile on the lock screen: the fingerprint overlay or face unlock, then the app. If the unlock is cancelled, nothing opens.
- The plan's route was `KeyguardServiceDelegate.dismissKeyguardToLaunch(intent)`. On Android 17, SystemUI's `KeyguardService.dismissKeyguardToLaunch` only logs "Ignoring dismissKeyguardToLaunch".
- `PhoneWindowManager.dismissKeyguardLw(IKeyguardDismissCallback, CharSequence)` is public and still works. With the scene container on, as on the tested build, SystemUI handles it with `DeviceEntryInteractor.attemptDeviceEntry(callback)`, the same entry the Wallet tile reaches. It shows the fingerprint overlay (alternate bouncer) if `canShowAlternateBouncer`, and the full bouncer otherwise.
- `canShowAlternateBouncer` is false while the keyguard is still leaving the always-on display, which takes up to 500 ms after a wake (`FromDozingTransitionInteractor`, `FromAodTransitionInteractor`). In a locked triple the last wake is at press 1 or press 2, and press 3 comes 150–300 ms after press 2. Asked at press 3, SystemUI showed the full bouncer on the test phone, where the fingerprint sensor is off.
- Presses 4 and 5 of an Emergency SOS sequence may follow press 3, and nothing should cover the SOS countdown.

## Decision

- On a locked phone, press 3 schedules the unlock request 400 ms later on the main looper. Press 3's event time is read on the input thread, as press 3 is handled. When the request runs:
  - if any power press came after press 3, or that can't be told, it does nothing (the user is heading for SOS, or did something else). SOS only counts presses less than 300 ms apart, so its press 4 always lands inside the delay;
  - if the keyguard has gone in the meantime (face unlock), it opens the app straight away;
  - otherwise it calls `dismissKeyguardLw` with a one-shot callback.
- The callback is a plain `Binder` that answers `IKeyguardDismissCallback`'s transaction codes, read from the framework's own `Stub` at boot and wrapped with `Stub.asInterface`. No hidden-API stubs are needed at compile time.
- `onDismissSucceeded` opens the app through PhoneWindowManager, but only once PhoneWindowManager also sees the keyguard gone. It looks every 100 ms for up to 0.5 s, so an early or forged success report can't open anything over the lock screen.
- `onDismissCancelled` opens nothing.
- `onDismissError` looks again: if the keyguard is already gone (for example a face unlock just before the request), the app opens; otherwise the held camera runs if there is one, so press 3 still does something.
- On an unlocked phone nothing changes: the app opens at press 3.

## Alternatives considered

| Option | Why not |
|---|---|
| Ask at press 3, without the delay | SystemUI shows the full bouncer, with the fingerprint sensor off, so fingerprint unlock (the reason to do this) doesn't work. |
| Wait for SystemUI's transition to finish instead of a fixed delay | system_server can't see SystemUI's keyguard transition state. The fixed delay covers the documented 500 ms transition with room to spare. |
| Start the app first, behind the keyguard, then ask to unlock | No callback needed, but a cancelled unlock would leave the app waiting behind the keyguard, to appear at the next unlock. |
| Our own hidden-API stub module for `IKeyguardDismissCallback.Stub` | More build machinery for the same result as answering three transaction codes. |

## Consequences

- From a locked phone, the lock screen shows for under half a second after press 3, then the fingerprint overlay; the app opens once unlocked. Face unlock can finish first, in which case the app opens with no prompt.
- On a locked phone, a press after press 3 calls the unlock off, so the prompt can't appear over the SOS countdown. Unlocked phones still open the app at press 3; whether that covers the countdown is for the SOS check in `docs/testing.md` (ADR 0004).
- If a press 3 arrives while the device isn't interactive (rare), its wake starts a new transition, and SystemUI may show the full bouncer anyway. Face unlock and the PIN still work there.
- Four more framework members to check per build: `dismissKeyguardLw`, and `IKeyguardDismissCallback$Stub`'s `DESCRIPTOR`, transaction codes and `asInterface`.
