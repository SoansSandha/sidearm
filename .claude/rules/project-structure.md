# Project structure rules

These rules load in every conversation. They say where things go, so the repo stays clean as phases add code. The target layout and the phase in which each part arrives are in `docs/plan.md` §4. To deviate, update both files first, with the owner's OK.

## Modules and their boundaries

| Module | Holds | May depend on | Must not |
|---|---|---|---|
| `:core` | Pure Kotlin. The press sequencer (state, events, effects, `reduce`) and the config contract (keys, defaults, validation) | Kotlin stdlib only | Android, Xposed, coroutines, I/O |
| `:hook` | Everything that runs inside system_server: the entry class, interceptors, framework targets, the effect runner, the remote-config reader | `:core`, and `compileOnly` libxposed api | AndroidX, Compose, coroutines, or any library (each one gets loaded into system_server). Must never reference `:app`. |
| `:app` | The APK: packaging, `META-INF/xposed/*`, the config UI, the XposedService client | `:core`, `:hook` as `runtimeOnly` (packaged but not importable), libxposed service, AndroidX/Compose | Importing `:hook` classes, or containing hook logic |
| `build-logic/` (from Phase 1) | Convention plugins shared by the modules | Gradle, AGP and KGP APIs | Product code |

- The config contract lives only in `:core`. The app writes through it and the hook reads through it. Never write a pref key string anywhere else.
- Add a module only when a real boundary needs one, and update plan §4 first. One expected case: a `compileOnly` `:hiddenapi` stub module, if the reflection targets outgrow `hook/target/`.
- Not used here, by decision: feature or `:core:*` sub-modules, Hilt or other DI frameworks (use constructor injection), product flavors, instrumented tests.

## Packages

Every package starts with `io.github.soanssandha.sidearm`.

| Module | Package | Contents |
|---|---|---|
| core | `.core.sequencer` | `State`, `Event`, `Effect`, `reduce()` |
| core | `.core.config` | `Config`, `ConfigKeys`, `ConfigCodec` |
| hook | `.hook` | The `SidearmModule` entry class only |
| hook | `.hook.intercept` | One `Hooker` per hooked framework method |
| hook | `.hook.target` | `*Targets` classes: reflection handles per framework class, resolved once, all-or-nothing |
| hook | `.hook.effect` | Runs reducer effects: timers, app launch, wake, keyguard |
| hook | `.hook.config` | Reads remote prefs into a `core.config.Config` |
| app | (root) | `Application` and `Activity` |
| app | `.ui` | Screens, components, theme |
| app | `.data` | XposedService client, launcher-app listing, config writer |

The Phase 0 probe (`hook/SidearmModule.kt`, `hook/GestureLauncherTargets.kt`) is exempt until Phase 2 replaces it.

## Files

- One public top-level type per file, named after the type.
- Tests live in `src/test/kotlin` under the same package as the code they test, named `<Type>Test.kt`. Test fixtures go in a `fixture` sub-package.
- Once Phase 1 adds convention plugins, module build files only apply them and set `namespace` and module-specific dependencies.
- Every version (libraries, plugins, SDK levels) lives in `gradle/libs.versions.toml`. Never put a version literal in a build file.
- `META-INF/xposed/*` lives only in `app/src/main/resources/`. App strings go in `app/src/main/res/values/strings.xml`. Icons are vector drawables only; the hygiene check blocks raster images.

## Where everything else goes

| What | Where |
|---|---|
| Roadmap and status | `docs/plan.md` |
| How the system works now | `docs/architecture.md` (from Phase 2) |
| Why a decision was made | `docs/decisions/NNNN-short-title.md`: an ADR with context, decision and consequences. To change one, write a new ADR that supersedes it; never rewrite an accepted one. |
| Verified framework signatures per build | `docs/hook-targets.md` |
| Device test matrix, SOS procedure, post-OTA checklist | `docs/testing.md` (from Phase 3) |
| Install, requirements, safety and recovery for users | `README.md` |
| Release notes | `CHANGELOG.md` (from Phase 5, in Keep a Changelog format) |
| Dev and repo scripts | `scripts/*.sh`: bash, kebab-case names, a header comment with usage |
| Git hooks | `.githooks/` |
| CI, dependency updates, PR template | `.github/` (from Phase 5) |
| Claude config | `.claude/`: project rules outside `ecc/`, personal notes in `private/` |
| Third-party tooling | `third_party/`, as pinned submodules |
| Throwaway output (decompiled jars, logs, test repos) | The session scratchpad, never the repo |

Don't add a top-level directory without updating plan §4 and this file.

## Naming and versions

- Gradle modules are short lowercase words. Convention plugin ids are `sidearm.android.application`, `sidearm.android.library` and `sidearm.jvm.library`.
- Branches come off `main` as `feat/…`, `fix/…`, `docs/…` or `chore/…`. Tags are `vX.Y.Z`. Commits use the conventional-commit format.
- The release `versionName` (semver) is defined in one place, and the release `versionCode` is derived from it. Dev installs override `versionCode` through `scripts/dev-install.sh`.

## One-command verification

From Phase 1, `./gradlew check` runs formatting (Spotless + ktlint), lint, unit tests and the Kover gate. Before any commit, also run `bash scripts/check-repo-hygiene.sh`.
