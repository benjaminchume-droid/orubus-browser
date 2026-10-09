# Orubus Browser privacy notes

## Current behavior

This project is an early development build. It uses Android System WebView and a small built-in host-based request filter. The app requires internet access to load web pages. It does not currently implement Tor, WireGuard, a verified VPN kill switch, true isolated profiles, or a comprehensive ad/tracker filtering engine.

The initial browser shell does not intentionally send browsing history to an Orubus-operated backend. Websites visited by the user may receive the network requests and information inherent to normal web browsing. Search requests are sent to the search provider selected in the current code.

## Local data

WebView may store cookies, cache, DOM storage and site data. Current tabs share the app's WebView storage context. Do not treat tabs as separate identities or private profiles. A complete retention/deletion interface has not yet been implemented.

## Future privacy requirements

Before a public production release, document each data type, its storage location, retention duration, deletion behavior, any telemetry, network endpoints and third-party services. Use privacy-preserving defaults, collect the minimum data needed, and do not claim that traffic is anonymized unless the full routing behavior has been independently tested.

This document describes the current repository state, not legal advice. Review it whenever application behavior or data flows change.
