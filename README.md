# Orubus Browser

A privacy-focused Android browser built around a bundled Chromium engine, browser profiles, network routing, and transparent privacy controls.

## Project status

The app now integrates a prebuilt Chromium engine through Cefrium/CEF. It does not build Chromium from source and no longer uses Android System WebView for page rendering. The browser shell has address/search navigation, back/forward/reload/home controls, tabs, pull-to-refresh, in-page find, page sharing, engine-provided EasyList blocking, and private tabs backed by the engine's off-the-record context. Bookmarks and normal browsing history are stored locally; normal-tab URLs can be restored after restart. Private-tab URLs are excluded from browser-owned history and session restore. Downloads use the engine download API with user confirmation and in-session pause/resume/cancel controls.

The repository integrates the Guardian Project Tor Android daemon and a Tor control-state/SOCKS5 handshake verifier. The Tor control blanks existing pages, applies Chromium's fixed SOCKS5 proxy to each browser context, waits for an established Tor circuit, and blocks address-bar navigation when the transport is not verified. Tor browsing URLs are excluded from the app's own history and session restore. The Tor-enabled APK has passed GitHub Actions build and JVM tests. Physical-device validation and browser proxy-leak testing remain outstanding. Cefrium is currently documented as a public-beta, pre-1.0 SDK, so Orubus must treat it as a candidate engine under validation rather than claim production readiness. See the engine decision in docs/ENGINE_DECISION.md and implementation status in ARCHITECTURE.md.

## Principles

- Real behavior only: no simulated VPN status, fake block counts, or controls that imply unimplemented security.
- Explicit routing policies and fail-closed behavior for protected sessions.
- Private tabs and named persistent profiles are distinct concepts.
- Privacy claims require reproducible tests, not just a UI indicator.
- Security updates and signed, repeatable CI builds are product requirements.

## Build

GitHub Actions builds the Android debug APK. The current bundled engine toolchain requires JDK 25, Gradle 9.7.1, Android Gradle Plugin 9.4, compile SDK 37, and minSdk 29. See the Android workflow.

## Current limitations

WireGuard is now integrated in source using the official WireGuard Android tunnel library. Users can import a provider configuration, which is validated for IPv4 and IPv6 default routes and encrypted with Android Keystore, then grant Android VPN permission and connect/disconnect the userspace tunnel. The WireGuard-enabled APK build is currently being validated in CI. Automatic route selection, OS-level VPN lockdown enforcement, named persistent profile isolation, and DNS/IPv6/WebRTC leak verification remain incomplete. Tor's browser proxy is not equivalent to a system-wide tunnel; Chromium proxy fallback and non-HTTP traffic must be tested before making a leak-protection claim. Do not use development builds as a guarantee of anonymity or leak protection.

## Third-party engine

The app uses the Cefrium SDK, which is LGPL-3.0-or-later; Chromium and CEF have their own licenses and notices. Review LICENSE-NOTICE.md before redistributing the app.
