# Deep debugging — 1.0.1

This pass fixes concrete data-integrity, permission, authentication and lifecycle
problems. See [verification results](VERIFICATION.md) for executed checks and
[platform limitations](LIMITATIONS.md) for guarantees the platform cannot provide.

| Area | Problem and correction | Regression evidence |
| --- | --- | --- |
| URLs | HTTP upgrade decoded `%2F` / `%26`, changing paths or signed queries. Preserve raw URI components. | Failing JVM test before fix; passing afterward |
| Origins | Default ports/case differed; international hosts were rejected. Canonicalize scheme, host, ports and unambiguous IDNs; reject invalid ports, credentials and ambiguous IDNA mappings. | JVM IDN, Persian host, IPv6, default-port and malformed-authority tests |
| Runtime permissions | Empty results could grant access. A late result could reach a different page after cancellation. | Empty/partial/unrelated grants deny; cancelled requests keep their slot until Android replies; pure JVM regressions |
| Website prompts | A permission confirmation could survive navigation or cancellation. Invalidate page generations and dismiss pending prompts exactly once. | Real HTTPS asset geolocation prompt followed by navigation |
| Accessibility | The visible Create button had no accessible label. Add explicit English/Persian semantics. | Native accessibility selector and editor recreation test |
| Website windows | Track JavaScript dialogs, cancel website UI on stop, reject late full-screen/pop-up requests while locked, and secure protected/private dialog windows. | Real JavaScript alert across Home/resume |
| Foreground protection | Returning to a retained browser bypassed its opening gate. Hide protected content synchronously on stop, authenticate again and withhold picker selections until unlocked. | Opt-in real Android device-PIN regression; the old APK fails the stopped-view check |
| Credential lifecycle | Legacy credentials can return before the Activity restarts. Apply successful authentication only after RESUMED, with a bounded wait, instead of dropping the result. | Real device-PIN opening and reopening regression |
| Configuration revocation | An already-open browser retained old permissions/lock settings after editing or restore. Observe its entry and close on change/removal. | Room update while a real browser is open |
| Script injection | A stale load callback could refer to a previous document. Check both native URL and renderer `location.origin` before executing custom code. | Real WebView configured-origin and foreign-document checks |
| Backups | Oversized exports could not be imported; malformed types could silently alter security settings; duplicate IDs could overwrite earlier entries. | Matching UTF-8 limits before writing, strict field types, unique IDs, transactional rollback; SQLite device tests |
| Icons | Imported compressed images could decode at arbitrary full size; file-picker work ran on the UI thread. | Sample bounds before decode, 192×192 output for dashboard/shortcut/editor, picker I/O off main; device bitmap test |
| Editor state | Large icons/code were serialized into saved Activity state, risking Binder size failures. | ViewModel drafts retained across recreation; completed saved-state payload measured on API 29+ |
| Lifecycle | A renderer crash could skip exit cleanup on legacy shared storage. | Track initialized storage independently of the live WebView; audit of renderer-exit path |
| Error navigation / video teardown | External opening must use the failed website URL rather than the internal error asset. Clear video references before calling native callbacks to tolerate reentry. | Lifecycle/code review; compilation; physical video acceptance remains separate |
| File picker | A stale picker result could be delivered to a subsequent chooser. | Reserve the picker while its external Activity is in flight; cancel page callbacks on navigation |
| Filter lists | Cached list parsing blocked startup; concurrent updates shared a temporary file; dashboard counters were stale. | Asynchronous cache load, serialized updates and observable counters |
| Shortcuts/deletion | Launcher exceptions could crash management; restored IDs could remain disabled. | Re-enable/update on pin, handle launcher failures, disable only after successful database deletion |

The first device pass exposed a test assumption: `loadDataWithBaseURL` is not an
HTTP navigation, and native URL/progress values are provider-dependent. That test
now waits for actual document content, asserts its renderer origin and verifies
that the configured script is absent. This changes the test rather than weakening
the application's origin checks.

Two additional harness issues were separated from application defects. On a slow
software emulator, injected Back input could return before Compose discarded the
draft; the test now awaits that state transition with a bounded deadline. Gradle
also removes its instrumentation package after connected tests, so the separate
PIN runner reinstalls the matching APKs before execution. The old runner's
"Unable to find instrumentation info" was not an application assertion failure.

No external analytics, native JavaScript bridge, website polling or simulated web
push was added. Locking protects UI access, not encryption or network anonymity.
WebView's `onPause` is best-effort and does not guarantee all JavaScript timers or
network work stops. Exit data deletion is asynchronous; force-stop can interrupt it.
