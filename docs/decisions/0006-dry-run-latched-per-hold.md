# 0006. A dry-run flag in the config, latched per hold, and an adb-only config tool until the app exists

- Status: Accepted
- Date: 2026-10-31
- How it works: [architecture.md](../architecture.md) "Config and dry run" and "Developing against a phone"

## Context

- Phase 2 first runs the hook in a dry run: it logs what it would do while every press stays stock. Its exit criterion compares real presses with those logs, and the same check is useful after every OTA.
- Switching between dry and live runs should take seconds, not a rebuild.
- The mode can't simply be read when each effect runs. If dry run is switched off mid-hold, the timer would replay a camera that stock had already opened. If it's switched on mid-hold, a held camera would never open.
- The hook reads its settings only from Vector's remote preferences, and only the module's own app can write those. The config app is Phase 4.

## Decision

- Add `dry_run` (Boolean, default false) to the `:core` config contract.
- The hook-side `Sequencer` latches the mode when a hold starts. Every effect of that sequence, including a late press 3 after the timer won, follows the latched mode.
- Until Phase 4, debug builds carry `DevConfigActivity`, which writes the config from `adb shell am start` extras. It's exported but needs `android.permission.DUMP`, which the adb shell holds and third-party apps can't get. It lives in the `debug` source set, so release builds don't contain it.
- The config writer itself (`data.RemoteConfigStore`) is in `main`, for the Phase 4 app to reuse.

## Alternatives considered

| Option | Why not |
|---|---|
| A build-time dry-run flag | Every switch needs a rebuild and reinstall, and a released build couldn't be put into a dry run after an OTA. |
| A `debug.sidearm.*` system property read by the hook | A second config channel in system_server code that ships in every build. |
| The exported activity without a permission | Any app on the phone could choose what system_server opens. |
| Waiting for the Phase 4 app | Phase 2 can't reach its exit criterion without a way to set a target. |

## Consequences

- The Phase 4 app can offer a "test mode" with no hook changes.
- `dry_run` is permanent in the contract. Renaming it resets the setting on every install.
- Switching modes takes effect from the next hold. A sequence already in progress finishes in its own mode.
