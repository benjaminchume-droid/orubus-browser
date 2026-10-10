# Architecture and implementation status

Status labels are evidence-based. A planned component is not considered implemented merely because its name appears in the UI or documentation.

## Current implementation

- [x] Android application and Gradle configuration
- [x] GitHub Actions debug APK workflow updated for the bundled engine toolchain
- [x] Bundled Chromium engine integrated through the Cefrium/CEF Android SDK
- [x] Address/search navigation and browser tabs using the bundled engine
- [x] Engine-provided EasyList ad/tracker blocking and per-tab blocked-request counts
- [x] Off-the-record private tabs through the engine's incognito request context
- [x] Honest privacy status for features that remain unimplemented
- [x] Stable release signing configuration and release workflow scaffold
- [ ] Successful CI build and real-device validation of the new engine integration
- [ ] Named persistent profiles with independently verified storage isolation
- [ ] Profile-specific permissions, download state, bookmarks, history and deletion
- [ ] Tor transport integration
- [ ] WireGuard integration
- [ ] Routing policy engine and automatic route enforcement
- [ ] Fail-closed behavior across tunnel start, crash, network switch and recovery
- [ ] DNS, IPv6, WebRTC and tunnel-failure leak testing
- [ ] Filtering audit, list-update pipeline and regression test corpus
- [ ] Downloads manager, bookmarks, persistent history and session restore
- [ ] Production security review, supported-device matrix and release verification

## Engine strategy

Orubus uses a prebuilt Chromium engine distributed by Cefrium, which embeds Chromium through CEF. We are not building Chromium from source. The engine is pinned as a Gradle dependency so CI can package it with the app.

**Release risk:** Cefrium's public documentation currently identifies the SDK as pre-1.0/public beta, with a changing API and limited large-scale production deployment. The engine migration is implemented in source but must not be considered verified until CI and supported physical devices pass. Pinning a dependency does not itself guarantee timely security updates. Track Chromium/CEF upstream security releases, update the SDK deliberately, and test each engine upgrade before release.

The engine increases app size and requires a modern Android toolchain. Current SDK requirements are JDK 25, Gradle 9.7.1, Android Gradle Plugin 9.4, compile SDK 37, and Android API 29 or newer, following the SDK quickstart.

## Routing design target

Represent routing as a policy and an observed connection state, not a UI toggle. A protected session must fail closed when its selected tunnel is unavailable. Direct fallback must never happen silently. Each backend will expose capabilities, lifecycle state, health checks, and diagnostics.

## Profile design target

Private tabs use the engine's off-the-record context. That is not equivalent to named persistent profiles. Named profiles must own independently verified storage contexts. Tests must cover cookies, DOM storage, IndexedDB, service workers, cache, permissions, history, downloads, and data deletion. Normal tabs are not profiles.

## Filtering design target

The bundled engine currently provides an EasyList-based network blocker. That is a real improvement over the initial small hard-coded hostname set, but it does not establish comprehensive tracker, cosmetic, anti-fingerprinting, or DNS-level protection. Verify the engine's filtering behavior, rule-list update cadence, blocked-request semantics and false-positive handling before making broader claims.
