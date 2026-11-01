# Sidearm (triple-press launcher)

LSPosed-style module for the Google Pixel 9 Pro XL: triple-pressing the power button launches a user-chosen app. The app is named Sidearm, with applicationId `io.github.soanssandha.sidearm`.

Status: Phases 0 and 1 are done. A log-only probe verified the hook targets on Android 17 QPR1 (2026-10-29), and the `:core` press sequencer and config contract are built and fully tested (2026-10-30). Phase 2 (hook glue: dry run, then camera) is built and reviewed (2026-10-31, branch `feat/hook-glue`). On the phone the dry run passed. Live runs showed that holding the camera locks a phone that was unlocked, so ADR 0007 holds only when the phone was locked at press 1. With that, unlocked doubles and triples work live (2026-10-31). The owner's own live presses and a manual SOS check are next, then Phase 3 (unlock-then-open). The plan is in `docs/plan.md`, how the built system works is in `docs/architecture.md`, and the verified hook targets for each build are in `docs/hook-targets.md`.

## Repo rules (read before any git action)

`.claude/rules/git-and-privacy.md` loads in every conversation. In short:
- Never commit, push, open PRs, or write to GitHub without the owner's explicit instruction naming that action. After making changes, stage them and stop.
- Never commit secrets, or anything specific to the owner's phone or PC. Personal notes go in `.claude/private/` (git-ignored).
- Before asking to commit or push, run `bash scripts/check-repo-hygiene.sh` and the pre-flight in §6 of the rules.
- Where code, docs and scripts go, and what may depend on what: `.claude/rules/project-structure.md` (also loaded every conversation).
- Branches, commits, PRs and releases follow `CONTRIBUTING.md`.
  - When a new task begins and the worktree is clean, run `bash scripts/start-branch.sh <type>/<description>` without asking. It updates `develop` from origin and branches off it.
  - If the work outgrows the branch name, propose a rename and wait for the owner's OK.
  - Commit messages follow Conventional Commits. Topic branches are squash-merged into `develop`, and `develop` reaches `main` only through a release PR. Never commit on `main` or `develop`.

After a fresh clone, run `bash scripts/setup-repo.sh` once. It turns on the hygiene hooks (pre-commit, commit-msg, pre-push) and tells you if the commit email isn't the GitHub noreply address, or if `.claude/private/denylist.txt` is missing.

## Target platform

- Pixel 9 Pro XL (`komodo`), Android 17 (SDK 37). The builds whose hook targets have been verified are listed in `docs/hook-targets.md`.
- Requires root with Zygisk (tested with Magisk + Zygisk Next) and Vector 2.2+ (JingMatrix's successor to LSPosed, libxposed API 102). Sidearm's scope is System Framework (`system`). After the first install, enable it in the Vector manager and reboot.
- adb root isn't needed for the dev loop: install, hot reload, logs and `adb reboot` all work without it. With adb root, Vector's CLI (`/data/adb/modules/zygisk_vector/cli`) can also enable modules and set scopes.

## This machine

Notes about the dev phone and PC live outside git and are imported here when present:

@.claude/private/environment.md

## Dev environment

- Android Studio Rabbit 2 (2026.2.2) or newer is required for AGP 9.5. Use its bundled JBR (JDK 25) as `JAVA_HOME`. build-tools 36.0.0 includes `dexdump`, which is used to inspect a build's framework jars (see `docs/hook-targets.md`).
- On Windows, PowerShell 5.1 mangles `adb shell` commands that contain double quotes or pipes, so run those from Bash.
- In Git Bash, prefix adb commands that take a device path (`adb pull /system/...`) with `MSYS_NO_PATHCONV=1`. Without it the path is rewritten to `C:/Program Files/Git/...`.

## Build and dev loop

- Build: `./gradlew :app:assembleDebug`. Versions are pinned in `gradle/libs.versions.toml`: AGP 9.5.0, Kotlin 2.4.21, Gradle 9.8.2, with the wrapper checksum-pinned. AGP 9 has built-in Kotlin, so never apply `org.jetbrains.kotlin.android`.
- Verify: `./gradlew check` runs Spotless (ktlint), lint on `:app` and `:hook`, the unit tests (`:core`, and `:hook` on JUnit 5 with fixture stand-ins for the framework classes), and the Kover gate on `:core`. `./gradlew spotlessApply` fixes formatting. Lint doesn't track `local.properties`, so after editing that file rerun lint with `./gradlew :app:lintAnalyzeDebug --rerun`.
- Modules: `:core` is pure Kotlin (the press sequencer in `core.sequencer`, the config contract in `core.config`). `:hook` holds the code that runs in system_server; `libxposed:api` is `compileOnly`. `:app` packages the APK and holds `META-INF/xposed/*`. Shared build settings live in the `build-logic/` convention plugins.
- Install a hook change: `bash scripts/dev-install.sh`. It passes a fresh versionCode, because Vector hot-reloads the module in system_server only when versionCode changes. No reboot is needed unless `onSystemServerStarting` itself changes.
- Logs: `adb logcat -s Sidearm SidearmDev` (the hook, then the dev tool). Vector routes module `log()` calls to logcat under the module's tag. Boot-time lines rotate out of the buffer within about a minute. Presses are only logged while the feature is armed.
- Set the config (debug builds, until the Phase 4 app): `adb shell am start -n io.github.soanssandha.sidearm/.DevConfigActivity --ez enabled true --es target_component <pkg>/<activity> --ez dry_run true`. Extras are the `:core` config keys; omitted ones keep their value; no extras reports the stored config. The activity is invisible: it logs the result under `SidearmDev`, shows a short toast and finishes. Run it from Bash, not PowerShell. Find a launcher activity with `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <pkg>`.
- Injecting presses: `adb shell input keyevent --delay 150 KEYCODE_POWER KEYCODE_POWER KEYCODE_POWER` (shows up as `device=-1`). **Never inject 4 or more presses**, because 5 triggers Emergency SOS when it's enabled.

## Tooling: ECC

[ECC](https://github.com/affaan-m/ECC) is vendored as a shallow git submodule at `third_party/ecc`, pinned to `91b5f6a` (v2.2.3). `scripts/sync-ecc.sh` copies an Android/Kotlin/Java subset of it into `.claude/`. The lists of rules, agents, and skills are at the top of that script.

- Rules: `.claude/rules/ecc/{common,kotlin,java}`. The kotlin and java rules load only for `*.kt`, `*.kts`, and `*.java` files.
- Agents (bare names, no `ecc:` prefix): planner, architect, tdd-guide, code-reviewer, security-reviewer, build-error-resolver, kotlin-reviewer, kotlin-build-resolver, java-reviewer, java-build-resolver.
- Skills: android-clean-architecture, kotlin-patterns, kotlin-coroutines-flows, kotlin-testing, java-coding-standards, coding-standards, error-handling, security-review, git-workflow, verification-loop.
- Not installed: ECC hooks (they need Node.js), ECC MCP configs, and the remaining agents and skills. The common rules mention some agents that aren't here (e2e-runner, refactor-cleaner, doc-updater, and others). Ignore those references.

`sync-ecc.sh` overwrites the ECC rules, agents, and skills, so don't edit them by hand. Put project-specific rules in `.claude/rules/`, outside `ecc/`.

- Update ECC: `git submodule update --remote third_party/ecc && bash scripts/sync-ecc.sh`
- Fresh clone: `git clone --recurse-submodules <url>`, or run `git submodule update --init` after cloning.
