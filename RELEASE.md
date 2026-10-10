# Stable release gate

A release is **not stable** merely because an APK compiles.

## Current assessment

Status: **NOT READY FOR STABLE DISTRIBUTION**

Orubus now integrates a prebuilt Chromium engine through Cefrium/CEF rather than Android System WebView. The SDK is pre-1.0/public beta and the new integration has not yet been validated by a successful CI build and supported-device test run. Named persistent profiles, Tor, WireGuard, automatic route enforcement and verified leak protection remain unimplemented.

## Mandatory release gate

- [ ] CI compile, lint and tests pass on the release commit.
- [ ] Bundled Chromium engine launches, navigates, downloads, plays media, handles certificate errors and survives renderer crashes on supported physical devices.
- [ ] A documented engine update cadence and security advisory review process is operational.
- [ ] Release keystore is backed up and signing certificate fingerprint is verified.
- [ ] Signed artifact installs and upgrades the prior stable build in place.
- [ ] Version code increases and package ID remains stable.
- [ ] No secrets, keystores or private browsing data are present in source/artifacts.
- [ ] Third-party licenses, LGPL compliance, Chromium/CEF notices and codec obligations are complete.
- [ ] Privacy notice matches actual data flows.
- [ ] Profile isolation and deletion are verified if marketed.
- [ ] VPN/Tor routing and DNS/IPv6/WebRTC leak behavior are verified if marketed.
- [ ] Security review and release notes are complete.
- [ ] Artifact checksum and CI provenance are preserved.

Do not mark unchecked requirements as passed. Release notes must distinguish implemented features from planned work.
