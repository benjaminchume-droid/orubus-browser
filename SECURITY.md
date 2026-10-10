# Security policy

## Current limitations

Orubus is an early development build and is not ready for stable distribution. It embeds Chromium through the Cefrium/CEF SDK instead of Android System WebView. Cefrium is currently a pre-1.0/public-beta SDK, so engine updates and behavior must be validated before releases. Do not rely on Orubus for anonymity, censorship resistance, or verified VPN leak protection.

The engine provides an EasyList-based network blocker and an off-the-record context for private tabs. Named persistent profiles, Tor, WireGuard, automatic route enforcement, and verified DNS/IPv6/WebRTC leak protection are not implemented.

## Reporting vulnerabilities

Please report security issues privately to the repository maintainer rather than publishing exploit details in an issue.

## Security requirements before production

- Track Chromium/CEF security releases and validate each pinned SDK update.
- Review the Cefrium/CEF/Chromium licenses, notices, codecs and redistribution obligations.
- Test browser process lifecycle, renderer crashes, certificate errors and untrusted URL schemes.
- Validate tunnel failure behavior, DNS routing, IPv6 and WebRTC exposure before advertising VPN/Tor features.
- Test profile storage isolation and deletion.
- Avoid logging browsing URLs or sensitive network metadata by default.
- Protect release signing keys outside source control.
- Maintain a supported Android/device matrix and repeat regression tests for every engine update.
