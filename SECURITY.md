# Security policy

## Current limitations

This is an early development build. Do not rely on it for anonymity, censorship resistance, or verified VPN leak protection. The current browser shell uses Android System WebView and a small host-based request filter. It does not currently implement VPN or Tor routing.

## Reporting vulnerabilities

Please report security issues privately to the repository maintainer rather than publishing exploit details in an issue.

## Security requirements before production

- Maintain a supported Chromium/WebView update strategy.
- Validate VPN tunnel failure behavior, DNS routing, IPv6 and WebRTC exposure.
- Test profile storage isolation and deletion.
- Review third-party dependency licenses and security advisories.
- Avoid logging browsing URLs or sensitive network metadata by default.
- Protect release signing keys outside source control.
