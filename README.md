# LiteSpace Browser

An original, open-source Android browser that turns websites into configurable
Lite Apps. Kotlin · Jetpack Compose / Material 3 · Room · DataStore · Android 8+.
No accounts, analytics, subscription tiers, root access or paid APIs.

<img src="docs/screenshots/design-library-light.png" width="250" alt="LiteSpace collection in light mode" />
<img src="docs/screenshots/design-library-persian-dark.png" width="250" alt="Persian RTL collection in dark mode" />

Real Android screenshots with temporary test-created Lite Apps. See the
[design gallery](docs/DESIGN.md) for settings and the app editor.

## Features

- Unlimited locally stored Lite Apps, search, groups, favorites, custom icons and accents.
- Adaptive card collection, search/group/favorite filters, bottom navigation and a keyboard-aware editor.
- English / Persian interface, bundled Vazirmatn typography and RTL; dynamic Material You colors and light/dark modes.
- Per-app JavaScript, CSS/scripts, UA, desktop mode, text size, zoom and darkening.
- Runtime-detected WebView profiles, opt-in shared profiles, temporary private sessions.
- Hosts-based content blocking with an explicit maintained-list update command.
- HTTPS-only mode, third-party cookie controls, profile data/cache clearing.
- Per-origin camera, microphone and geolocation permission prompts; file picker uploads.
- Android downloads, pull-to-refresh, edge navigation, fullscreen video and immersive mode.
- Device biometric/PIN gates and launcher-pinned shortcuts.
- Reader with four palettes, text/font controls, local sanitized articles and shared-URL entry.
- Android printing/PDF, bookmarks, multiple tags, tracking parameter removal.
- Shared automatic/manual script library with JavaScript file import and per-app selection.
- Selectable custom HOSTS sources with opt-in weekly updates.
- Opt-in RSS/Atom and HTML/CSS monitors with native grouped notifications.
- Default-browser links, configurable site search/share templates, image controls and Save-Data hint.
- Five fixed isolated color sandboxes on Android 9+, including older WebViews.
- Versioned JSON backup/restore with custom icons, tool definitions and global preferences.

**Read [platform limitations](docs/LIMITATIONS.md) before relying on privacy or website
compatibility.** Web push notifications are unsupported by Android WebView. Isolation
requires provider support; older WebViews use disclosed shared storage. This project
has no affiliation with Hermit and uses no proprietary code, branding or assets.
It is not an exact Hermit clone or a verified replacement for every premium feature;
see the [feature scope and parity audit](docs/FEATURE-PARITY.md) for concrete gaps.

## Build and install

Install Android Studio, JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Open
this directory in Android Studio and let Gradle sync. Set `ANDROID_HOME` or create an
untracked `local.properties` containing `sdk.dir=/path/to/Android/Sdk`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle 8.10.2 is pinned by the included wrapper. The debug APK uses a development
signing key and is suitable for evaluation; debug apps are not a production release.
Download the [latest published build](https://github.com/zarya8096-max/LiteSpaceBrowser/releases).
The 1.2 source adds the [officially compared Premium feature categories](docs/HERMIT-COMPARISON.md).
See the [interface redesign](docs/DESIGN.md) and [deep debugging report](docs/DEBUGGING.md) for the security, data-integrity
and lifecycle corrections in this version.

## Signed release

Use Android Studio **Build → Generate Signed App Bundle or APK → APK → release**.
Create your own signing key outside the repository and store its password securely.
Keep the key for future updates. Never commit keystores, passwords or access tokens.

For command-line signing:

```sh
./gradlew assembleRelease
# Create a key once; keytool prompts for passwords.
keytool -genkeypair -keystore /secure/litespace.jks -alias litespace \
  -keyalg RSA -keysize 3072 -validity 10000
# Use your SDK's build-tools/35.0.0 binaries:
zipalign -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks /secure/litespace.jks --ks-key-alias litespace \
  --out litespace-release.apk aligned.apk
apksigner verify --verbose litespace-release.apk
```

A release signed with your key cannot update the differently signed debug app; export
configuration before uninstalling it. For Play distribution, produce and sign an AAB
and complete current store privacy/permission requirements separately.

See the [verification history](docs/VERIFICATION.md) and [testing instructions](docs/TESTING.md).
Each release must list its actual completed checks; earlier results do not certify new features.

## Structure and testing

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): ownership and dependency flow.
- [`docs/TESTING.md`](docs/TESTING.md): automated checks and device acceptance matrix.
- [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md): exact supported behavior and differences.
- `data/`: Room DAO/entity, local repository, DataStore, blocking-list storage.
- `domain/`: repository contract, URL security policy, domain matching.
- `ui/`: ViewModel, Compose screens, theme, localization and device authentication.
- `web/`: WebView lifecycle, profiles, browser permissions and launcher shortcuts.

The bundled Vazirmatn 33.003 fonts use the SIL Open Font License; their copyright
and license are included in `app/src/main/assets/licenses/Vazirmatn-OFL.txt`.

Mozilla Readability 0.6.0 is bundled under its MIT license, retained in
`app/src/main/assets/licenses/Readability-LICENSE`. Jsoup uses the MIT license.

The source uses MIT licensing. AndroidX/Kotlin dependencies retain their respective
licenses. The optional StevenBlack list update retains that project's and upstream
sources' licenses; the small bundled seed is original to this project.

## فارسی

لایت‌اسپیس مرورگری متن‌باز برای ساخت برنامه‌های سبک از وب‌سایت‌هاست. زبان فارسی و
چیدمان راست‌به‌چپ از بخش تنظیمات انتخاب می‌شود. همه قابلیت‌ها بدون پرداخت در دسترس
هستند. جداسازی داده به نسخه WebView دستگاه وابسته است و اعلان پوش وب پشتیبانی
نمی‌شود. پیش از انتشار عمومی، آزمون‌های دستگاه و بررسی امنیتی مستندشده را انجام دهید.
