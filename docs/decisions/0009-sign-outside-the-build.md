# 0009. Sign releases only in a hand-run workflow on main, in a job that runs no build code

- Status: Accepted
- Date: 2026-11-01
- How it's built: `.github/workflows/release.yml`, `build.yml` and `scripts/sign-apk.sh`; the owner's steps are in [CONTRIBUTING.md](../../CONTRIBUTING.md#release-signing)

## Context

- Sidearm's code runs inside system_server. Whoever holds the release key can publish an APK that installs over Sidearm as an update and runs with system_server's power on every phone that has it. The key is the most valuable secret this project has.
- A Gradle build runs a lot of code that isn't ours: Gradle itself, AGP, the Kotlin compiler, Spotless, Kover, their dependencies, and anything a pull request changes in the build scripts. Any of it can read environment variables and files in the job that runs it.
- A workflow run takes its workflow file from the commit it runs on. A run triggered by pushing a tag uses the tagged commit's workflow, so whoever can push a tag on any commit decides what the job holding the key runs. Checks inside that workflow can't prevent that, because they're part of the file being replaced.
- The repo rules already say release APKs are built in CI from secrets, not on the owner's PC, so they carry no local paths or debug signature (`.claude/rules/git-and-privacy.md` §9).
- The repo is public. Pull requests from forks must never reach secrets, and its artifacts are visible to any signed-in GitHub user.

## Decision

- Only `release.yml` signs, and it has one trigger: `workflow_dispatch`, run by hand from `main` for an existing tag. Its jobs run `main`'s workflow and scripts, and read the tag's files with `git show` as data. It refuses to run from any other branch.
- The signing secrets live in a GitHub environment named `release` whose deployment rule allows only the `main` branch: `SIDEARM_KEYSTORE_BASE64` (a PKCS12 keystore), `SIDEARM_KEYSTORE_PASSWORD`, and `SIDEARM_KEY_ALIAS`. They're not repository secrets.
- The build job (`build.yml`) builds and checks the tag's code unsigned, with no secrets and no Gradle cache, and uploads the APK as a short-lived artifact.
- A separate job downloads that APK and signs it with `apksigner` from pinned build-tools, through `scripts/sign-apk.sh`. It runs no Gradle and no build scripts. The password reaches `apksigner` through the environment, never the command line, and the job prints only the certificate's subject and SHA-256 digest. It then checks the APK's package name and version before publishing it with its SHA-256 and the certificate digest in the notes.
- A run with "publish" off stops after signing and keeps the APK as a 3-day artifact, so a release can be tried on a phone before it's published.
- CI on pull requests and on pushes to `develop` and `main` never signs.
- The owner creates the keystore and enters the secrets, so their values never pass through anyone else; the keystore and its password are kept in the owner's password manager.

## Alternatives considered

| Option | Why not |
|---|---|
| Publish when a `v*` tag is pushed, with the environment open to `v*` tags | A tag on any commit would run that commit's workflow with the key; the tag checks can be deleted in the same commit. |
| Also sign every push to `main` | It exposes the key to a job on every release merge and leaves untagged signed APKs downloadable for weeks, for little gain over the "publish" off run. |
| A Gradle `signingConfig` read from environment variables | One fewer job, but the key and password sit in the environment of the whole build, which runs all the third-party code above. |
| Repository secrets instead of an environment | Any workflow run on any branch in the repo could read them. |
| Signing on the owner's PC | Against the repo rules: the APK would depend on one PC's state, and the key would sit on a machine used for everything else. |
| A third-party signing action | Another dependency handed the key, for a job that `apksigner` does in one command. |

## Consequences

- Releasing takes one more step: after pushing the tag, run the Release workflow from `main` (CONTRIBUTING.md, Release).
- Local release builds (`./gradlew :app:assembleRelease`) are unsigned. To try a release build on a phone, use a "publish" off run.
- The release key and debug builds sign differently, so moving a phone between a release APK and `scripts/dev-install.sh` builds means uninstalling first. The config and Vector's settings for Sidearm go with it: re-enable the module, reboot, and choose the app again.
- If a release run fails, the fix lands on `main` and the run is repeated for the same tag, since tags can't be moved.
- If the key is lost, installed copies can't be updated in place; users would have to uninstall. If it leaks, rotate it and say so in the next release's notes. Either way, the password manager copy is what matters.
- Until the `release` environment and its secrets exist, a release run fails at signing and says which secret is missing.
