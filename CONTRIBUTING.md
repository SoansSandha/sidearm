# Contributing

How changes move through this repo: branches, commit messages, pull requests, releases, and what to do in each situation that comes up. It applies to everyone, Claude included. The privacy and permission rules in [.claude/rules/git-and-privacy.md](.claude/rules/git-and-privacy.md) apply on top of it. Why this model was chosen: [ADR 0001](docs/decisions/0001-branching-model.md).

## The model

```
             v0.1.0                        v0.2.0     v0.2.1
main     -------o-----------------------------o----------o--------
                 \                           / \        / \
                  \             release PR  /   hotfix/…   \ back-merge
develop  ----------o----o----o----o--------o----------------o---
                        ^    ^    ^
                   topic branches off develop, each squash-merged as one commit
```

| Branch | Branches from | Merges into | Merge method | Lifetime |
|---|---|---|---|---|
| `main` | | | | Permanent. Released code only; every release is an annotated `vX.Y.Z` tag on it. GitHub's default branch. |
| `develop` | `main` | `main`, through a release PR | Merge commit | Permanent. The integration branch; `./gradlew check` always passes on it. A ruleset stops it from being deleted, including by auto-delete after a release PR. |
| Topic: `feat/…`, `fix/…`, `docs/…` and the other commit types | `develop` | `develop` | Squash | Days, not weeks. Deleted after merging. |
| `hotfix/…` | `main` | `main`, then `main` is merged back into `develop` | Merge commit | Until the patch release is tagged |
| `spike/…` | `develop` | Never | | A throwaway experiment, like the Phase 0 probe. Write up the findings through a `docs/` branch, then delete it. |

Nobody commits to `main` or `develop` directly. They change only through pull requests: the pre-push hook refuses direct pushes to them, and so do GitHub's rulesets.

## Branch names

`<type>/<description>`, for example `feat/camera-hold`.

- **type** is one of the commit types below, or `hotfix` or `spike`. Use the type the change will land as.
- **description** is 2–5 lowercase words joined by hyphens (dots are fine inside versions: `chore/release-v0.2.0`). It says what changes, not who is doing it. An issue number can lead: `fix/12-stale-timer`. The whole name is at most 60 characters.
- No personal or device details (rules §3). Public Android build IDs are fine.
- `scripts/start-branch.sh` checks the name. To check one by hand: `bash scripts/check-conventions.sh --branch feat/camera-hold`.

## Starting and growing a branch

Every new piece of work starts from the latest `develop`:

```bash
bash scripts/start-branch.sh feat/camera-hold
```

The script stops unless the worktree is clean. It then fetches, fast-forwards `develop` to `origin/develop`, and creates the branch, refusing if local `develop` has drifted from origin. By hand, the same thing is `git switch develop && git pull --ff-only && git switch -c feat/camera-hold`.

- Name the branch after what the task is about to do. More work on the same task stays on its branch, and a task that changes no files needs no branch.
- Claude runs the script on its own when a new task begins and the worktree is clean. If there are uncommitted changes, it asks first (rules §1).
- GitHub deletes merged branches, but local copies stay. When the branch you're leaving is already on `develop`, the script says so and gives the `git branch -D` command; deleting it is still the owner's call.

If the work grows past the branch's name, for example a fix that turned into a feature or a second related change, rename the branch so the name still describes it. If the new part could ship on its own, a separate branch is usually better, since each PR covers one concern. Claude proposes the new name or the split and waits for the owner's OK before renaming.

| Branch state | How to rename |
|---|---|
| Not pushed | `git branch -m <new-name>` |
| Pushed, no PR yet | `git branch -m <new-name>`, `git push -u origin <new-name>`, then `git push origin --delete <old-name>` |
| PR open | Don't rename it: GitHub closes a PR when its branch is renamed. Update the PR title instead; with squash merges, the title is what lands on `develop`. |

## Commit messages

[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<scope>)!: <subject>

<body>

