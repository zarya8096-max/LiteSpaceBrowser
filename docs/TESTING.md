# Verification

Automated: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
Unit tests cover URL scheme/credential rejection, HTTPS upgrades, origin boundaries,
hosts parsing/domain boundaries and bounded stream reading.

Device acceptance matrix (must be run before a production release): Android 8/API 26
with an older provider, current Android with current System WebView, Persian RTL,
light/dark theme, gesture navigation and a launcher with/without pin support.

1. Create/edit/delete two apps; kill/relaunch and verify configuration persists. Search
   by name and group; favorite ordering. Import a large image as icon; pin both apps.
2. Same login website in two isolated apps: different cookies/localStorage. Set matching
   shared profile names: sessions shared. Older provider: warning and no private launch.
3. Private session on a provider supporting MULTI_PROFILE and DELETE_BROWSING_DATA:
   login, close, reopen; login gone. Force-stop while private; restart the process,
   relaunch browser and verify orphan profile removal. Download a file; verify it remains.
4. Blocked domain in an iframe: blocked; similar-suffix nonblocked domain loads. Update
   filters offline: existing rules remain. Reload a site broken by filtering after
   disabling per-app blocking. Inspect known service-worker limitations separately.
5. HTTP with HTTPS-only on: upgrade/reject. Bad TLS certificate: blocked. file/javascript
   navigation: rejected. Unrequested popup blocked; user popup prompts.
6. Geolocation/camera/microphone: toggle off, toggle on + deny, allow, navigate to a new
   origin while prompt is visible, revoke system permission. Verify no silent grants.
7. Upload with single/multiple file selection, cancel picker, rotate. Download ordinary
   and signed HTTP links on API 26/28/35, deny storage on API 28. Cookie-only protected
   downloads require a signed link or an external browser; verify no cookies are exported. Blob gets notice.
8. Pull refresh at top, scrolling below top, edge back/forward, system back, hide/show
   controls, zoom, desktop UA, custom UA, text size, dark-supported site, fullscreen video
   and back from video. Pause/resume, rotation, renderer crash, low-memory process death.
9. Lock app; cancel authentication, authenticate with device PIN/biometric, reopen via
   shortcut, edit/delete, restore over locked app. Device without screen lock fails closed.
10. Backup round-trip icons/settings/custom code. Malformed, unsupported-version,
    oversized or partly invalid backups must fail without partial imports. Import only
    trusted code. Export contains no cookies. Uninstall erases local configuration.

This repository's build/test results do not substitute for physical-device acceptance,
accessibility review, external security audit or store-policy review.

Android instrumentation suite (connected device/emulator):

```sh
./gradlew connectedDebugAndroidTest
```

`RepositoryDeviceTest` verifies real SQLite/Room backup round trips and rollback of
partly invalid imports. `LaunchDeviceTest` exercises dashboard/browser Activity lifecycle.
