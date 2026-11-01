# 0001. Branching model: `develop` and `main`, with short-lived topic branches

- Status: Accepted
- Date: 2026-10-31
- How to work with it: [CONTRIBUTING.md](../../CONTRIBUTING.md)

## Context

- One maintainer works on Sidearm with Claude. Outside contributions become possible once the repo is public.
- Sidearm ships versioned APKs that people install into system_server. Every release needs a manual check on the phone, including Emergency SOS, which CI can't do.
- A monthly Pixel OTA can make a released version go inactive. A fix then has to ship from the released code, even while new work is half done.
- Until Phase 5 there is no CI, and the only GitHub ruleset keeps `develop` from being deleted, so local hooks do most of the enforcement.
- Until now, every commit went straight onto `main`.

## Decision

- `main` holds releases only and stays GitHub's default branch for now. `develop` is the integration branch.
- Work happens on short-lived topic branches off `develop`. They're named `<type>/<description>` using the Conventional Commits types, and squash-merged.
- `hotfix/…` branches come off `main`, merge into `main`, and are then merged back into `develop`.
- There are no `release/…` branches. Release prep happens on `develop`, which freezes while the release is tested on the phone.
- Everything that enters `main`, and every back-merge, uses a merge commit, never squash or rebase.
- Commit messages and PR titles follow Conventional Commits 1.0.0.
- Releases are annotated `vX.Y.Z` tags on `main`. Until 1.0.0, the minor version is the phase number.
- `scripts/check-conventions.sh` enforces this locally from the commit-msg and pre-push hooks: the message format, branch and tag names, no direct pushes to `main` and `develop`, and tags only on `main`. GitHub rulesets and CI add server-side enforcement once they're available.

## Alternatives considered

| Option | Why not |
|---|---|
| GitHub Flow: `main` plus topic branches | It's the usual advice for small teams that deploy continuously. Here, `main` would mix phone-tested releases with untested work, and hotfixing a release would mean branching from a tag. |
| Trunk-based development | Keeping the trunk releasable needs strong CI and feature flags. Sidearm's riskiest checks are manual and happen on a phone. |
| Full git-flow, with `release/…` branches | A release branch lets `develop` keep moving while a release is tested. With one maintainer nothing else is moving, so it would only add a back-merge to every release. |
| Squash or rebase into `main` | Both rewrite the commits, so `main` and `develop` diverge and every release PR conflicts. |

## Consequences

- `main` is always the latest release, and `git log --first-parent main` lists the releases and hotfixes.
- `develop` gets one commit per PR, which keeps a `git bisect` on the phone short.
- Each hotfix costs an extra back-merge PR.
- `develop` is frozen while a release is tested.
- Because `main` is the default branch, PRs need `develop` picked as their base, and `Fixes #N` closes an issue only when its release reaches `main`.
- Merged branches are deleted automatically. A ruleset that restricts deleting `develop` keeps it from being deleted when a release PR merges.
- Claude starts topic branches by itself with `scripts/start-branch.sh` when the worktree is clean. Every other branch operation, renames included, needs the owner's instruction (rules §1).
- Revisit if several people work in parallel and the release freeze gets in the way (add `release/…` branches), or if CI with device tests makes trunk-based development realistic.