<footers>
```

| Type | For | Example | In release notes |
|---|---|---|---|
| `feat` | New or changed behaviour a user can notice | `feat(hook): hold the camera launch for one press window` | Yes |
| `fix` | A bug fix | `fix(core): ignore a timer from a cancelled hold` | Yes |
| `perf` | Faster or lighter, same behaviour | `perf(hook): resolve reflection handles once at boot` | Yes |
| `revert` | Undoing an earlier commit | `revert: feat(hook): hold the camera launch` | Yes |
| `refactor` | A code change with no behaviour change | `refactor(hook): move interceptors into intercept/` | No |
| `test` | Tests only | `test(core): cover a press at exactly 300 ms` | No |
| `docs` | Documentation only | `docs(targets): add the November QPR1 build` | No |
| `build` | Gradle, `build-logic/`, the version catalog, dependencies | `build(deps): bump AGP to 9.6.0` | No |
| `ci` | GitHub Actions (Phase 5) | `ci: run the hygiene check on pull requests` | No |
| `chore` | Repo tooling and housekeeping: scripts, git hooks, `.claude/`, ECC, release prep | `chore(release): prepare v0.2.0` | No |

The scope is optional. Leave it out when a change spans several areas.

| Scope | Covers |
|---|---|
| `core` | `:core`: the press sequencer and the config contract |
| `hook` | `:hook`: everything that runs in system_server |
| `app` | `:app`: packaging, `META-INF/xposed`, the config UI |
| `targets` | Support for an Android build: `docs/hook-targets.md` and the target resolvers |
| `deps` | Dependency and toolchain versions |
| `repo` | Git hooks, `scripts/`, `.claude/`, ECC |
| `release` | The version bump and changelog for a release |

- **Header:** at most 72 characters, no full stop at the end, and one blank line before the body.
- **Subject:** imperative and lowercase ("add", "fix", "hold", not "Added" or "Adds"). Say what changes, not which file changed.
- **Body** (optional, wrapped at 72): why the change is needed, and anything a reviewer can't see in the diff. For `:hook` changes, say how it was checked on the phone, for example "1, 2 and 3 presses, plus the manual SOS check with the phone locked". Quote trimmed log lines only (rules §3).
- **Breaking changes:** put `!` before the colon and add a `BREAKING CHANGE: <what breaks and what to do>` footer. For Sidearm that means a config change that loses saved settings, a raised requirement (Android build, Vector or libxposed API), or removed behaviour.
- **Footers:** `Fixes #12` closes the issue once the change reaches `main`, the default branch, which means in the release that ships it. `Refs: #12` links it without closing it. Credit co-authors with `Co-Authored-By: Name <noreply address>`.
- **Topic-branch commits** can be as small as you like, but each still needs a valid header. Only the pull request title lands on `develop`, because topic branches are squash-merged.

The commit-msg hook checks the header's shape and length, the full stop, and the blank line. Messages git writes itself (`Merge …`, `Revert "…"`, `fixup! …`) pass.

## Pull requests

1. Before opening one, `./gradlew check` and `bash scripts/check-repo-hygiene.sh` pass. The docs the change affects are updated in the same branch (`docs/plan.md` status, `docs/hook-targets.md`, `README.md`).
2. Target `develop`. GitHub suggests `main`, the default branch, so change the base (`gh pr create --base develop`). Only release PRs (from `develop`) and `hotfix/…` PRs target `main`.
3. The title becomes the squash commit's header, so it follows the commit format; GitHub adds ` (#N)`. The description says what and why, how it was tested (for `:hook` changes: on the phone, including the manual SOS check), and links the issue. The privacy rules apply to titles, descriptions and comments too.
4. One concern per PR. A phase usually takes several; Phase 2, for example, splits into target resolvers, dry-run interceptors, and the camera hold.
5. Merge with the method below. When GitHub offers a choice of commit email, pick the noreply address.

| Pull request | Merge method | Why |
|---|---|---|
| Topic → `develop` | Squash and merge | One reviewed, buildable commit per change on `develop`. Work-in-progress commits stay on the branch. |
| `develop` → `main` (release) | Create a merge commit | Squashing or rebasing rewrites the commits, so `main` and `develop` would diverge and every later release PR would conflict. |
| `hotfix/…` → `main` | Create a merge commit | Same reason. `main`'s first-parent history stays one entry per release or hotfix. |
| Back-merge into `develop` | Create a merge commit | Brings a hotfix into `develop` without duplicating it. |

To bring a branch up to date with `develop`: if it has never been pushed, rebase it (`git rebase develop`). Once it's pushed, merge `develop` into it instead. Force-pushing is denied in this repo, and the squash merge flattens the extra merge commit anyway.

## Versions and tags

