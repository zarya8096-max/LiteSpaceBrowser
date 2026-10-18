# LiteSpace architecture

Single Android application module, with dependency direction UI / Web → domain ← data.
Domain owns URL validation, backup validation, repository contract and filter matching.
The pure JVM domain model has no Room or Android dependencies. Room stores it through
a LiteAppEntity mapping; UI only uses the domain model. The repository implements transactional
backup imports. DataStore stores global theme and language. MainViewModel exposes
StateFlows and commands to Compose. A small application container owns the repository
and blocker; no Activity or WebView is retained in it.

BrowserActivity owns exactly one primary WebView and destroys it with the Activity.
It assigns an AndroidX WebKit profile before navigation when MULTI_PROFILE is available.
An incognito session requires multi-profile and full browsing-data deletion support.
It uses a unique temporary profile: data clears on close, and its directory is reclaimed
after process restart (AndroidX forbids deleting profiles already loaded into memory).
Device authentication gates protected app launches, including pinned shortcuts.
Permission grants are requested per origin and per session, constrained by per-app toggles.
Custom scripts have the same privileges as the page, with no native JS bridge.

No analytics, server, account or paid service. Background site checks and weekly
filter updates are opt-in and use constrained WorkManager tasks. The homepage/error document
is served through WebViewAssetLoader under its reserved HTTPS domain.

## 1.2 Premium modules

Room schema 2 adds advanced app settings and a tool table for shared script,
bookmark, list and notification-source definitions. A non-destructive 1→2 migration
and a real migration test preserve schema-1 configurations. Backup version 2 accepts
version 1 and validates all definitions before a Room transaction; DataStore global
preferences are restored separately.

Five reserved color profiles use non-exported Activities in separate OS processes
on Android 9+, initialized with `WebView.setDataDirectorySuffix` before any WebView
API. Room multi-instance invalidation propagates configuration revocations between
those processes. Native multi-profile support handles arbitrary profiles where
available. Background sync runs only in the main process and excludes fixed colors,
private apps and locked apps.

SafeHttp uses HTTPS, bounded bodies, timeouts and manual redirect handling. RSS/Atom
parsing refuses DTD/entities. CSS monitor extraction is server-HTML-only. One periodic
WorkManager task batches enabled notification sources; a separate opt-in weekly
job updates selected filter lists. No WebView is created for background checks.

The browser runs bundled Readability against a cloned page DOM. ReaderActivity
sanitizes the resulting HTML and serves a local non-scriptable document with remote
resources blocked. Printing uses PrintManager. LinkRouterActivity validates exported
HTTP(S)/plain-text inputs and routes existing entries through the normal auth gate.
Debug-only non-exported probes test separate-process cookies; they are absent from
release APKs.
