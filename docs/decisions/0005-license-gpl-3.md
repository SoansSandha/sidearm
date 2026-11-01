# 0005. License Sidearm under GPL-3.0-or-later

- Status: Accepted
- Date: 2026-10-31
- Files: [LICENSE](../../LICENSE), [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)

## Context

- The repo is public, so it needs a license before anyone can reuse or contribute to it.
- GPL-3.0 is the norm for LSPosed and Vector modules, and Vector itself is GPL-3.0.
- The repo vendors part of ECC (MIT) into `.claude/`, and depends on libxposed (Apache-2.0).

## Decision

- Our own code is `GPL-3.0-or-later`.
- Third-party material keeps its own license, with its notice in `THIRD_PARTY_NOTICES.md` in the same commit that adds it.
- Don't add code under a license that conflicts with GPL-3.0.

## Alternatives considered

| Option | Why not |
|---|---|
| MIT or Apache-2.0 | Lets forks ship closed builds of a module that runs in system_server. The module ecosystem expects GPL. |
| GPL-3.0-only | "Or later" leaves room for a future GPL version without asking every contributor. |

## Consequences

- Anyone who distributes a modified build has to publish its source under the same terms.
- MIT and Apache-2.0 material can be included, with its notice kept.
