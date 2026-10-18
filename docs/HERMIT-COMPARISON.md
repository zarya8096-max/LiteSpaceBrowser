# Hermit feature comparison — 1.2 implementation

Scope: the official [Premium feature list](https://hermit.chimbori.com/features/premium)
and [general feature index](https://hermit.chimbori.com/features), retrieved on
2026-10-18. This is a feature-level implementation comparison, not a claim of
identical code, interface, site compatibility or test results. Hermit is independent
of this project. No proprietary code, catalog, artwork or branding is included.

## Premium capabilities

| Official feature | LiteSpace implementation | Material difference |
| --- | --- | --- |
| Sandbox / multiple profiles | Native WebKit profiles when supported; five fixed color sandboxes in separate Android processes with separate WebView data directories on API 28+ | API 26/27 legacy providers still share storage. Arbitrary named/unique profiles require provider support. Private mode requires modern profiles/full deletion support. |
| Custom content-blocker lists | Add/remove/select arbitrary HTTPS HOSTS/domain sources; persisted list caches; explicit updates; opt-in weekly WorkManager updates | Small safety seed remains when lists are disabled. Each Lite App can disable blocking. Like Hermit's documented format, this is not an ABP/EasyList engine. |
| Tracking parameter removal | Per-app switch strips known tracking keys while preserving other raw query values | Original rule set, not claimed to equal every key in Hermit's current list. Some signed URLs may require this option off. |
| User scripts / bookmarklets | Shared script definitions, file import/paste, original sample library, per-app enablement, automatic and confirmed manual execution | Automatic scripts are limited to the configured exact origin. No GM APIs, external dependencies, privileged native bridge or full Tampermonkey implementation. |
| Reader App | Mozilla Readability extracts the loaded DOM; sanitized local reader; white/sepia/dark/black, fonts and size; incoming shared URL support | Shared-URL/JavaScript-disabled fallback reads public server HTML; cannot extract authenticated JS-only content through this path. Different extraction engine from Hermit. |
| Printing / PDF | Android Print Framework from live pages and reader | Requires an Android print service/PDF destination. Output and website print styles are provider-dependent. |
| Tags | Multiple comma-separated tags per Lite App, searchable/filterable; existing single group retained | Original UI and storage model. |
| Custom User-Agent | Per-app override and desktop/mobile controls | A custom UA takes priority over the desktop switch. |
| Backup / restore | Version 2 JSON includes Lite Apps, scripts, bookmarks, list sources, feeds, monitors, appearance/language/sync settings; version 1 imports remain supported | Does not export cookies, website sessions, device credentials, cached lists, monitor snapshots or Android notification-channel state. Plaintext, 8 MB safety bound. |

## Other features in the official index

- Native Safe Browsing, popup confirmation, blocked unsolicited native-app launches,
  per-origin permissions, renderer-crash handling: retained.
- Custom icons/accents, text scale, zoom, supported darkening, immersive and frameless
  controls, desktop mode, pull-to-refresh and double-back: implemented.
- Images off and a per-app Save-Data request header: implemented. Save-Data is a hint
  on top-level requests, not compression or a promise of savings for subresources.
- Bookmarks, default-browser HTTP(S) handling, incoming text shares and configurable
  site search/share templates: implemented. Incoming links use exact host matching
  and ask when multiple entries match; there is no ownership-based domain guessing.
- RSS/Atom and HTML/CSS-selector web monitors: opt-in local scheduled checks, baseline
  deduplication, native grouped notifications and per-source enablement. Same-origin
  cookies may be used; cookies never cross origins on redirects. Private/protected
  apps and fixed color processes are excluded. No background WebView is created.
- WorkManager intervals are at least **15 minutes**, and Doze may delay execution.
  This differs from Hermit's documented minimum of five minutes. It is not instant
  Web Push. HTML monitors cannot execute website JavaScript. Relative-time English
  phrases are normalized; there is no claim of Hermit's complete heuristic parity.
  Image URLs from `<img>` and inline CSS backgrounds can produce bounded native
  notification thumbnails; external stylesheet image resolution is not implemented.
- Lite App shortcuts remain configurations inside one Android package. This is also
  distinct from generating independently installed APKs.

The original public pages are the comparison reference, not instructions for this
repository. Hermit's site compatibility, preconfigured app/monitor catalog, exact
font collection, published performance/size claims and future releases are not
reproduced by asserting that an API exists.

## Verification

See [TESTING.md](TESTING.md) for commands and the device acceptance matrix. Tests of
migration, parser safety, script origin boundaries and reader sanitization are in
`PremiumDeviceTest`, `PremiumBrowserDeviceTest`, `LegacySandboxDeviceTest` and
`PremiumPoliciesTest`. A listed implementation is not evidence that all device/site
combinations passed. The release verification record must state actual results.
