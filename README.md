# Sidearm

Triple-press the power button to open an app of your choice, on a rooted Google Pixel 9 Pro XL running Android 17. Sidearm is an LSPosed-style module for the [Vector](https://github.com/JingMatrix/Vector) framework (libxposed API 102).

> **Status: in development.** Phase 0 is done: the hook targets are verified on Android 17 QPR1, and a log-only probe runs in system_server. The feature itself isn't built yet. See [docs/plan.md](docs/plan.md).

> [!WARNING]
> **Read this before installing anything from this repo.**
> - Sidearm runs inside `system_server`, Android's core process, and changes how the power button behaves. That is the same button that triggers **Emergency SOS**.
> - Development builds are unfinished. Don't put one on a phone you rely on for emergencies.
> - Whenever you install or update it, test Emergency SOS by hand (start the countdown, then cancel it) and know the recovery steps below.
> - It's provided as is, with no warranty (see [LICENSE](LICENSE)). You use it at your own risk.

## What it does

| Presses | Result |
|---|---|
| 1 | Screen on/off (unchanged) |
| 2 | Camera (unchanged, opens about 350 ms later) |
| 3 | Your chosen app. If the phone is locked, the unlock prompt shows first. |
| 5 | Emergency SOS (unchanged) |
| Long press | Assistant or power menu (unchanged) |

Each press has to land within 300 ms of the previous one, the same window Android uses for the camera double-press. Nothing runs in the background.

## How it works

Android already counts fast power presses in `GestureLauncherService`, inside system_server. Sidearm hooks that service:
- On press 2, it holds the camera launch for one press window.
- If a third press arrives within that window, it cancels the camera and opens your app.
- If none arrives, the camera opens as usual.

Sidearm never changes Android's own press counting, so screen on/off and Emergency SOS behave exactly as stock. Details are in [docs/plan.md](docs/plan.md). The verified framework signatures for each Android build are in [docs/hook-targets.md](docs/hook-targets.md).

## Requirements

- Google Pixel 9 Pro XL (`komodo`) on Android 17. Builds whose hook targets have been verified are listed in [docs/hook-targets.md](docs/hook-targets.md).
- Root with Zygisk (tested with Magisk and Zygisk Next).
- [Vector](https://github.com/JingMatrix/Vector) 2.2 or newer (libxposed API 102).
- Double-press power set to **Camera** (Settings › System › Gestures).

## Safety and recovery

- If an Android update changes the code Sidearm hooks, Sidearm stays inactive and the stock double-press camera keeps working.
- Sidearm never touches the Emergency SOS gesture.
- To turn it off, disable Sidearm in the Vector manager and reboot.
- If the phone ever fails to boot, start Android in safe mode by holding Volume Down while the boot animation starts. Magisk disables all modules in safe mode.

## Building

You need Android Studio Rabbit 2 (2026.2.2) or newer (use its bundled JDK), and Android SDK platform 37 with build-tools 36.0.0.

```bash
git clone --recurse-submodules https://github.com/SoansSandha/sidearm.git
cd sidearm
bash scripts/setup-repo.sh        # turns on the repo's hygiene hooks
./gradlew :app:assembleDebug
```

For development on a rooted test phone:
- `bash scripts/dev-install.sh` builds and installs the module. Vector hot-reloads it without a reboot.
- `adb logcat -s Sidearm` shows what it does.

The first install needs Sidearm enabled in the Vector manager for "System Framework", followed by one reboot.

## Project docs

- [docs/plan.md](docs/plan.md): design, phases, testing, risks and recovery.
- [docs/hook-targets.md](docs/hook-targets.md): framework methods and fields Sidearm relies on, per Android build.
- [.claude/rules/git-and-privacy.md](.claude/rules/git-and-privacy.md): what may and may not be committed.
- [.claude/rules/project-structure.md](.claude/rules/project-structure.md): where code, docs and scripts go.

## License

Copyright (C) 2026 SoansSandha.

Sidearm is free software. You can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE), version 3 or (at your option) any later version (`GPL-3.0-or-later`). It comes with no warranty.

Third-party material in this repo keeps its own license. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
