# Stable release gate

A release is **not stable** merely because an APK compiles.

## Current assessment

Status: **NOT READY FOR STABLE DISTRIBUTION**

The project currently provides an early Android browser shell. Custom Chromium, isolated profiles, Tor, WireGuard, automatic route enforcement, comprehensive filter lists and verified leak protection remain unimplemented. The current signing process requires the maintainer to create and securely store a long-lived release key.

## Mandatory release gate

- [ ] CI compile, lint and tests pass on the release commit.
- [ ] Release keystore is backed up and signing certificate fingerprint is verified.
- [ ] Signed artifact installs and upgrades the prior stable build in place.
- [ ] Version code increases and package ID remains stable.
- [ ] No secrets, keystores or private browsing data are present in source/artifacts.
- [ ] Third-party licenses and notices are complete.
- [ ] Privacy notice matches actual data flows.
- [ ] Supported browser-engine update strategy is documented and operational.
- [ ] Profile isolation and deletion are verified if marketed.
- [ ] VPN/Tor routing and DNS/IPv6/WebRTC leak behavior are verified if marketed.
- [ ] Security review and release notes are complete.
- [ ] Artifact checksum and CI provenance are preserved.

Do not mark unchecked requirements as passed. Release notes must distinguish implemented features from planned work.
