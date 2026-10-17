# Verification record — 2026-10-17

Application code: `edce907` (subsequent changes are tests and documentation).
Android 8 runtime test: `0d59f01`; the subsequent application change only guards
invalid redirected/popup URLs against validation exceptions. No API, dependency,
storage or normal page-loading behavior changed in that guard.

## Completed checks

| Check | Result |
| --- | --- |
| Debug compilation and packaging | Passed |
| Release compilation, R8 and packaging | Passed; unsigned APK generated |
| JVM policy tests | 5 passed, 0 failures |
| Android lint | Passed, 0 errors; pinned-dependency/kapt migration advisories |
| Android 8 / API 26, Chrome WebView 69.0.3497.100 | 4 passed, 1 unsupported-profile assumption skipped |
| Debug APK signing | APK Signature Scheme v2 verified |
| Native library packaging | 16 KB ELF/ZIP alignment verified |
| GitHub Actions | Build, unit tests and lint passed for application source |

Android instrumentation exercised a real WebView loading bundled HTTPS content,
custom JavaScript execution, CSS injection, Activity launch/close, Persian configuration
round trips through SQLite/Room, and transaction rollback for partly invalid backups.
The screenshot in this repository was captured from the running Android 8 app.

## Android 15 environment limitation

The software-emulated API 35 Google APIs image did not complete Android framework
startup. Although package/activity services and WebView 124.0.6367.219 became
available, installing the APK failed inside Android's `StorageManagerService`:
`PackageManagerInternal.freeStorage(...)` was called on a null system-service reference.
The failure occurred before application execution. API 35 runtime tests are therefore
**blocked**, not reported as passed. Hardware acceleration was unavailable.

`ProfileDeviceTest` is included for supported devices. It checks separate native cookie
stores and that clearing one profile leaves another untouched. It uses complete
browsing-data deletion when available and per-profile cookie removal otherwise.
Private sessions in the app require both MULTI_PROFILE and DELETE_BROWSING_DATA;
unsupported providers refuse private mode rather than sharing a normal session.

## Artifact

- File: `LiteSpaceBrowser-1.0.0-debug.apk`
- Bytes: `12470189`
- SHA-256: `1ef4acb8a585324815c63d1ad6a3b8565ed2cdb204453e07cf439f7ca66bce15`
- Debug signing key; use your own key for production updates.

See [testing instructions](TESTING.md) for the remaining physical-device acceptance
matrix. Camera/microphone/location, launcher behavior, biometric/device-PIN prompts,
video playback, downloads, website compatibility and modern profile behavior still
need physical-device acceptance before a production/store release. This record is
not an external security audit or a claim that every website is compatible.
