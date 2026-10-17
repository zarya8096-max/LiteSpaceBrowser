# Platform behavior and limits

* Lite Apps are local configurations, not separately installed APK packages. There
  is no artificial app count limit. Pinned shortcuts require launcher support and
  user acceptance; apps cannot silently add home-screen icons. Deleting a Lite App
  disables its pinned shortcut; editing it requires pinning again to update its label.
* AndroidX WebKit MULTI_PROFILE is detected at runtime, not inferred from Android
  version. Supported devices use a unique profile for each app; explicitly matching
  profile names share cookies and storage. Older WebViews share the default profile
  and display a warning. Separate processes/data-directory suffixes are not used:
  they cannot provide unlimited simultaneous independent profiles on API 26.
* Incognito requires both MULTI_PROFILE and DELETE_BROWSING_DATA support. It uses
  temporary disk storage and clears browsing data asynchronously on normal close.
  AndroidX cannot delete a profile loaded into the current process, even after its
  WebViews are destroyed. Unloaded temporary profile directories are deleted on a
  browser launch after process restart. Force-stop/power loss can leave data until
  that next launch, and can interrupt pending asynchronous cleanup. Profile names
  may remain until that removal. This is not anonymity. Downloads remain in Downloads
  and in Android's download history.
* Clear-on-exit means Activity destruction, not every background transition. Android
  does not guarantee callbacks on force-stop/process death. Clearing a shared profile
  signs out all its apps. Cache APIs may have provider-specific scope. Configuration
  deletion does not erase persistent profile data: clear it before deleting the app.
* Website Web Push / Notification API / service-worker push notifications are not
  implemented by Android WebView. There is no fake push, periodic website scraping,
  or background browser service. Per-app notification control covers download
  completion notices; Android Download Manager still owns progress notifications.
* The blocker supports hosts/domain lists, including subdomains. It does not implement
  EasyList syntax, cosmetic filtering, CNAME uncloaking, WebSocket filtering or a VPN.
  Isolated profiles also filter service-worker network requests. Shared/default profiles
  do not apply a per-app service-worker policy, and interception cannot see every
  cache/network request. A small
  original seed is bundled. Explicit update downloads the maintained StevenBlack
  unified hosts list over TLS; no scheduled refresh/background job runs. List licenses:
  https://github.com/StevenBlack/hosts (including each upstream source's license).
* Pop-ups require a user gesture and confirmation, then open in the same WebView.
  Multi-window opener/postMessage-dependent OAuth flows may require an external
  browser. Downloads support HTTP(S) links through DownloadManager, not blob/data URLs,
  POST-only downloads, or a custom background download service. Website cookies are never passed to
  DownloadManager, whose custom headers may be forwarded across redirects. Protected
  files need a direct signed download link or an external browser. Download jobs can
  outlive this Activity.
* Website permissions default to denied in configuration. Enabling a capability only
  permits a subsequent per-origin confirmation and Android runtime permission request.
  Cross-origin frames cannot request camera/microphone/location; only the top HTTPS
  origin is accepted. Permissions are not persisted as website grants.
* JavaScript/CSS injection is restricted to the configured site's exact origin. It
  requires JavaScript enabled and runs after page load. Single-page-app navigation
  may need an observer in user scripts. No native JavaScript bridge is exposed.
* Algorithmic darkening depends on the installed WebView and the site's styling.
  A custom user-agent overrides the desktop toggle. HTTPS-only upgrades navigation
  and rejects HTTP subresources; certificate failures are always cancelled. Explicitly
  disabling HTTPS-only allows cleartext HTTP, while mixed content remains blocked.
* Device biometrics/system PIN protect opening, editing, deleting and overwriting
  protected app configurations. This is a device-auth gate, not encryption or a
  separately stored app PIN. The manager still shows names and URLs. Root/debug access
  and malicious backups are outside this protection. No biometric data is stored.
* Persian/English Compose management and browser controls support RTL. Some system
  dialogs and diagnostic messages use English or the Android system locale.
* Backup JSON includes custom scripts and URLs and is not encrypted. It excludes
  cookies, website data, global theme/language and device credentials. Restore merges
  IDs transactionally after confirmation. It accepts at most 8 MB / 5000 entries as
  parser safety bounds; this does not limit the number of Lite Apps stored locally.

## Android API references

- [AndroidX WebKit profiles](https://developer.android.com/reference/androidx/webkit/Profile)
- [Profile deletion restrictions](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String))
- [Runtime feature detection](https://developer.android.com/reference/androidx/webkit/WebViewFeature)
- [Browsing-data deletion](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)
- [Pinned shortcuts](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts)
- [WebView security guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager)

Locks protect a Lite App entry, not the whole storage profile. An unlocked entry
that deliberately shares its profile (or any entry using shared storage on an older
WebView) can access the same website session. Use supported isolated profiles when
session separation matters. Website and WebView-provider traffic, including Safe
Browsing checks, is separate from LiteSpace, which has no analytics endpoint.

Some websites reject embedded browsers, block embedded sign-in, or require DRM that
the installed WebView cannot provide. Use an external browser for those sites;
LiteSpace cannot override a website's compatibility or authentication policy. Keep
the device OS and WebView provider updated. API 26 compatibility does not supply
security updates to a provider that its vendor no longer maintains.
