# Verification

Automated: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
Unit tests cover URL scheme/credential rejection, HTTPS upgrades, origin boundaries,
hosts parsing/domain boundaries, raw encoded URL preservation, IDN/IPv6/default
ports, invalid ports, cancellation/partial runtime permission results, stale-result
isolation and bounded stream reading.

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

Deep-debug instrumentation adds real WebView tests for disabled JavaScript, origin
boundaries and navigation while a website permission prompt is visible; Room tests
cover duplicate IDs, strict security-field types, icon bounds and oversized export.
An image decoder test uses a compressed 2048×2048 bitmap and checks 192×192 output.

Foreground lock regression is opt-in. **Use a disposable emulator**, configure its
screen PIN yourself, then pass that test PIN to the instrumentation runner:

```sh
adb shell am instrument -w -r \
  -e class org.litespace.browser.ForegroundLockDeviceTest \
  -e testDevicePin YOUR_DISPOSABLE_EMULATOR_PIN \
  org.litespace.browser.test/androidx.test.runner.AndroidJUnitRunner
```

The test does not change device credentials. It performs two real Android credential
confirmations, goes Home between them, verifies hidden/secure browser content while
locked, and checks the existing WebView is preserved after unlocking. It is skipped
without the opt-in argument. Physical biometric and current-provider acceptance
remain separate checks.

CI now exercises API 26, 35 and 36. API 26 also runs the opted-in device-PIN
regression on its disposable emulator, with a generated temporary PIN removed in
`finally`. `scripts/run_pin_regression.py` refuses non-emulators and reinstalls the matching
built APKs after Gradle's connected-test cleanup. The test waits
for Activity creation and actual stop events and focuses the native PIN entry
before submitting, rather than assuming animation/accessibility idleness means
that lifecycle transitions have completed.

Website JavaScript dialogs are real native dialogs with per-page cancellation;
the device suite verifies they do not persist after Home/resume. Protected/private
dialog windows use FLAG_SECURE. Native camera/microphone, physical biometrics,
external download servers and video compatibility still need the acceptance
matrix above.

The 1.1 interface suite adds `DesignDeviceTest`: search + favorite filtering,
empty-result recovery, persisted dark/Persian preferences, Activity recreation and
actual mirrored bottom-navigation coordinates. The large-draft test explicitly
confirms the discard dialog before checking that the ViewModel draft is cleared.

For optional real screenshots on a disposable emulator:

```sh
adb shell am instrument -w -r -e captureDesign true \
  -e class org.litespace.browser.DesignDeviceTest \
  org.litespace.browser.test/androidx.test.runner.AndroidJUnitRunner
```

PNG files are saved in the debug application's cache. For example:

```sh
adb exec-out run-as org.litespace.browser cat cache/design-library-light.png > library.png
```

Screenshot capture is opt-in; regular CI tests exercise behavior without depending
on the host's graphics capture support. The optional capture checks actual PNG
output and uses the native screencap path when an older provider cannot return a
UiAutomation bitmap.

## 1.2 Premium regression tests

`PremiumPoliciesTest` verifies raw-query-safe tracking removal, URL-encoded template
substitution, multiple-host lines and multiple tags. `PremiumDeviceTest` opens a
real schema-1 SQLite database, migrates it with Room validation, checks RSS/Atom and
monitor parsing/DTD rejection, sanitizes Reader HTML and exercises version-2 tool
backup rollback. `PremiumBrowserDeviceTest` runs actual shared scripts in WebView,
checks cross-origin exclusion and opens Reader from a real long article DOM.
`LegacySandboxDeviceTest` uses debug-only non-exported Activities in the same red/
green processes as the application to prove same-origin cookies remain independent
on API 28+. It is skipped on API 26, where data-directory suffixes do not exist.

Manual acceptance additionally requires: choose a public feed; test/save; check once
for the baseline; publish a new item on a controlled server; check again and inspect
native notification/channel/grouping. Repeat with a monitored CSS selector, logged-in
same-origin source and a redirect to a second HTTPS origin (no cookie forwarding).
Disable notifications at Android level, disable the source, lose connectivity and
verify visible error/old baseline preservation. Exercise PDF output using a real
print destination, default-browser routing from another app, exported text sharing,
script import via document picker, and weekly-list retries. Native polling is not
instant push and must be checked with Doze/background restrictions enabled.
