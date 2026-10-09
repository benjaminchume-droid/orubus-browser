# Architecture and implementation status

Status labels are evidence-based. A planned component is not considered implemented merely because its name appears in the UI or documentation.

## Current bootstrap

- [x] Android application skeleton and Gradle configuration
- [x] GitHub Actions debug APK build workflow
- [x] Native address/search bar and basic navigation
- [x] Multiple tabs in the initial browser shell
- [x] Basic host-based request blocking for the bundled starter domain set
- [x] Privacy status that explicitly describes current limitations
- [ ] Custom Chromium source build and engine integration
- [ ] Comprehensive filter-list engine and cosmetic filtering
- [ ] True isolated browser profiles
- [ ] Private/ephemeral profile lifecycle and verified data clearing
- [ ] WireGuard integration
- [ ] OpenVPN/provider integration
- [ ] Tor integration
- [ ] Routing policy engine and automatic routing
- [ ] DNS, IPv6, WebRTC and tunnel-failure leak testing
- [ ] Downloads manager, bookmarks, persistent history and session restore
- [ ] Production test suite, release signing and security update pipeline

## Routing design target

Represent routing as a policy and an observed connection state, not a UI toggle. A protected session must fail closed when its selected tunnel is unavailable. Direct fallback must never happen silently. Each backend will expose capabilities, lifecycle state, health checks, and diagnostics.

## Profile design target

A profile must own a distinct browser storage context. At minimum, isolation work must consider cookies, DOM storage, IndexedDB, service workers, cache, permissions, history, downloads, and password data. Tabs are not profiles. Profile switching must not be presented as isolation until verified by tests.

## Filtering design target

Filtering will use real request interception and a maintainable ruleset pipeline. The current starter host set is deliberately small and does not claim full EasyList/uBlock compatibility. Counters are incremented only when a request is actually blocked by the current filter.

## Chromium strategy

The bootstrap currently uses Android System WebView, which is based on Chromium but is updated independently by the device. It is not a custom bundled Chromium build. The custom Chromium build pipeline is a major separate engineering milestone because of source size, build resources, security patch cadence, and APK packaging constraints.
