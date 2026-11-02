#!/usr/bin/env bash
# Blocks secrets, personal or device-specific data, and forbidden files from entering git.
# Rules: .claude/rules/git-and-privacy.md
#
# Usage: scripts/check-repo-hygiene.sh                   # what's staged (run by .githooks/pre-commit)
#        scripts/check-repo-hygiene.sh <commit>          # one commit: files, author, committer, message
#                                                        #   (run by .githooks/pre-push for each commit)
#        scripts/check-repo-hygiene.sh --message <file>  # a commit message (run by .githooks/commit-msg)
#
# Findings print as location and rule only, never the matched text, so a leaked secret doesn't end
# up in terminal scrollback or CI logs. A missing private denylist is a failure unless
# HYGIENE_ALLOW_NO_DENYLIST=1 (for machines that aren't the owner's, e.g. CI).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SELF="scripts/check-repo-hygiene.sh"
DENYLIST=".claude/private/denylist.txt"
MAX_BYTES=$((1024 * 1024))
ALLOWED_BINARIES=("gradle/wrapper/gradle-wrapper.jar")
# Vendored ECC copies (synced verbatim from public upstream) quote fake secrets, sample IPs and
# placeholder emails, so "own"-scope rules skip them. The denylist and "all"-scope rules don't.
VENDORED=(":(exclude).claude/rules/ecc" ":(exclude).claude/agents" ":(exclude).claude/skills")

FORBIDDEN_NAME='(^|/)(local\.properties|keystore\.properties|signing\.properties|google-services\.json|settings\.local\.json|\.env(\..*)?|services\.jar|framework\.jar|[^/]*(bugreport|tombstone|anr_|logcat|dumpsys|getprop|screenshot|screen_record)[^/]*)$'
FORBIDDEN_EXT='\.(jks|keystore|p12|pfx|pem|key|pk8|apk|aab|apks|dex|odex|vdex|oat|art|smali|so|hprof|log|logcat|zip|tar|tgz|gz|7z|rar|bin|img|ab|png|jpe?g|webp|gif|heic|bmp|mp4|mov|webm|mkv|3gp|pdf|db|db-journal|sqlite3?|pcap(ng)?)$'

# --- Content rules: name, scope (own | all), case (i | -), allowed-match ERE ('' = none), ERE ---
R_NAME=() R_SCOPE=() R_CASE=() R_ALLOW=() R_PATTERN=()
rule() { R_NAME+=("$1"); R_SCOPE+=("$2"); R_CASE+=("$3"); R_ALLOW+=("$4"); R_PATTERN+=("$5"); }

# Allowances use [.] rather than \. because awk -v would treat the backslash as an escape.
rule private-key        own - '' '-----BEGIN [A-Z ]*PRIVATE KEY-----'
rule github-token       own - '' '(gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})'
rule aws-key            own - '' 'AKIA[0-9A-Z]{16}'
rule google-api-key     own - '' 'AIza[0-9A-Za-z_-]{35}'
rule slack-token        own - '' 'xox[abprs]-[A-Za-z0-9-]{10,}'
rule llm-api-key        own - '' 'sk-(ant-|proj-)?[A-Za-z0-9_-]{32,}'
rule credential-in-url  own - '' '[a-z]+://[^/[:space:]:@]+:[^/[:space:]@]+@'
rule hardcoded-secret   own i '' "(password|passwd|secret|api[_-]?key|access[_-]?token)[\"' ]*[:=][[:space:]]*[\"'][^\"' ]{6,}[\"']"
rule ip-address         own - '^(127[.]0[.]0[.]1|0[.]0[.]0[.]0|255[.]255[.]255[.]255)$' \
  '\b(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])(\.(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])){3}\b'
# support@github.com is Dependabot's sign-off ("Signed-off-by: dependabot[bot] <support@github.com>").
rule email-address      own i '(noreply|^support@github[.]com$|@example[.](com|org|net)$|@localhost$)' \
  '[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}'
