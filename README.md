# LiteSpace Browser

An original, open-source Android browser that turns websites into configurable
Lite Apps. Kotlin · Jetpack Compose / Material 3 · Room · DataStore · Android 8+.
No accounts, analytics, subscription tiers, root access or paid APIs.

## Features

- Unlimited locally stored Lite Apps, search, groups, favorites, custom icons and accents.
- English / Persian interface and RTL; dynamic Material You colors and light/dark modes.
- Per-app JavaScript, CSS/scripts, UA, desktop mode, text size, zoom and darkening.
- Runtime-detected WebView profiles, opt-in shared profiles, temporary private sessions.
- Hosts-based content blocking with an explicit maintained-list update command.
- HTTPS-only mode, third-party cookie controls, profile data/cache clearing.
- Per-origin camera, microphone and geolocation permission prompts; file picker uploads.
- Android downloads, pull-to-refresh, edge navigation, fullscreen video and immersive mode.
- Device biometric/PIN gates and launcher-pinned shortcuts.
- Transactional JSON backup/restore with validation and embedded custom icons.

**Read [platform limitations](docs/LIMITATIONS.md) before relying on privacy or website
compatibility.** Web push notifications are unsupported by Android WebView. Isolation
requires provider support; older WebViews use disclosed shared storage. This project
has no affiliation with Hermit and uses no proprietary code, branding or assets.

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
Downloaded release assets and test results are available on the repository's Releases
page when a build is published.

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

## Structure and testing

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): ownership and dependency flow.
- [`docs/TESTING.md`](docs/TESTING.md): automated checks and device acceptance matrix.
- [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md): exact supported behavior and differences.
- `data/`: Room DAO/entity, local repository, DataStore, blocking-list storage.
- `domain/`: repository contract, URL security policy, domain matching.
- `ui/`: ViewModel, Compose screens, theme, localization and device authentication.
- `web/`: WebView lifecycle, profiles, browser permissions and launcher shortcuts.

The source uses MIT licensing. AndroidX/Kotlin dependencies retain their respective
licenses. The optional StevenBlack list update retains that project's and upstream
sources' licenses; the small bundled seed is original to this project.

## فارسی

لایت‌اسپیس مرورگری متن‌باز برای ساخت برنامه‌های سبک از وب‌سایت‌هاست. زبان فارسی و
چیدمان راست‌به‌چپ از بخش تنظیمات انتخاب می‌شود. همه قابلیت‌ها بدون پرداخت در دسترس
هستند. جداسازی داده به نسخه WebView دستگاه وابسته است و اعلان پوش وب پشتیبانی
نمی‌شود. پیش از انتشار عمومی، آزمون‌های دستگاه و بررسی امنیتی مستندشده را انجام دهید.
