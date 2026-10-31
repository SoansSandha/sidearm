#!/usr/bin/env bash
# One-time setup for a fresh clone: turns on the hygiene hooks, then checks the commit identity and
# the private denylist. Safe to re-run. Rules: .claude/rules/git-and-privacy.md
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
status=0

git config core.hooksPath .githooks
echo "Hooks enabled: pre-commit, commit-msg and pre-push run scripts/check-repo-hygiene.sh."

email="$(git config --local user.email || true)"
if [[ "$email" != *@users.noreply.github.com ]]; then
  echo "Set this repo's commit email to your GitHub noreply address (Settings > Emails on GitHub):" >&2
  echo '  git config user.email "<id>+<login>@users.noreply.github.com"' >&2
  status=1
fi

if [ ! -f .claude/private/denylist.txt ]; then
  echo "Create .claude/private/denylist.txt (git-ignored): one personal string per line, such as" >&2
  echo "your phone's serial number, personal and work emails, carrier, and your Windows user path." >&2
  status=1
fi

exit "$status"
