# Orubus Browser privacy notes

## Current behavior

Orubus uses a bundled Chromium engine through Cefrium/CEF rather than Android System WebView. The app requires internet access to load web pages. The engine provides an EasyList-based network ad/tracker blocker. This does not guarantee that all ads or trackers are blocked, and it is not DNS-level filtering or anti-fingerprinting protection.

Private tabs use the engine's off-the-record context. The engine documents that private tabs share an off-the-record session and that its in-memory cookies, history and cache are cleared when the last private browser is closed. This is not the same as named persistent profile isolation, and private browsing does not hide traffic from internet providers, network administrators or destination websites.

Tor is integrated as an embedded Tor daemon with a fixed local SOCKS5 proxy. The app observes Tor's circuit-established status and verifies a local SOCKS5 handshake before resuming pages. While Tor is requested but not verified, address-bar navigation is blocked; if the controller reports failure, active pages are blanked and Tor URLs are not saved to browser-owned history/session restore. This is not a system-wide VPN or proven kill switch. WireGuard, automatic routing, Chromium proxy-fallback verification, and DNS/IPv6/WebRTC leak protection are not currently implemented.

## Network and local data

Websites visited by the user receive the network requests and information inherent to normal web browsing. Search terms entered without a URL are sent to DuckDuckGo. Download requests require user confirmation; approved downloads are written to the app-specific Downloads directory and the current download list is held in memory for the app session. Downloads are not currently persisted in a durable download database.

Normal browser tabs use the engine's normal browsing context. Orubus stores bookmarks, up to 500 recent normal-history entries, and normal-tab URLs for session restoration in app-private SharedPreferences. Private-tab URLs are excluded from browser-owned history and session restoration, and saving a bookmark from a private tab is disabled. Clearing the saved history list does not clear cookies, cache, or other engine storage. Named persistent profiles with independently verified storage separation and a complete data-deletion interface have not been implemented. Do not treat normal tabs as isolated identities.

## Engine supply chain

The app packages the Cefrium SDK and its Chromium/CEF components. The engine is pinned as a Gradle dependency. This means Orubus must track engine security releases and deliberately update and validate the dependency. The SDK is currently pre-1.0/public beta, so compatibility and production suitability remain under evaluation.

## Future privacy requirements

Before a public stable release, document each data type, storage location, retention duration, deletion behavior, network endpoint and third-party service. Do not claim traffic is anonymized unless the complete routing behavior has been implemented and independently tested.

This document describes the current repository state, not legal advice. Review it whenever application behavior or data flows change.
