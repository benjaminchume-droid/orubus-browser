# Security model

## Assets to protect

- Release signing identity and release credentials.
- Cookies, authentication state, browsing history, downloads and profile data.
- User-selected routing policy and tunnel credentials/configuration.
- DNS queries, destination metadata and network traffic.
- Browser process integrity and the ability to receive security updates.

## Trust boundaries

1. Web content is untrusted. Do not expose privileged JavaScript bridges to arbitrary pages.
2. Android System WebView is a device-provided Chromium-based engine. Its version and patch level depend on the device/provider; the current project is not a custom Chromium build.
3. Filter lists and remote configuration are untrusted inputs. Validate size, format, source, update freshness and failure behavior.
4. VPN and Tor components are separate transport systems, not anonymity guarantees. Show verified state, not a user's desired state.
5. GitHub Actions and repository write permissions can affect release integrity. Stable signing secrets must be available only to the release job/environment.
6. A profile selector is not an isolation boundary by itself. Storage partitioning and deletion require tests.

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
| Malicious web content | Chromium/WebView patch strategy, strict bridge policy, safe URI handling |
| Tracker/ad request leakage | Maintained rule lists, request matching tests, transparent limitations |
| Profile cross-contamination | Separate storage contexts/process strategy and automated isolation tests |
| VPN crash or route loss | OS-level VPN lifecycle, kill-switch/fail-closed design, leak tests |
| DNS/IPv6/WebRTC leaks | Device-level integration tests under each supported network condition |
| Signing key theft | Offline encrypted backups, protected CI environment, least privilege |
| Malicious dependency/update | Pinned reviewed dependencies, advisory checks, artifact provenance |
| Sensitive local data recovery | Clear retention defaults, explicit deletion semantics, storage encryption where applicable |

## Known gaps

The current implementation does not yet provide a custom Chromium build, true isolated profiles, Tor, WireGuard, a route enforcement engine, comprehensive ad/tracker filtering or verified leak protection. Do not use it for high-risk anonymity or claim production privacy guarantees until those systems exist and are tested.
