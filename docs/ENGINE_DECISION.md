# Browser engine decision record

Status: selected for integration validation, not yet approved for stable distribution.

## Decision

Use the prebuilt Cefrium SDK, which embeds Chromium through the Chromium Embedded Framework (CEF), rather than build Chromium from source or use Android System WebView. Pin the SDK version in Gradle and distribute the engine inside the Orubus app.

Pinned candidate: Cefrium SDK 0.9.4.

## Why this direction

- No Chromium source checkout or custom Chromium build pipeline is required.
- The engine is bundled, rather than delegated to the device's System WebView provider.
- The SDK exposes browser APIs needed for navigation, tabs, request filtering and off-the-record private tabs.
- The engine includes an EasyList-based network blocker.
- A fixed dependency version gives CI and testers a reproducible engine input.

## Material risks

The Cefrium project documents the SDK as pre-1.0/public beta. Its API may change between minor releases and it has not yet had a large production deployment. The engine is large and increases APK/AAB size. It requires a newer build toolchain and currently supports arm64-v8a devices and x86_64 emulators. Orubus must track Chromium/CEF security updates and cannot assume the SDK's release cadence is sufficient without monitoring.

The current dependency is not a production approval. The integration must pass CI and physical-device tests for navigation, media, downloads, TLS errors, renderer crashes, lifecycle, private-session cleanup, memory pressure and supported Android versions before a stable release.

## Alternatives considered

- Android System WebView: mature and security-updated by the device/provider, but does not meet the requirement to bundle a separately pinned Chromium engine.
- Chrome Custom Tabs: uses the installed browser and cannot provide the full in-app browser controls and request policy required by Orubus.
- Building Chromium from source: gives maximum control but requires substantial build infrastructure and ongoing engineering, which is not needed for this initial integration.
- Other embedded engines: GeckoView is a viable embeddable engine but uses Gecko, not Chromium.

## License and supply chain

Cefrium is documented as LGPL-3.0-or-later. Chromium and CEF include their own notices and third-party dependencies. The full-codec SDK variant may have additional codec patent/licensing considerations. Before distribution, verify the exact artifacts' license texts, include required notices, and satisfy applicable LGPL source/relinking requirements.

## Exit criteria

1. CI build succeeds with the pinned engine and the release pipeline.
2. Debug APK installs and runs on at least one arm64 physical device and an x86_64 emulator.
3. Engine behavior and security boundaries are tested and documented.
4. The app has an engine update monitoring and patch-release process.
5. License and redistribution review is complete.
6. Release readiness remains blocked until the above are complete.
