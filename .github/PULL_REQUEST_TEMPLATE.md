## Summary

<!-- What changes and why. Link the plan section, ADR or issue if there is one. -->

## Test plan

<!-- How it was checked: unit tests, scripts/device-smoke.sh, rows of docs/testing.md. -->

## Pre-flight

- [ ] The title follows the commit format, `<type>(<scope>): <subject>`, in at most 72 characters ([CONTRIBUTING.md](../CONTRIBUTING.md)).
- [ ] Commits use your GitHub noreply email (GitHub › Settings › Emails); CI rejects other addresses.
- [ ] `./gradlew check` passes.
- [ ] `bash scripts/check-repo-hygiene.sh` passes, and the diff and this description hold no pasted logs, screenshots, or details of a particular phone or PC.
- [ ] Docs match the change: `docs/plan.md`, `docs/architecture.md`, `docs/testing.md`, `README.md`, and an entry under "Unreleased" in `CHANGELOG.md` for anything people installing the APK would notice.
- [ ] Changes to the hook (`:hook`, `:core`, or what Vector loads) were checked on a phone (`docs/testing.md`), including the Emergency SOS check. Injected presses never go past 3 in a row.