- Versions are SemVer `MAJOR.MINOR.PATCH`, set in one place: `sidearm` in `gradle/libs.versions.toml`. The release `versionCode` is derived from it as `MAJOR × 10000 + MINOR × 100 + PATCH` (0.5.0 is 500), so minor and patch stay below 100. `scripts/dev-install.sh` overrides it for dev installs.
- Until 1.0.0, the minor version marks the phase: `v0.1.0` is Phase 1 (the core), `v0.2.0` Phase 2, and so on to `v0.5.0`, the first published release (Phase 5). Patch versions are hotfixes. `v1.0.0` follows the Phase 7 soak.
- From 1.0.0: a breaking change bumps major, `feat` bumps minor, and `fix` or `perf` bumps patch.
- A release is an annotated `vX.Y.Z` tag on the merge commit of its PR into `main`. Tags are never moved or deleted; a bad release is fixed by the next patch release. The pre-push hook checks that a tag has the right format, is annotated, and is on `main`.
- After the tag is pushed, the Release workflow (`.github/workflows/release.yml`), run by hand from `main`, publishes it. It checks that the tag is annotated, on `main`, matches `sidearm`, and has a `CHANGELOG.md` section; builds and checks the tag's code; signs the APK; checks its package and version; and publishes the GitHub Release with the section as its notes, the APK, its SHA-256 and the signing certificate's digest. While the version is 0.x the release is marked as a pre-release. Pushing a tag alone never signs anything ([ADR 0009](docs/decisions/0009-sign-outside-the-build.md)).

## Playbook

