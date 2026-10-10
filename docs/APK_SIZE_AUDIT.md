# APK size audit and optimization plan

## Scope and evidence

This audit reviews the Android Gradle configuration, the Cefrium dependency, and the latest successful GitHub Actions artifact metadata. It distinguishes measured facts from likely causes.

- Repository: benjaminchume-droid/orubus-browser
- Engine dependency: `com.cefrium:cefrium-sdk:0.9.4`
- Packaging before this change: one debug APK artifact containing both supported CPU architectures, with legacy JNI packaging enabled.
- Latest Actions artifact ZIP size: 245,022,219 bytes. This is the uploaded Actions artifact size, not a measured standalone APK entry-by-entry breakdown.
- Cefrium's published documentation describes the ARM64 `libcef` binary as roughly 203 MB uncompressed. This is consistent with a large browser-engine footprint, but is not an exact measurement of this specific APK.

## Findings

### 1. Main cause: embedded Chromium/CEF native engine

The app embeds a full browser engine. Its native libraries and engine resources dominate the payload; the Kotlin UI and ordinary Android dependencies are not credible explanations for a roughly 234 MB download. Chromium includes a JavaScript engine, rendering, networking, media/platform integrations and supporting resources.

### 2. Architecture duplication was an avoidable distribution cost

Before this audit, the APK build did not declare ABI splits. The package could include native libraries for both `arm64-v8a` and `x86_64`, although a normal Android phone needs only ARM64. The build now emits architecture-specific APKs and disables the universal APK. A phone user should receive the ARM64 APK; x86_64 is retained for compatible emulator/testing targets.

Expected outcome: a substantial reduction in the per-device APK versus a universal APK. Exact savings are deliberately not estimated until the new CI size report measures the output.

### 3. Compression is possible, but cannot erase the engine's intrinsic size

The APK is already a ZIP container, and `useLegacyPackaging = true` requests compressed native-library packaging (with extraction at install). ZIP compression has limited benefit for already-compressed or high-entropy native binaries. More aggressive compression can increase install/startup CPU costs and is not a substitute for excluding the wrong ABI.

The CI workflow now reports, for every generated APK, the exact file size and largest ZIP entries with both compressed and uncompressed sizes. It also totals native libraries by ABI. Use those numbers to decide whether further packaging changes have measurable benefit.

### 4. Release code shrinking is not the first lever

Release minification is currently disabled. R8 may reduce Kotlin/Java bytecode and unused Android resources, but it cannot materially shrink the CEF native engine. Since Cefrium may rely on JNI/reflection, enabling minification without reviewing its keep rules and testing a release build risks breaking the browser. It remains off pending that validation.

### 5. App Bundle is a distribution format, not magical compression

The release workflow builds an Android App Bundle as well as split APKs. A store can deliver device-specific native libraries from an AAB. The AAB file itself may still be large, and a sideloaded APK does not receive Play's split delivery behavior automatically.

## Changes made

- Enabled ABI-specific APK generation for `arm64-v8a` and `x86_64`.
- Disabled universal APK generation to avoid bundling both architectures into a single APK.
- Added CI inspection of APK ZIP entries, compressed/uncompressed sizes and native-library totals.
- Updated release signature verification to verify each generated split APK instead of assuming one universal `app-release.apk`.
- Kept R8 minification disabled until Cefrium/CEF shrinker rules and release runtime behavior are validated.

## What remains to verify

1. Wait for CI and inspect `apk-size-report.txt`; compare the ARM64 APK size to the previous universal APK.
2. Install the ARM64 split APK on a physical ARM64 device and verify startup, navigation, JavaScript, media, downloads and private tabs.
3. Confirm the x86_64 split APK installs and runs on the intended emulator.
4. Compare debug and signed release outputs. Do not infer release savings from debug-only results.
5. Review Cefrium's exact native packaging, compression and license/source obligations before public distribution.

## Bottom line

Yes, we can reduce the APK delivered to a phone by avoiding duplicate CPU architectures, and we can measure whether ZIP compression improves it further. The largest remaining floor is the bundled Chromium engine itself. A reduction from roughly 234 MB to a much smaller per-device package is plausible from ABI splitting, but only the new CI report can establish the actual result. If the ARM64 split remains too large, the next decision is engine distribution/architecture, not cosmetic UI optimization.
