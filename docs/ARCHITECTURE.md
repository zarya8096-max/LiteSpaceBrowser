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

No analytics, server, account, background polling or paid service. Filter updates are
explicit foreground operations; WorkManager is unnecessary. The homepage/error document
is served through WebViewAssetLoader under its reserved HTTPS domain.
