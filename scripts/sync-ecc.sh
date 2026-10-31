#!/usr/bin/env bash
# Copies the curated subset of ECC (third_party/ecc) into .claude/ for this project.
# Edit the lists below to add or remove ECC pieces, then re-run:
#   bash scripts/sync-ecc.sh
# To update ECC itself first:
#   git submodule update --remote third_party/ecc
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ECC="$ROOT/third_party/ecc"
DEST="$ROOT/.claude"

RULES=(common kotlin java)
AGENTS=(
  planner architect tdd-guide code-reviewer security-reviewer build-error-resolver
  kotlin-reviewer kotlin-build-resolver java-reviewer java-build-resolver
)
SKILLS=(
  android-clean-architecture kotlin-patterns kotlin-coroutines-flows kotlin-testing
  java-coding-standards coding-standards error-handling security-review
  git-workflow verification-loop
)

[ -d "$ECC/rules" ] || { echo "ECC submodule missing; run: git submodule update --init" >&2; exit 1; }

mkdir -p "$DEST/rules" "$DEST/agents" "$DEST/skills"
rm -rf "$DEST/rules/ecc"
mkdir -p "$DEST/rules/ecc"
for r in "${RULES[@]}"; do cp -R "$ECC/rules/$r" "$DEST/rules/ecc/"; done
for a in "${AGENTS[@]}"; do cp "$ECC/agents/$a.md" "$DEST/agents/"; done
for s in "${SKILLS[@]}"; do rm -rf "$DEST/skills/$s"; cp -R "$ECC/skills/$s" "$DEST/skills/"; done

echo "Synced ECC $(git -C "$ECC" rev-parse --short HEAD) into .claude/ (${#RULES[@]} rule packs, ${#AGENTS[@]} agents, ${#SKILLS[@]} skills)"
