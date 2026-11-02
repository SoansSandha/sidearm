#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version, for the GitHub Release's notes. Fails if the
# section is missing or empty, so a release can't go out without notes.
#
# Usage: scripts/release-notes.sh <version> [changelog]   # version without the "v", e.g. 0.5.0;
#                                                         #   changelog defaults to the repo's CHANGELOG.md
set -euo pipefail

version="${1:?usage: scripts/release-notes.sh <version> [changelog]}"
changelog="${2:-}"
if [ -z "$changelog" ]; then
  cd "$(git rev-parse --show-toplevel)"
  changelog=CHANGELOG.md
fi

# The section runs from "## [<version>]" to the next "## [" heading or the link references at the end.
notes="$(awk -v heading="## [$version]" '
  index($0, heading) == 1 { found = 1; next }
  found && (/^## \[/ || /^\[[^]]+\]: /) { exit }
  found { print }
' "$changelog" | sed -e '/./,$!d')"   # drop leading blank lines

if [ -z "${notes//[[:space:]]/}" ]; then
  echo "CHANGELOG.md has no notes for $version. Move the Unreleased entries under \"## [$version] - <date>\"." >&2
  exit 1
fi
printf '%s\n' "$notes"
