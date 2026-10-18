# Verification record — 2026-10-18

Application code: `352c654`. Subsequent commits adjust device-test task resumption,
CI diagnostics and documentation; they do not change the application binary.

## Completed build checks

| Check | Result |
| --- | --- |
| Debug compilation and packaging | Passed |
| Release compilation, R8 and packaging | Passed; unsigned APK generated |
| JVM policy/permission tests | 12 passed, 0 failures |
| Android lint | 0 errors; 17 pinned-dependency/kapt migration advisories |
| Debug APK signing | APK Signature Scheme v2 verified |
| Native library packaging | 16 KB ELF/ZIP alignment verified |

## Device results

[CI run 39112270860](https://github.com/zarya8096-max/LiteSpaceBrowser/actions/runs/39112270860)
used test commit `e5b5d88` with the application code above:

| Runtime | Standard suite |
| --- | --- |
| Android 8 / API 26 | 17 passed, 0 failed, 2 skipped |
| Android 15 / API 35 | 18 passed, 0 failed, 1 skipped |
| Android 16 / API 36 | 18 passed, 0 failed, 1 skipped |
| API 26 separate native PIN regression | 1 passed, 0 failed |

The credential test is opt-in and skipped in each standard suite. The API 26
provider also lacks native profiles, so that profile test is explicitly skipped.
The API 35/36 suites exercise real separate WebView cookie stores and clearing one
profile without clearing the other. Modern runtime coverage now runs on
hardware-accelerated CI; the earlier local API 35 software-emulator boot failure
is not counted as an application test result.

The suites also cover real HTTPS WebViewAssetLoader content, CSS/JavaScript,
foreign-origin script exclusion, disabled JavaScript, native permission/dialog
cancellation, configuration revocation, history/back handling, protected opening
without credentials, editor recreation and bounded completed Activity saved state,
sampled icon decoding, Persian SQLite/Room round trips, strict backup validation
and transactional rollback.

The separate real Android credential test passed both locally and in CI: initial
PIN, Home/actual Activity stop, hidden secure content, second PIN and the same
retained WebView. The old 1.0.0 APK fails the stopped-view protection assertion.
An earlier CI step could not find instrumentation after Gradle cleanup; reinstalling
the matching built APKs corrected that setup problem. All four final CI jobs passed.

## Local regression checks

Android 8 / API 26 with Chrome WebView 69.0.3497.100:

- Native PIN regression: **1 passed** (68.718 seconds), including initial
  authentication, actual Home/stop, hidden/secure content and a second authentication
  preserving the original WebView. The disposable PIN was cleared afterward.
- Corrected editor synchronization regression: **1 passed** (44.346 seconds).
- The preceding standard local run had 16 passed, 1 failed, 2 skipped. Its editor
  assertion raced input dispatch; awaiting the actual state transition corrected
  the harness. This failed run is not presented as a successful full-suite run.

The software emulator also showed a System UI ANR during framework startup;
that infrastructure issue was handled separately from application test results.

## Minified release acceptance

The actual R8-minified release APK was signed with the existing development key
for installation on the disposable API 26 emulator. The following native UI flow
passed:

1. Create a Lite App through Compose and save it to Room.
2. Force-stop/relaunch; open the saved entry and render bundled HTTPS content
   through the real WebViewAssetLoader/WebView.
3. Export through Android's Storage Access Framework, read the actual file and
   validate the version, ID, name, URL and security field names after minification.
4. Delete the entry through the UI, import the same file through SAF and confirm
   restoration.
5. Force-stop/relaunch again and verify the restored entry is still present.

Result: **RELEASE_SMOKE_PASSED**. The software-emulator driver required handling
Gboard's first-run contacts prompt and verifying text input/focus. These are not
reported as application fixes. No real personal data was used.

The optional `release-evaluation.apk` asset is this minified build, signed with a
**development key**, not a production signing identity. Follow the README to build
and sign a production APK with your own key.

## Artifact

- File: `LiteSpaceBrowser-1.0.1-debug.apk`
- Bytes: `11888260`
- SHA-256: `051e74616cb2066fd40ece905ff6f3d50b4c0f4ef97382c4637b674ce466b3b1`
- Version code 2; minimum API 26; target API 35.
- Development signing key; use your own key for production updates.

See [testing instructions](TESTING.md) for the physical-device acceptance matrix.
Camera/microphone/location, launcher behavior, physical biometrics, video playback,
download servers and website compatibility still require physical-device
acceptance before a production/store release. This is not an external security
audit or a guarantee that every website is compatible.
