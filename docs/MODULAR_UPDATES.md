# Modular update architecture

## Goal

Keep the initial APK focused on the browser shell and required native runtime. Deliver independently versioned, signed data packs separately, and use binary delta delivery for application releases where the distribution channel supports it. A changed APK can be 11 MB as a downloaded delta without being an 11 MB complete replacement APK.

## Update lanes

### Lane A: data packs, independently downloadable

Suitable for filter lists, search-provider definitions, language data, fonts, icons, images, and other non-executable resources. Each pack has its own version, SHA-256 digest, compressed and expanded size limits, application compatibility range, HTTPS URL, and media type. A detached ECDSA/SHA-256 signature covers the exact manifest bytes. The app pins the update public key; pack hashes are not trusted until the manifest signature verifies.

The current source adds `UpdateManifestVerifier` and `UpdatePackInstaller`. The installer rejects redirects, requires HTTPS, checks the signed byte length and digest, applies archive path-traversal and expansion limits, rejects executable/native payload types, stages under app-private storage, and activates a version directory only after validation. It is a backend foundation, not yet connected to a settings UI or a hosted update service.

### Lane B: UI and application logic

Native Android UI and Kotlin logic remain part of the signed Android application update. They should not be hot-swapped by downloading arbitrary DEX, JAR, native libraries, or scripts. If we choose a web-rendered UI for specific surfaces, its update policy and distribution-channel rules need separate review; the current pack installer explicitly rejects JavaScript.

For Google Play distribution, use Android App Bundles, ABI/device configuration splits, and Play-managed delivery for eligible feature modules. For direct APK distribution, produce signed full APKs plus signed binary-delta packages that reconstruct the exact target APK locally, verify the reconstructed APK's package/signature/version, then hand installation to Android's Package Installer. A delta is only a transfer optimization: Android still installs a complete signed target package.

### Lane C: Chromium / CEF engine

The Chromium engine is the largest and riskiest layer. Do not attempt to load an arbitrary replacement `libcef.so` from a downloaded ZIP inside the running process. Native linker, dependency, ABI, signing, and process-lifecycle constraints make this unsafe and brittle.

Preferred order:

1. Measure real compressed APK entries per ABI from CI artifacts.
2. Use ABI-specific APKs or Android App Bundle splits so a device does not download another architecture's engine.
3. Test binary-delta delivery for engine upgrades; it can reduce bytes transferred while preserving a complete signed APK after reconstruction.
4. Evaluate an independently installed, signed engine package only if the chosen engine supports a stable IPC/service boundary. Cefrium currently embeds the CEF engine in the app, so this would be a significant architecture change, not a simple file split.
5. Pin engine versions, validate signatures and hashes, keep a known-good release available, and test rollback/recovery before rollout.

### Lane D: network transports

Tor and WireGuard implementations have separate dependency and configuration lifecycles, but their Kotlin integration and native/service dependencies still ship in the APK today. Data-only update packs cannot replace their executable code. Transport changes must use signed application releases or a supported modular-delivery mechanism, not an unverified plugin download.

## Manifest contract

Manifest schema version 1 has this shape:

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "generatedAt": "2026-10-10T00:00:00Z",
  "packs": [
    {
      "id": "filterlists",
      "version": 1,
      "url": "https://updates.example.invalid/packs/filterlists-1.zip",
      "sha256": "<64 hex characters>",
      "compressedBytes": 12345,
      "maxExpandedBytes": 65536,
      "minAppVersionCode": 2,
      "maxAppVersionCode": 999999,
      "mediaType": "application/vnd.orubus.filterlists+zip"
    }
  ]
}
```

The detached signature is Base64-encoded ECDSA/SHA-256 over the exact UTF-8 manifest bytes. Production builds must pin the public key and restrict allowed update hosts. Do not put the private signing key in the repository, APK, or client-side configuration.

## Rollout safety

- Keep separate stable, beta, and nightly channels.
- Verify signature before parsing trusted update fields; verify the downloaded pack digest before extraction.
- Apply strict compressed/expanded size and entry-count limits.
- Never execute pack contents. Treat all downloaded pack files as data.
- Stage new versions and keep the previous pack version available until the consumer reports successful activation.
- Add a kill switch and minimum supported version policy at the update service.
- Log version, digest, result, and failure reason without logging private keys or browsing history.
- Test interrupted downloads, corrupt ZIPs, invalid signatures, replayed old manifests, storage exhaustion, and rollback.

## What is not complete yet

- No production update host, signed production manifest, or pinned production signing key has been configured.
- The manifest verifier and pack installer are not yet wired into a browser settings screen or background scheduler.
- No updater UI, progress reporting, retry/backoff policy, or rollback-aware consumer exists yet.
- APK delta generation/reconstruction and Android Package Installer handoff are not implemented.
- Play App Bundle/feature-module delivery has not been set up.
- The Cefrium/CEF engine is still bundled with the APK; this work does not make Chromium upgrades independent APK modules.
- CI must compile and test these changes before considering the update foundation verified.