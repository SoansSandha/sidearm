#!/usr/bin/env bash
# Checks commit messages, branch names and tags against CONTRIBUTING.md, and keeps main and develop
# pull-request-only. Why this model: docs/decisions/0001-branching-model.md
#
# Usage: scripts/check-conventions.sh --message <file>   # a commit message (run by .githooks/commit-msg)
#        scripts/check-conventions.sh --branch <name>    # a branch name, e.g. before creating it
#        scripts/check-conventions.sh --push <remote> <remote-ref> <local-sha> <remote-sha>
#                                                        # one pushed ref (run by .githooks/pre-push)
set -euo pipefail

TYPES="feat|fix|docs|refactor|test|perf|build|ci|chore|revert"
HEADER_RE="^($TYPES)(\([a-z0-9]+(-[a-z0-9]+)*\))?!?: [^[:space:]]"
# Messages git writes itself: merges, `git revert`, and the markers `git rebase --autosquash` uses.
GIT_MADE_RE='^(Merge (branch|remote-tracking branch|pull request|tag) |Revert "|(fixup|squash|amend)! )'
TOPIC_RE="^($TYPES|hotfix|spike)/[a-z0-9]+([.-][a-z0-9]+)*$"
TAG_RE='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
ZERO_RE='^0+$'
MAX_HEADER=72
MAX_BRANCH=60

fail() { # fail <problem> [hint...]
  echo "Convention check FAILED: $1" >&2
  shift
  local hint
  for hint in "$@"; do echo "  $hint" >&2; done
  echo "Conventions: CONTRIBUTING.md" >&2
  exit 1
}

check_message() {
  local lines=() line header
  # git runs commit-msg before it strips comments, so skip them, and stop at the diff `commit -v` adds.
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    [ "$line" = "# ------------------------ >8 ------------------------" ] && break
    [[ "$line" == "#"* ]] && continue
    [ "${#lines[@]}" -eq 0 ] && [ -z "${line//[[:space:]]/}" ] && continue   # leading blank lines
    lines+=("$line")
  done < "$1"
  [ "${#lines[@]}" -gt 0 ] || return 0                 # empty: git aborts the commit itself

  header="${lines[0]}"
  [[ "$header" =~ $GIT_MADE_RE ]] && return 0
  [[ "$header" =~ $HEADER_RE ]] || fail "the first line isn't a Conventional Commits header." \
    "Write <type>(<scope>): <subject>, e.g. 'fix(core): ignore a timer from a cancelled hold'." \
    "Types: ${TYPES//|/, }. The scope is optional and lowercase, e.g. core, hook, app, targets."
  [ "${#header}" -le "$MAX_HEADER" ] || fail "the first line is ${#header} characters; keep it to $MAX_HEADER." \
    "Move the detail into the body."
  [[ "$header" != *. ]] || fail "the first line ends with a period; drop it."
  [ "${#lines[@]}" -lt 2 ] || [ -z "${lines[1]//[[:space:]]/}" ] || fail "the second line isn't blank." \
    "Leave one blank line between the header and the body."
}

check_branch() {
  local name="$1"
  case "$name" in main | develop | dependabot/*) return 0 ;; esac
  [[ "$name" =~ $TOPIC_RE ]] || fail "branch '$name' isn't <type>/<description>." \
    "Type: ${TYPES//|/, }, hotfix or spike. Description: lowercase words joined by '-' (or '.' in versions)." \
    "E.g. feat/camera-hold, fix/12-stale-timer, chore/release-v0.2.0."
  [ "${#name}" -le "$MAX_BRANCH" ] || fail "branch '$name' is ${#name} characters; keep it to $MAX_BRANCH."
}

check_push() {
  local remote="$1" ref="$2" local_sha="$3" remote_sha="$4" name
  case "$ref" in
    refs/heads/main | refs/heads/develop)
      # Creating the branch on a remote is the only direct push allowed; after that, pull requests only.
      [[ "$remote_sha" =~ $ZERO_RE && ! "$local_sha" =~ $ZERO_RE ]] && return 0
      fail "'${ref#refs/heads/}' only changes through pull requests on GitHub, never by a direct push." \
        "Push a topic branch and open a pull request (hotfix/… targets main, everything else develop)."
      ;;
    refs/heads/*)
      [[ "$local_sha" =~ $ZERO_RE ]] && return 0       # deleting a merged or abandoned topic branch
      check_branch "${ref#refs/heads/}"
      ;;
    refs/tags/*)
      name="${ref#refs/tags/}"
      [[ "$local_sha" =~ $ZERO_RE || ! "$remote_sha" =~ $ZERO_RE ]] \
        && fail "tag '$name' is already published; release tags are never moved or deleted." \
          "Fix a bad release with a new patch release instead."
      [[ "$name" =~ $TAG_RE ]] || fail "tag '$name' isn't a release tag; use vMAJOR.MINOR.PATCH, e.g. v0.2.0."
      [ "$(git cat-file -t "$local_sha")" = tag ] \
        || fail "tag '$name' is a lightweight tag; release tags are annotated (git tag -a)."
      git merge-base --is-ancestor "$local_sha" "refs/remotes/$remote/main" 2>/dev/null \
        || fail "tag '$name' isn't on $remote/main; release tags go on the merge commit of a release PR." \
          "Run 'git fetch $remote', then: git tag -a $name $remote/main -m \"Sidearm $name\""
      ;;
  esac
}

case "${1:-}" in
  --message) check_message "${2:?usage: --message <file>}" ;;
  --branch)  check_branch "${2:?usage: --branch <name>}"; echo "Branch name OK: $2" ;;
  --push)    check_push "${2:?usage: --push <remote> <remote-ref> <local-sha> <remote-sha>}" \
               "${3:?missing <remote-ref>}" "${4:?missing <local-sha>}" "${5:?missing <remote-sha>}" ;;
  *)         echo "usage: $0 --message <file> | --branch <name> | --push <remote> <ref> <local-sha> <remote-sha>" >&2
             exit 2 ;;
esac
