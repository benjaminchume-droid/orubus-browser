# Security model

## Assets to protect

- Release signing identity and release credentials.
- Cookies, authentication state, browsing history, downloads and profile data.
- User-selected routing policy and tunnel credentials/configuration.
- DNS queries, destination metadata and network traffic.
- Browser process integrity and the ability to receive timely Chromium security updates.

## Trust boundaries

1. Web content is untrusted. Do not expose privileged JavaScript bridges to arbitrary pages.
2. The app embeds Chromium through the Cefrium/CEF SDK. Unlike Android System WebView, the engine is packaged with the app. The app must deliberately update the SDK to receive newer engine releases.
3. Cefrium is currently documented as a pre-1.0/public-beta SDK. API changes and limited large-scale production deployment are release risks; pin versions and validate each update on supported devices.
4. Filter lists and remote configuration are untrusted inputs. Validate size, format, source, update freshness and failure behavior.
5. Tor is integrated as a SOCKS transport with a Tor-exit check; WireGuard is not implemented. Neither a connected tunnel nor a successful exit check proves all browser traffic classes are leak-free. Show observed state, not a user's desired state.
6. GitHub Actions and repository write permissions can affect release integrity. Stable signing secrets must be available only to the release job/environment.
7. A profile selector is not an isolation boundary by itself. Storage partitioning and deletion require tests.

## Required invariants

- A protected route must fail closed when its tunnel is unavailable. Never silently route protected traffic directly.
- The UI distinguishes configured, connecting, connected, degraded and failed route states based on real observations.
- Do not log full URLs, search queries, cookies, authorization headers or tunnel secrets by default.
- HTTPS certificate errors must not be bypassed automatically.
- External URI schemes are denied unless a reviewed, explicit handler exists.
- Release secrets and keystores are never written to build logs or repository files.
- Privacy features must be backed by reproducible tests and must not claim anonymity solely because Tor/VPN is enabled.

## Threats and mitigations

| Threat | Required mitigation |
| --- | --- |
| Malicious web content | Chromium security updates, strict bridge policy, safe URI handling |
| Delayed engine security patch | Track upstream releases, pin dependencies, test and ship updates on a defined cadence |
| Tracker/ad request leakage | Maintained rule lists, request matching tests, transparent limitations |
| Profile cross-contamination | Separate storage contexts/process strategy and automated isolation tests |
| VPN crash or route loss | OS-level VPN lifecycle, kill-switch/fail-closed design, leak tests |
| DNS/IPv6/WebRTC leaks | Device-level integration tests under each supported network condition |
| Signing key theft | Offline encrypted backups, protected CI environment, least privilege |
| Malicious dependency/update | Pinned reviewed dependencies, advisory checks, artifact provenance |
| Sensitive local data recovery | Clear retention defaults, explicit deletion semantics, storage encryption where applicable |

## Known gaps

The bundled Chromium engine is integrated in source but has not yet been validated by a successful CI build and supported-device testing. Named persistent profile isolation, WireGuard, complete network-layer route enforcement, Chromium proxy-fallback verification, and DNS/IPv6/WebRTC leak testing are not implemented. Tor transport is integrated but has not yet passed CI and supported-device verification. Do not use the app for high-risk anonymity or claim production privacy guarantees until those systems exist and pass tests.
