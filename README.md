# Orubus Browser

A privacy-focused Android browser project built around real browser, routing, filtering, and profile primitives.

## Project status

This repository is the start of implementation, not a claim of production readiness. The first milestone establishes a reproducible Android APK build and a native application foundation. Chromium engine packaging, isolated profiles, VPN routing, Tor, and production-grade filtering will be integrated and verified in subsequent milestones.

## Principles

- Real behavior only: no simulated VPN status, fake block counts, or controls that imply unimplemented security.
- Explicit routing policies and fail-closed behavior for protected sessions.
- Profile isolation must be backed by distinct browser storage contexts, not just profile labels.
- Security updates and reproducible CI builds are product requirements.
- Every subsystem is tracked as implemented, tested, partial, or not implemented.

## Build

The Android debug APK is built by GitHub Actions on pushes and pull requests. See `.github/workflows/android.yml`.

## Security and privacy

Do not use this initial build as a guarantee of anonymity or leak protection. The project will document each security claim only after implementation and testing.
