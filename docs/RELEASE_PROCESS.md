# Release process

## Branch and version policy

- Work on feature branches and merge reviewed changes to `main`.
- Every stable release must be associated with a reviewed commit and a version tag such as `v0.2.0`.
- Keep `versionName` semantic and user-facing. Increment `versionCode` monotonically for every Android release, including rebuilds that need to replace an artifact.
- Do not publish a release from an unreviewed or failed CI run.
- The release workflow is manually dispatched and must be run against a protected stable tag. It requires signing secrets and produces signed APK/AAB artifacts.

## Release checklist

1. Update `CHANGELOG.md` and the app version.
2. Review `ARCHITECTURE.md` and all privacy/security statements for accuracy.
3. Run unit tests, instrumented tests available for the target devices, lint and release builds.
4. Check dependency advisories and third-party licenses.
5. Validate profile isolation, private-data deletion, routing failure behavior, DNS/IPv6/WebRTC exposure and downloads before advertising those capabilities.
6. Confirm signing secrets are configured in GitHub Actions and that the release tag points to the reviewed commit.
7. Run the release workflow and inspect the output artifact.
8. Verify APK signature and certificate fingerprint against the offline release record.
9. Install the APK over the previous stable version on a test device and confirm that an in-place update succeeds.
10. Test fresh installation, upgrade migration, rollback protection, app startup and critical browsing paths.
11. Publish checksums and release notes with the artifacts.
12. Preserve the exact signed artifacts, source commit, CI run link and checksums.

## Incident response

If a release is suspected compromised, stop distribution, preserve CI logs and artifacts, revoke exposed repository credentials, and determine whether the signing key was exposed. Communicate known impact without claiming that rotating a key repairs already-installed copies.