rule raw-logcat-line    all - '' '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} +[0-9]+ +[0-9]+ [VDIWEF] '
rule raw-getprop-output all - '' '^\[(ro|persist|sys|gsm|vendor)\.[a-z0-9._]+\]: \['
rule mac-address        all - '' '\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b'
# Windows user paths, or Unix home paths that start a token (not "/users/" inside a URL).
rule user-home-path     all i '(^|[^a-z0-9._-])(user|username|you|yourname|runner|name|example|public|default|shared)$' \
  '([a-z]:[\\/]+users[\\/]+|(^|[[:space:]"=(:/])(/c/users/|/home/|/users/))[a-z0-9._-]{2,}'

# --- Target selection ---
MODE="staged" TARGET="" MSG_FILE="" prefix=""
case "${1:-}" in
  "") ;;
  --message) MODE="message"; MSG_FILE="${2:?usage: --message <file>}" ;;
  *)
    MODE="commit"; TARGET="$1"; prefix="$TARGET:"     # git grep prefixes matches with "<commit>:"
    git cat-file -e "$TARGET^{commit}" 2>/dev/null || { echo "Not a commit: $TARGET" >&2; exit 2; }
    ;;
esac

# --- Private denylist: plain strings, case-insensitive ---
DENY_TERMS=()
if [ -f "$DENYLIST" ]; then
  while IFS= read -r term || [ -n "$term" ]; do
    term="${term%$'\r'}"
    [[ "$term" =~ ^[[:space:]]*(#|$) ]] || DENY_TERMS+=(-e "$term")
  done < "$DENYLIST"
elif [ "${HYGIENE_ALLOW_NO_DENYLIST:-0}" != 1 ]; then
  echo "Repo hygiene check FAILED: $DENYLIST is missing, so personal strings can't be checked." >&2
  echo "Create it (see scripts/setup-repo.sh), or set HYGIENE_ALLOW_NO_DENYLIST=1 on a machine that isn't the owner's." >&2
  exit 1
fi

findings=0
report() { # report <rule>, reading "location[:line[:...]]" lines on stdin
  local rule="$1" line
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    line="${line#"$prefix"}"
    printf '  %-22s %s\n' "$rule" "$(cut -d: -f1-2 <<<"$line")"
    findings=$((findings + 1))
  done
}

# grep_tree <git grep options and patterns...> -- [pathspec excludes...]: searches the target's files.
grep_tree() {
  local opts=()
  while [ "$1" != "--" ]; do opts+=("$1"); shift; done
  shift
  if [ "$MODE" = commit ]; then
    git grep -I -n "${opts[@]}" "$TARGET" -- . ":(exclude)$SELF" "$@" || true
  else
    git grep --cached -I -n "${opts[@]}" -- . ":(exclude)$SELF" "$@" || true
  fi
}

# strip_allowed <ERE>: reads "path:line:match" lines, prints "path:line" unless the match is allowed.
strip_allowed() {
  sed "s|^${prefix}||" | awk -v allowed="$1" '{
    m = $0; sub(/^[^:]*:[^:]*:/, "", m)
    if (allowed == "" || tolower(m) !~ allowed) { split($0, f, ":"); print f[1] ":" f[2] }
  }' | sort -u
}

scan_tree() {
  local i opts excludes
  for i in "${!R_NAME[@]}"; do
    opts=(-o -E -e "${R_PATTERN[$i]}")
    [ "${R_CASE[$i]}" = i ] && opts=(-i "${opts[@]}")
    excludes=()
    [ "${R_SCOPE[$i]}" = own ] && excludes=("${VENDORED[@]}")
    report "${R_NAME[$i]}" < <(grep_tree "${opts[@]}" -- "${excludes[@]}" | strip_allowed "${R_ALLOW[$i]}")
  done
  if [ "${#DENY_TERMS[@]}" -gt 0 ]; then
    report "private-denylist" < <(grep_tree -i -F "${DENY_TERMS[@]}" --)
  fi
}

# scan_text <label> <text>: applies every content rule and the denylist to a message or name.
scan_text() {
  local label="$1" text="$2" i opts
  for i in "${!R_NAME[@]}"; do
    opts=(-o -E -e "${R_PATTERN[$i]}")
    [ "${R_CASE[$i]}" = i ] && opts=(-i "${opts[@]}")
    if grep "${opts[@]}" <<<"$text" \
      | awk -v allowed="${R_ALLOW[$i]}" 'allowed == "" || tolower($0) !~ allowed { found = 1 } END { exit !found }'; then
      report "${R_NAME[$i]}" <<<"$label:-"
    fi
  done
  if [ "${#DENY_TERMS[@]}" -gt 0 ] && grep -qiF "${DENY_TERMS[@]}" <<<"$text"; then
    report "private-denylist" <<<"$label:-"
  fi
}

# NUL-delimited "mode ...<TAB>path" records, so paths with spaces or quotes survive.
list_entries() {
  if [ "$MODE" = commit ]; then
    git -c core.quotepath=off ls-tree -r -z "$TARGET"
  else
    git -c core.quotepath=off ls-files -s -z
  fi
}

blob_size() {
  if [ "$MODE" = commit ]; then git cat-file -s "$TARGET:$1"; else git cat-file -s ":$1"; fi
}

# Every text file in the target (git grep -I skips binaries), NUL-delimited. Unlike grep_tree,
# this doesn't exclude this script, which would otherwise look like a binary.
list_text_files() {
  if [ "$MODE" = commit ]; then
    git grep -I -l -z -e '' "$TARGET" -- . || true
  else
    git grep --cached -I -l -z -e '' -- . || true
  fi
}

check_files() {
  local -A text_files=()
  local rec mode path size allowed name
  while IFS= read -r -d '' path; do
    text_files["${path#"$prefix"}"]=1
  done < <(list_text_files)

  while IFS= read -r -d '' rec; do
    mode="${rec%% *}"
    path="${rec#*$'\t'}"
    [ "$mode" = 160000 ] && continue                       # submodule pointer, not a file
    if grep -qiE "$FORBIDDEN_NAME|$FORBIDDEN_EXT" <<<"$path" || [[ "$path" == .claude/private/* ]]; then
      report "forbidden-file" <<<"$path:-"
    fi
    if [ "${#DENY_TERMS[@]}" -gt 0 ] && grep -qiF "${DENY_TERMS[@]}" <<<"$path"; then
      report "denylist-in-filename" <<<"$path:-"
    fi
    size="$(blob_size "$path")"
    [ "$size" -gt "$MAX_BYTES" ] && report "file-over-1MB" <<<"$path:-"
    if [ "$size" -gt 0 ] && [ -z "${text_files[$path]:-}" ]; then
      allowed=""
      for name in "${ALLOWED_BINARIES[@]}"; do [ "$path" = "$name" ] && allowed=1; done
      [ -n "$allowed" ] || report "binary-file" <<<"$path:-"
    fi
  done < <(list_entries)
}

check_commit_identity() {
  local short email
  short="commit $(git rev-parse --short "$TARGET")"
  for email in "$(git log -1 --format=%ae "$TARGET")" "$(git log -1 --format=%ce "$TARGET")"; do
    case "$email" in
      *@users.noreply.github.com | noreply@github.com) ;;
      *) report "non-noreply-identity" <<<"$short:-" ;;
    esac
  done
  scan_text "$short message" "$(git log -1 --format=%B "$TARGET")"
  scan_text "$short names" "$(git log -1 --format='%an%n%cn' "$TARGET")"
}

case "$MODE" in
  staged)  label="staged files"; check_files; scan_tree ;;
  commit)  label="commit $(git rev-parse --short "$TARGET")"; check_files; scan_tree; check_commit_identity ;;
  message) label="commit message"; scan_text "commit message" "$(grep -v '^#' "$MSG_FILE" || true)" ;;
esac

if [ "$findings" -gt 0 ]; then
  echo
  echo "Repo hygiene check FAILED for $label: $findings finding(s) above."
  echo "Fix them, or if one is a false positive, ask the owner before changing the patterns."
  echo "Rules: .claude/rules/git-and-privacy.md"
  exit 1
fi
echo "Repo hygiene check passed for $label."
