#!/usr/bin/env bash
# Starts a new piece of work: checks that the worktree is clean, brings develop up to date with
# origin, and creates a topic branch off it. Claude runs this without asking when a new task begins
# (.claude/rules/git-and-privacy.md §1); every other branch operation still needs the owner's OK.
#
# Usage: scripts/start-branch.sh <type>/<description>   # e.g. feat/camera-hold (CONTRIBUTING.md)
set -euo pipefail

name="${1:?usage: scripts/start-branch.sh <type>/<description>}"
cd "$(git rev-parse --show-toplevel)"

stop() { echo "No branch created: $1" >&2; exit 1; }

case "$name" in
  main | develop | hotfix/* | dependabot/*)
    stop "'$name' isn't a topic branch off develop. Hotfixes branch from main; ask the owner." ;;
esac
bash scripts/check-conventions.sh --branch "$name" >/dev/null || stop "see the convention error above."
[ -z "$(git status --porcelain)" ] || stop "the worktree has changes. Commit, stash or discard them first (ask the owner)."
if git show-ref --verify --quiet "refs/heads/$name"; then stop "branch '$name' already exists."; fi

git fetch --quiet --prune origin || stop "couldn't fetch from origin."
git show-ref --verify --quiet refs/remotes/origin/develop || stop "origin/develop doesn't exist yet; push develop first."
if git show-ref --verify --quiet "refs/remotes/origin/$name"; then stop "'$name' already exists on origin."; fi

previous="$(git branch --show-current)"
git switch --quiet develop
git merge --quiet --ff-only origin/develop || true
# develop only changes through pull requests, so after the fast-forward it must match origin exactly.
[ "$(git rev-parse develop)" = "$(git rev-parse origin/develop)" ] \
  || stop "local develop has commits that aren't on origin/develop (you're now on develop). Ask the owner."
git switch --quiet -c "$name"

echo "Created $name from develop at $(git log -1 --format='%h %s')."
if [ -n "$previous" ] && [ -n "$(git rev-list "$previous" --not --remotes)" ]; then
  echo "Note: $previous has commits that aren't pushed yet. They stay on that branch."
fi
