# 0003. Build on libxposed API 102 under Vector, not the legacy Xposed API

- Status: Accepted
- Date: 2026-10-29 (recorded 2026-10-31)
- Facts: [plan.md](../plan.md) §2

## Context

- On Android 17 the maintained framework is Vector (JingMatrix's successor to LSPosed). Vector 2.2 implements libxposed API 102 and still runs legacy `XposedBridge` modules.
- Sidearm needs settings that change live without a reboot, a fast way to iterate on hook code, and a way to call the original of a hooked method later.

## Decision

- Write the module against libxposed API 102 (`io.github.libxposed:api:102.0.0`, `compileOnly`), with metadata in `META-INF/xposed/` and scope `system`.
- The app reaches Vector through `io.github.libxposed:service:102.0.0` and stores settings in its remote preferences.
- Pin both from Maven Central.

## Alternatives considered

| Option | Why not |
|---|---|
| Legacy `XposedBridge` API | Runs in Vector, but has no live remote preferences, no hot reload and no typed invokers. Settings would need `XSharedPreferences` file tricks or a reboot. |
| Our own Zygisk module | We'd rebuild hooking, scoping and module management that Vector already does. |

## Consequences

- Settings reach system_server live, and Vector hot-reloads the hook whenever versionCode changes (`scripts/dev-install.sh`).
- `getInvoker(...).setType(ORIGIN)` replays the held camera call without going through any hook.
- libxposed's upstream was suspended in 2026 and Vector mirrors it. If the artifacts disappear from Maven Central, we mirror the AAR privately. The API is `compileOnly`, so nothing of it ships in the APK.
- A Vector update can break compatibility, as 2.1 → 2.2 did between manager and daemon. Read the release notes before updating.
