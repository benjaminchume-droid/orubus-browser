# WireGuard integration

## Implemented

Orubus embeds the official WireGuard Android tunnel library (userspace WireGuard backend). The app can accept a provider-generated WireGuard configuration, validate it, store it encrypted in app-private storage, request Android's VPN consent, and bring the tunnel up or down through the library backend.

- Configuration parsing uses the WireGuard library's Config.parse implementation. Invalid configs are rejected before storage.
- Routing policy requires at least one peer and IPv4 plus IPv6 default routes (0.0.0.0/0 and ::/0).
- IncludedApplications and ExcludedApplications routing directives are rejected to avoid accidental app-level bypass.
- Configuration bytes are encrypted using AES-GCM with a 256-bit key managed by Android Keystore. The plaintext config is not written to a regular file.
- The tunnel uses Android's VpnService consent flow. The library contributes its GoBackend.VpnService through Android manifest merging.
- UI states are based on backend lifecycle callbacks, not a simulated connection indicator.
- The Tor browser proxy and WireGuard system VPN can be enabled together. With a full-device WireGuard config, Tor daemon traffic is expected to traverse the WireGuard tunnel; this composition still needs device-level route verification.

## User setup

1. Obtain a WireGuard .conf from a VPN provider or your own WireGuard server. Orubus does not provide a VPN service, server, keys, or endpoint.
2. In Orubus, tap WireGuard: Setup and paste the configuration.
3. Save and approve Android's VPN connection prompt.
4. Verify that the provider endpoint is reachable and that the observed public IP changes as expected.
5. For OS-level lockdown, open Android Settings and enable Always-on VPN for Orubus Browser and Block connections without VPN where the OS exposes that option.

Never share a WireGuard config publicly: it contains a private key.

## Explicit limitations

- The app cannot enable Android's lockdown option on the user's behalf. Without Android lockdown, traffic can fall back to the regular network if the tunnel stops or is revoked.
- There is no bundled VPN subscription or server infrastructure. A valid peer config is required.
- The current UI accepts pasted configuration text; QR import, file-picker import, multiple named tunnel profiles, automatic reconnect policy, and tunnel statistics UI are not implemented.
- Tor and WireGuard have independent controls. The UI does not yet implement a general routing-policy engine for Tor-only, VPN-only, Tor-over-VPN, or direct sessions.
- Build success is not a substitute for physical-device testing. Validate start/stop, app process recreation, Wi-Fi/mobile handover, captive portals, DNS behavior, IPv4 and IPv6 routing, and observed exit IPs before production claims.
- Do not claim the tunnel is leak-proof until those tests pass on supported Android devices.

## Library

The tunnel library is published by WireGuard under Apache-2.0. See the official WireGuard Android repository and Maven Central artifact com.wireguard.android:tunnel for upstream version and notices.