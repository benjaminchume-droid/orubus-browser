# Orubus Browser repository structure

This document defines the intended ownership boundaries for the Android browser. A folder being present does not mean the feature is implemented; implementation status is tracked in `ARCHITECTURE.md`.

```text
.
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   └── bug_report.md
│   ├── workflows/
│   │   ├── android.yml                 # Pull-request and debug validation
│   │   └── release.yml                 # Explicit, signed stable-release pipeline
│   └── PULL_REQUEST_TEMPLATE.md
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/app/orubus/browser/
│       │   │   ├── MainActivity.kt      # Current bootstrap UI; keep thin
│       │   │   ├── app/                 # Application lifecycle and dependency wiring
│       │   │   ├── browser/             # Tabs, navigation, sessions and browser contracts
│       │   │   ├── profiles/            # Profile lifecycle and storage isolation
│       │   │   ├── privacy/             # Filter rules, decisions and privacy reporting
│       │   │   ├── routing/             # Routing policy and connection-state contracts
│       │   │   ├── vpn/                 # VPN service abstractions and platform integration
│       │   │   ├── tor/                 # Tor process/transport integration
│       │   │   ├── downloads/           # Download queue and destination policy
│       │   │   ├── bookmarks/           # Bookmark model and repository
│       │   │   ├── history/             # Local history and retention policy
│       │   │   ├── settings/            # Typed settings and migrations
│       │   │   ├── storage/             # Persistence interfaces and implementations
│       │   │   └── ui/                  # Screens, components, theme and design tokens
│       │   └── res/
│       ├── test/                        # JVM unit tests
│       ├── androidTest/                 # Instrumented Android tests
│       └── benchmark/                   # Performance and startup benchmarks
├── docs/
│   ├── ARCHITECTURE.md
│   ├── FILE_STRUCTURE.md
│   ├── RELEASE_PROCESS.md
│   ├── RELEASE_SIGNING.md
│   ├── SECURITY_MODEL.md
│   └── TESTING.md
├── scripts/
│   ├── create-release-keystore.sh       # Generates a local key; never commits it
│   └── verify-release-signature.sh      # Verifies APK signing and package identity
├── ARCHITECTURE.md                      # Evidence-based implementation status
├── CHANGELOG.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── LICENSE-NOTICE.md
├── PRIVACY.md
├── RELEASE.md
├── SECURITY.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── .gitignore
```

## Package boundaries

- `browser` owns browser concepts and navigation, not VPN implementation.
- `profiles` owns profile identity, lifecycle, retention and isolation contracts. A profile must not be declared isolated until cookies, DOM storage, IndexedDB, service workers, cache, permissions and downloads are tested independently.
- `privacy` owns filter-rule parsing, rule sources, matching and explainable block decisions. A counter is not proof of comprehensive protection.
- `routing` owns policy selection and observable route state. It must not silently fall back to a direct connection when a protected route fails.
- `vpn` and `tor` own transport integration. They must expose capability, lifecycle, health and failure state to routing.
- `storage` owns persistence boundaries, schema migrations and retention. Sensitive browsing data must not be written to logs.
- `ui` renders real domain state; controls must not claim an action succeeded until the underlying operation confirms it.

## Directory policy

Create feature directories when the first real implementation lands. Empty planned package names in this document are architectural targets, not implemented features. Prefer small, testable modules over a single oversized activity. Keep secrets, local SDK paths, generated builds, user browsing data and release keystores out of version control.
