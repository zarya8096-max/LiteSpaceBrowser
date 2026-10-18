# Verification — LiteSpace Browser 1.2.0

Application source: `67a76a3e48a250cc2a204faffd9fbe84a5bd3ac8`.
Documentation-only commits after this revision do not change the application APK.
Minimum API 26; target/compile API 35; version code 4. All features are available
without a purchase. This is an original implementation, not a verified exact clone.
See [the feature comparison](HERMIT-COMPARISON.md).

## Completed checks

| Check | Result |
| --- | --- |
| Debug build and packaging | Passed |
| Release build, R8 and packaging | Passed |
| JVM tests | 16 passed, 0 failed |
| Android Lint | 0 errors; 20 advisories |
| Real SQLite schema 1 → 2 migration | Passed, including Room schema validation and preservation of an existing entry |
| APK signatures | Both APKs verified with Signature Scheme v2 |
| Native packaging | ZIP alignment and every ELF PT_LOAD alignment verified for 16 KB pages; 8 native libraries per APK |
| Debug-only test probes in release manifest | Confirmed absent |

The release evaluation APK uses the project's **development certificate**, the
same certificate as its debug APK and earlier development builds. It is an
installable R8 build, not a private production signing identity. Generate and keep
your own release key using the README instructions for production distribution.

## Complete hardware-accelerated device matrix

[CI run 39151948890](https://github.com/zarya8096-max/LiteSpaceBrowser/actions/runs/39151948890)
ran the application revision above:

| Android runtime | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Android 8 / API 26 | 25 | 0 | 3 |
| Android 15 / API 35 | 27 | 0 | 1 |
| Android 16 / API 36 | 27 | 0 | 1 |
| API 26 separate native PIN regression | 1 | 0 | 0 |

The standard suite skips the opt-in credential test. API 26 also skips native
multi-profile and separate-data-directory tests, because its older provider/API
cannot supply those APIs. API 35/36 verify same-origin cookie independence in
real separate color processes, as well as native WebKit profile isolation.

The new tests cover actual loaded-DOM Reader extraction, local non-scriptable
Reader rendering, shared automatic scripts and foreign-origin exclusion, image
settings, RSS/Atom and CSS monitor parsing, unsafe XML rejection, typed source
validation and tool backup/rollback. Existing lifecycle, permissions, secure
foreground locking, large editor drafts, Persian RTL, filtering and icon decoding
regressions remain in the full matrix.

## Local verification and corrections

The software-rendered API 26 emulator passed the five new migration/parser/backup
checks. After corrections, it passed both actual WebView Reader/script tests and
the standalone collection/Persian/theme recreation test. An earlier full local
run failed Reader and a preference wait during resource pressure; these results
were not counted as successful checks. Reader had an actual compatibility defect:
upstream nullish-coalescing syntax and matchAll needed legacy-compatible
replacements. The full corrected hardware matrix above passed afterward. The
Reader now pins extraction to the selected document generation and does not rely
on getUrl() for a document created with a valid base URL.

The remote cookie probe also needed a framework ResultReceiver parcel wrapper:
its anonymous instrumentation subclass is not in the remote app's classloader.
The corrected test passed on both current Android runtimes. No production
isolation claim is based only on profile names or mock cookie stores.

## Installed R8 APK check

The exact release-evaluation APK listed below was installed on the local API 26
emulator. Native UI interaction created a Lite App with a complete HTTPS URL,
saved it while the keyboard remained visible, then force-stopped and relaunched
the app to confirm Room persistence. Both checks passed. An initial fast shell
text-entry attempt timed out; the completed check used verified full text entry.
This is a focused release smoke check, not the complete debug instrumentation
suite repeated against R8. The complete device matrix above tests the same
application source in its debug variant.

## APK checksums

- `LiteSpaceBrowser-1.2.0-debug.apk` (13,050,664 bytes): `478140adcf52bb1f4620a58282e8ee07b226ea35f408df55a03a00290fba5f6e`
- `LiteSpaceBrowser-1.2.0-release-evaluation.apk` (3,192,921 bytes): `039017013db72fd4c886028892dfa16b63d8ca39bc7e5c23c240d10ef0263546`

## Remaining acceptance work

Automated tests are not a physical-device or independent security certification.
Real third-party camera/microphone/geolocation flows, device biometrics, launcher
variations, external authenticated download servers, selected live RSS/monitor
endpoints under Doze, print destinations and website-specific DRM/sign-in behavior
still require the [manual matrix](TESTING.md). Some websites reject embedded
browsers. Do not interpret passing tests as proof of complete Hermit equivalence.

Website Web Push is unsupported. Opt-in RSS/HTML polling is native functionality
with a minimum 15-minute WorkManager interval and explicit limits. Fixed-color,
private and locked apps are excluded from background sources. Backups contain
configuration and global preferences, not cookies or website sessions. Detailed
limits are in [LIMITATIONS.md](LIMITATIONS.md).
