# Tor transport integration

## Implementation

Orubus embeds the Guardian Project Android Tor daemon (info.guardianproject:tor-android) and its control library. It does not rely on a separately installed Orbot app.

- The app writes an app-private Tor configuration with SOCKSPort 127.0.0.1:39050, SafeSocks 1, and ClientOnly 1. The SOCKS listener binds to loopback only.
- The app starts and binds to the Tor service and observes its service status and error broadcasts.
- Before reporting a verified Tor route, the controller checks Tor's status/circuit-established control value, checks that Tor reports the configured SOCKS port, performs a SOCKS5 no-auth handshake, and requests the Tor Project endpoint https://check.torproject.org/api/ip through a manually negotiated SOCKS5 connection. The SOCKS CONNECT request carries the hostname, avoiding a local DNS lookup for this verification request. The response must contain IsTor: true.
- Each Cefrium browser context receives an explicit fixed SOCKS5 proxy configuration before pages are resumed.
- On activation, open pages are blanked and their web URLs held in memory. Navigation remains blocked until the transport passes verification.
- If verification fails or a later periodic exit check fails, open pages are blanked and navigation remains blocked. The app does not deliberately switch to Direct.
- URLs visited while Tor is requested are excluded from browser-owned history and session restoration.
- Disabling Tor is an explicit user action. The app blanks pages, clears proxy configuration for all open browser contexts, and only then resumes their URLs directly. If any context cannot clear the proxy, the app restores the proxy on all contexts and does not resume pages.

## What the state means

- **Off / Direct:** Tor is not requested; browser contexts use their normal direct route.
- **Connecting:** the app has configured the SOCKS proxy and is waiting for a verified Tor exit request.
- **Exit verified:** the Tor control state, local SOCKS endpoint, TLS request, and Tor Project exit-check response passed at the latest check.
- **Failed:** the transport did not verify or the periodic exit check failed; the app blanks pages and blocks address-bar navigation.

The exit check is repeated every 30 seconds while connected. A failed check may reflect Tor failure, captive portals, a blocked Tor-check endpoint, or temporary network loss. The app intentionally prefers blocking over opening a direct route.

## Important limitations

This integration is an actual Tor client and SOCKS route, but it is not a system-wide VPN. The current implementation has not yet proven that Chromium never falls back to direct networking after a proxy error, nor has it validated every browser subresource, WebRTC, DNS, IPv6, QUIC/UDP, or auxiliary engine request. The UI therefore must not be interpreted as proof that every packet from the device is protected. The separate routing-policy and fail-closed phase must enforce and test these cases, ideally with network-level controls.

Tor service and browser behavior still require CI verification and supported physical-device testing. Network access to the Tor Project check endpoint is required for the verified state. A failed check leaves navigation blocked; users can retry or explicitly switch back to Direct.

## Test plan before release

1. Start Tor on ARM64 and supported emulator/device variants; verify the controller reports Exit verified only after the Tor check response is received.
2. Block Tor bootstrap and verify the state reaches Failed and browser pages stay blank.
3. Kill the Tor service after a successful connection; verify the periodic check detects failure and no URL is resumed directly.
4. Disable the network, enable airplane mode, switch Wi-Fi/mobile data, and restore connectivity; verify state transitions and recovery.
5. Compare browser-observed egress with the Tor Project check page and inspect DNS, IPv6, WebRTC, QUIC and direct fallback with packet capture or controlled test endpoints.
6. Test tabs created during Connecting, Failed, and Exit verified; test explicit Direct transition and repeated start/stop.
7. Review Tor/CEF licenses and upstream security updates before a public release.
