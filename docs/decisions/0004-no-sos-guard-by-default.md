# 0004. Don't delay the app launch to protect the SOS countdown, unless testing shows it's needed

- Status: Accepted
- Date: 2026-10-29 (recorded 2026-10-31)
- Design: [plan.md](../plan.md) §3.2 (SOS guard), §6 (SOS test)

## Context

- Presses 4 and 5 still reach stock after a triple press, so a five-press SOS sequence fires SOS exactly as before.
- On the way there, press 3 opens the chosen app, or shows the unlock prompt if the phone is locked. That could briefly appear over the SOS countdown.
- A guard could hold the launch for one more press window after press 3, so presses 4–5 cancel it before anything appears. Every triple press would then take about 300 ms longer.

## Decision

- Don't build the guard by default.
- The Phase 3 SOS test (phone locked, face unlock on, countdown on) checks whether the app or the unlock prompt ever covers the countdown. Build the guard only if it does.

## Consequences

- Triple presses stay as fast as the press window allows.
- SOS fires either way. What's at stake is only whether something flashes over its countdown.
- If the guard is needed, it's a reducer change in `:core`, tested there, with no new hook.
