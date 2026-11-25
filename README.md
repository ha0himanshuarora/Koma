# Koma

A tile-grid Android launcher in Kotlin and Jetpack Compose. Lightweight by design.

## Status

Phase 3. Every component in the design doc is built, the home screen is editable, and colours follow the theme or wallpaper.

Built:
- Home: 4-column tile grid. Long-press to edit; tap a tile for size, colour, clock style, order, remove; hold and drag to reorder; + ADD for any component.
- Components: clock (6 styles), date, kanji, weather, music, calendar (1x1 to 4x4, month views), search (1x1 to 4x2), favourite contacts, recent calls, app group, device status, quick toggles (torch, Wi-Fi, Bluetooth, DND), timer, tasks, note, photo, screen time, calculator, Focus Lock, Android widgets, app shortcuts.
- Koma Settings (pinned in the drawer): edit layout, colours, fonts and clock, app icons, components, search, calendar, built-in apps, Focus Lock, system.
- Colours: 7 presets, follow wallpaper (Material You style, live), custom seed; tonal, vibrant, expressive and mono variants. Nothing is hard-coded.
- App icons: 11 styles, 5 shapes, icon packs, forced icons on third-party apps (monochrome or adaptive layer, letter mark fallback).
- Built-in apps: Notes, Tasks, Timer and stopwatch, Calculator, Focus (timer with slide to start, bags of allowed apps, routines, stats, slide to exit, +15 min, strict mode).
- Left-handed mode: drawer on the right, alphabet slider on the left. Optional seconds on clock tiles.
- Drawer: frosted panel over the live wallpaper, letter rail, search with web fallback, long-press for app info.

Not built or not tested: hourly weather strip, photo rotation from EXIF, timer survives process death, per-app icon overrides. The whole project was written without an Android SDK and has not been compiled by me. Send build errors and I will fix them.

Focus Lock blocking outside Koma needs the Koma accessibility service turned on (Settings > Accessibility). Screen time needs Usage access. Music needs Notification access.

## Build

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run the `app` configuration on a device or emulator (API 26+).
3. Press Home and choose Koma as the default launcher.

If you ran an earlier version, the saved layout is replaced once by the new default layout.

## Why it stays small

- Only Compose UI and Foundation are used. No Material library, no icon pack, no Room, no image loader, no DI framework.
- Layout is stored with the platform's `org.json` in a small file; tile positions are not stored.
- Weather uses `HttpURLConnection`; no networking library.
- Release builds use R8 minify and resource shrinking.

## Layout

- `data/` model, prefs, JSON stores, apps, icon engine, focus engine, calendar, device, widget host, weather, media
- `ui/` theme, kit, glyphs, grid, tiles, drawer, settings, built-in apps, edit sheets, home screen, state
- `MainActivity.kt` HOME entry point and package-change receiver


## Release build

1. Create a keystore once: `keytool -genkeypair -v -keystore koma-release.jks -alias koma -keyalg RSA -keysize 2048 -validity 10000`. Back it up; losing it means you can never update the app.
2. Add `keystore.properties` in the project root (it is git-ignored):
   ```
   storeFile=/absolute/path/koma-release.jks
   storePassword=...
   keyAlias=koma
   keyPassword=...
   ```
3. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every release.
4. Build: `./gradlew bundleRelease` (Play Store, `app/build/outputs/bundle/release/`) or `./gradlew assembleRelease` (APK).
5. Test the release build on a real device first: R8 is on, so run through every screen once.

The release build turns on R8 minify and resource shrinking, keeps only English resources, drops the dependency-info blob and strips debug logging.

## Fonts

Bundled fonts are open source (SIL OFL) and trimmed to Latin characters: Archivo Black, Jost, Space Grotesk, Sora, JetBrains Mono, DM Serif Display, Lexend, Fuzzy Bubbles, Gaegu, Josefin Sans, Kalam, Manrope, Merienda, Montserrat, Nothing You Could Do, Nunito, Pangolin, Righteous, Sour Gummy and Tsukimi Rounded. Users can also import their own .ttf / .otf in Settings > Fonts.