| Situation | Branch from | Name | PR into | Notes |
|---|---|---|---|---|
| A feature, or the next piece of a phase | `develop` | `feat/<what>` | `develop` | Split a phase into PR-sized branches. |
| A bug in unreleased code | `develop` | `fix/<what>` | `develop` | Add a test that fails without the fix (`:core`), or record the phone check (`:hook`). |
| A bug in a release people have installed | `main` | `hotfix/<what>` | `main` | See [Hotfix](#hotfix). |
| A new Android build, hook targets unchanged | `develop` | `docs/targets-<build>` | `develop` | Add the build to `docs/hook-targets.md`. |
| A new Android build breaks the hooks (Sidearm goes inactive) | `main` once a release exists, else `develop` | `hotfix/targets-<build>`, or `fix/targets-<build>` before the first release | `main` or `develop` | Verify the new signatures with `dexdump`, use scope `targets`, and run the full phone check including SOS. |
| Support a new Android version or QPR | `develop` | `feat/android-<version>` | `develop` | Ships in the next minor release. |
| A release or phase milestone | `develop` | `chore/release-v<x.y.z>` for the prep | `develop`, then `develop` → `main` | See [Release](#release). |
| Documentation | `develop` | `docs/<what>` | `develop` | If the mistake could hurt people using a release (install, safety or recovery steps), use the hotfix flow without a version bump or tag. |
| A refactor | `develop` | `refactor/<what>` | `develop` | No behaviour change. Tests pass before and after. |
| A dependency or toolchain bump | `develop` | `build/<what>`, or Dependabot's own branch | `develop` | `build(deps): …`. Anything loaded into system_server (libxposed) or a change to the Vector requirement also needs the phone check. |
| An ECC update | `develop` | `chore/ecc-<version>` | `develop` | Review the upstream diff, then run `scripts/sync-ecc.sh`. Update `THIRD_PARTY_NOTICES.md` if the license changed. |
| An experiment or probe | `develop` | `spike/<what>` | Never merged | Write up the findings through a `docs/` branch, then delete the spike. |
| Undo a merged change | `develop` | `revert/<what>` | `develop` | `git revert <sha>`. If the change is already released, use the hotfix flow. |
| A breaking change, such as renaming a config key | `develop` | `feat/<what>` | `develop` | `!` and a `BREAKING CHANGE:` footer. Migrate old values in `ConfigCodec` where possible. |
| A security problem in a release | `main` | `hotfix/<neutral name>` | `main` | Don't describe the problem in branch names, commits or PRs until the fixed release is out. Reporters use GitHub's private vulnerability reporting. |
| The work outgrew the branch name | | | | Propose a rename or a split; rename only with the owner's OK. See [Starting and growing a branch](#starting-and-growing-a-branch). |
| A branch has fallen behind `develop` | | | | Rebase if it was never pushed; otherwise merge `develop` into it. |
| Work that needs an unmerged branch | | | | Better: merge the first branch, then branch from `develop`. If you must stack, branch from the first one and use it as the PR base. GitHub retargets the PR to `develop` when the first branch is deleted; then merge `develop` in. |
| Abandoned work | | | | Close the PR and delete the branch. |
| An outside contribution | the contributor's fork | any | `develop` | Review it before building or running it (rules §9). If it was opened against `main`, change its base to `develop`. Fix the PR title to the commit format before squash-merging; their authorship is kept. |

### Release

1. Prepare on `chore/release-v<x.y.z>` from `develop`: set `sidearm` in `gradle/libs.versions.toml`, move the "Unreleased" entries in `CHANGELOG.md` under `## [<x.y.z>] - <date>` (and update the links at the bottom), and update the status in `docs/plan.md` and `README.md`. Squash-merge it into `develop`.
2. Freeze `develop` (merge nothing else) and test its build on the phone: the device matrix and the manual SOS check (`docs/plan.md` §6; `docs/testing.md` from Phase 3). Fix problems through `fix/…` PRs into `develop`, then test again.
3. Open a PR from `develop` into `main` titled `chore(release): v<x.y.z>`, and merge it with a merge commit.
4. Tag it: `git fetch origin`, then `git tag -a v<x.y.z> origin/main -m "Sidearm v<x.y.z>"` and `git push origin v<x.y.z>`.
5. Try the signed build: run the Release workflow from `main` with the tag and "publish" off (Actions › Release › Run workflow, or `gh workflow run release.yml --ref main -f tag=v<x.y.z> -f publish=false`). Install its artifact on the phone and run the Emergency SOS check. Switching from a debug build means uninstalling it first (README, Building).
6. Publish: run it again with "publish" on. Check the Release page. If a run fails, fix the workflow or scripts through a hotfix into `main`, then run it again for the same tag; tags can't be moved.

### Hotfix

1. Branch `hotfix/<what>` from `main`, which is the released code, not `develop`.
2. Fix the problem with a test or a recorded phone check. Bump the patch version in `gradle/libs.versions.toml`, and add its `CHANGELOG.md` section.
3. Open a PR into `main` and merge it with a merge commit. Then fetch, tag `v<x.y.z>` on `origin/main`, push the tag, and run the Release workflow, as in a release.
4. Back-merge: on `chore/back-merge-v<x.y.z>` from `develop`, run `git merge origin/main` and resolve any conflict (for the version line, keep the higher version). Push it and merge its PR into `develop` with a merge commit.

### Release signing

Only the Release workflow signs, run by hand from `main`, in a job that runs no build code, with secrets that only `main` can reach ([ADR 0009](docs/decisions/0009-sign-outside-the-build.md)). The owner sets this up once, before the first release, so the key and password never pass through anyone else:

1. Create the keystore outside the repo with the JDK's `keytool`. It asks for a password: use a long random one from your password manager.
   ```powershell
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore sidearm-release.p12 -storetype PKCS12 `
     -alias sidearm -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Sidearm"
   ```
2. On GitHub, Settings › Environments › New environment, named `release`:
   - Under "Deployment branches and tags", choose "Selected branches and tags" and add only the branch `main`. No tag patterns.
   - Optionally, under "Required reviewers", add yourself and leave "Prevent self-review" off. Each release run then waits for your click before it can read the key, so a stolen token can't use it silently.
3. Add the environment's three secrets, from PowerShell in the keystore's folder (`gh` asks for the password; clear the clipboard after pasting it):
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\sidearm-release.p12")) | gh secret set SIDEARM_KEYSTORE_BASE64 --env release --repo SoansSandha/sidearm
   gh secret set SIDEARM_KEYSTORE_PASSWORD --env release --repo SoansSandha/sidearm
   gh secret set SIDEARM_KEY_ALIAS --env release --body sidearm --repo SoansSandha/sidearm
   ```
4. Keep the `.p12` file and its password in the password manager, ideally with a second offline copy, then delete the local file. Without them, installed copies can't be updated in place.

Every release's notes show the certificate's SHA-256 digest. After the first release, add it to `README.md` too, so people can check an APK against a copy that isn't on the Release page.

## What enforces this

| Rule | Enforced by |
|---|---|
| Commit header format | commit-msg hook → `scripts/check-conventions.sh` |
| Branch and tag names; no direct pushes to `main` or `develop`; tags annotated and on `main` | pre-push hook → `scripts/check-conventions.sh` |
| No secrets or personal data | `scripts/check-repo-hygiene.sh`, from all three hooks. GitHub's secret scanning and push protection are a backstop for secrets. |
| `main` and `develop` change only through PRs, with the merge methods above, and can't be force-pushed or deleted (so auto-delete skips `develop`) | The "Protect main" and "Protect develop" rulesets on GitHub (rules §9) |
| Release tags can't be moved or deleted | The "Protect release tags" ruleset |
| Only squash and merge commits on the merge button | Repo settings (rebase merging is off) |
| PR title and branch name; the hygiene check on the title and description | CI: `.github/workflows/pr.yml` |
| The hygiene check on every new commit, the Gradle wrapper jar, formatting, lint, unit tests, the coverage gate and the release build, on every PR and every push to `develop` and `main` | CI: `.github/workflows/ci.yml` and `build.yml` |
| Releases are signed only from `main`, for annotated tags on `main` that match the version and have changelog notes | `.github/workflows/release.yml`, and the `release` environment's deployment rule |

Turn the hooks on once per clone with `bash scripts/setup-repo.sh`.
