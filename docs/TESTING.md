# Testing strategy

## Required layers

- **Unit tests:** URL classification, filter rule matching, route policy decisions, profile metadata, retention policies and migration logic.
- **Android instrumented tests:** WebView lifecycle, navigation, downloads, app restart, profile storage boundaries and private-session cleanup.
- **Network integration tests:** tunnel establishment/loss, protected-route fail-closed behavior, DNS routing, IPv6, WebRTC, captive portals and network switching.
- **Security tests:** untrusted URI schemes, TLS error handling, JavaScript bridge exposure, storage separation, log redaction and release-signature verification.
- **Performance tests:** cold start, memory use, tab switching, page load responsiveness and background process recovery.
- **Release tests:** install, upgrade over previous stable, clean install, migration, signer continuity and checksum generation.

## CI expectations

Pull requests must compile and run available automated checks. Stable release jobs must require signing secrets and a stable tag. A green compile is not evidence that VPN/Tor routing, privacy protections, profile isolation or production readiness work.

## Test reporting

Every test report should include commit SHA, Android API level, device/emulator model, network conditions, test command, pass/fail totals and known exclusions. Record a skipped test as skipped, not passed.
