# Changelog

User-visible changes for each release are recorded here.

## Unreleased

- Replaced Android System WebView rendering with the bundled Chromium engine through Cefrium/CEF.
- Added engine-backed browser tabs and off-the-record private tabs.
- Replaced the small starter hostname filter with the engine-provided EasyList-based blocker.
- Updated debug and stable-release CI toolchains for the bundled engine.
- Added explicit engine maturity, license, security-update and release-readiness notes.
- Retained honest limitations for named profiles, Tor, WireGuard, automatic routing and leak protection.
- Added repository layout, stable signing, security, testing and contribution documentation.

## 0.1.0

- Initial Android browser shell.
- Basic address/search navigation and tabs.
- Small host-based request filter with an explicit limitations notice.
- Debug APK build workflow.

## Browser foundation milestone

- Added safe address-bar resolution: explicit HTTP(S) URLs pass through, host-like input defaults to HTTPS, and other input is searched with DuckDuckGo. Unsupported executable schemes are never passed to Chromium.
- Added persistent bookmarks, bounded normal browsing history, and restoration of normal-tab URLs. Private-tab URLs are excluded from browser-owned history and session restoration.
- Added back/forward/reload/home controls, pull-to-refresh, in-page find, page sharing, and confirmed downloads with progress and pause/resume/cancel controls.
- Added JVM unit tests for address-bar resolution and dangerous-scheme handling. GitHub Actions now passes the debug APK build and unit tests; physical-device validation remains outstanding.
