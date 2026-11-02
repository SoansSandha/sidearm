# Changelog

Notable changes to Sidearm, one section per release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Until 1.0.0, the minor version is the development phase ([CONTRIBUTING.md](CONTRIBUTING.md#versions-and-tags)).

Each release's section becomes the notes of its GitHub Release, so write entries for people installing the APK.

## [Unreleased]

### Added

- Triple-press the power button to open an app of your choice. From an unlocked phone the camera shows for about a tenth of a second first. From a locked phone the unlock prompt (fingerprint, face or PIN) shows first, and the app opens once you unlock.
- The Sidearm app: turn triple press on and off, choose the app, and see whether everything it needs is in place (Vector, its scope, the module loaded in System Framework, double-press set to Camera, and an Android build it has been tested on). Changes apply at once, with no reboot.
- Signed release APKs, built and published by GitHub Actions, with a SHA-256 checksum and the signing certificate's digest in the release notes.

### Known issues

- On an unlocked triple press, the camera from the second press sometimes comes up over the chosen app.

## [0.1.0] - 2026-10-31

### Added

- The press sequencer and its config, as a tested library. Not a working module yet, and not published as an APK.

[Unreleased]: https://github.com/SoansSandha/sidearm/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/SoansSandha/sidearm/tree/v0.1.0
