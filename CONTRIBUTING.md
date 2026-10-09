# Contributing to Orubus Browser

## Development principles

- Implement real behavior. Do not add simulated success states or controls that only change labels.
- Keep the user interface honest about feature availability and limitations.
- Treat privacy, profile isolation, routing and release signing as security-sensitive systems.
- Add tests with each meaningful behavior change.
- Keep dependencies current and document why a new dependency is needed.
- Avoid collecting or logging browsing data unless a documented feature requires it and the user has control over it.

## Pull requests

1. Describe the user-visible change and its implementation.
2. List files/modules changed and any migration or compatibility implications.
3. Include test commands and actual results.
4. Identify new permissions, dependencies, network calls, telemetry or data retention.
5. Update architecture, security and release documentation when behavior or guarantees change.
6. Never attach keystores, credentials, private browsing data or unredacted logs.

## Feature claims

Do not describe a feature as implemented until the underlying system exists and relevant tests pass. In particular, tabs are not isolated profiles, a VPN toggle is not a verified kill switch, and a Tor connection is not by itself proof of anonymity.
