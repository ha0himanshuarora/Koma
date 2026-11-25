# Koma

Koma is a tile-based Android launcher built with Kotlin and Jetpack Compose. Your home screen is a grid of live tiles you arrange yourself, colours follow your wallpaper or a theme you pick, and a set of small built-in apps (clock, notes, reminders, calculator, focus) comes along without adding a pile of dependencies.

- Package: `app.koma.launcher`
- Min SDK 26 (Android 8.0), target SDK 36
- Compose UI and Foundation only. No Material library, no Room, no image loader, no DI framework.

## Features

### Home screen

- Tile grid with a configurable number of columns and rows. Long-press to edit.
- In edit mode: tap a tile to select it, tap again for its options (size, colour, style, order), hold and drag to move, drag the handles to resize, and use the bar at the bottom to add components, change the grid or finish.
- Stacks: drop one tile onto another of the same size to make a stack, then swipe through its components (vertically, or horizontally if set). Stacks can be flipped in edit mode too.
- Home gestures (double tap, long press, swipes) can be mapped to actions, including system actions through the accessibility service.
- Swipe up on the home screen to open search.
- Landscape layouts keep tiles square and flow them sideways.
- Left-handed mode puts the drawer on the right and the alphabet rail on the left.

### Components

Every component comes in several sizes and adapts its layout to the one you choose.

| Group | Components |
| --- | --- |
| Time | Clock (six styles), Date, Words, Countdown, Alarms, Timer, Stopwatch |
| Information | Weather, Calendar (up to a month view), Phone status, Screen time, Bluetooth devices |
| Media | Now playing (several styles), Photo |
| People | Favourite contacts, Recent calls |
| Productivity | Reminders, Notes, Focus, Calculator |
| Shortcuts | Search, App, App group, All apps, Koma apps, Quick switches (torch, Wi-Fi, Bluetooth, Do Not Disturb), Android widgets |
| Layout | Stack, Gap |

Lists on the Reminders and Calendar tiles scroll when there is more than fits.

### App drawer

- Frosted panel over the live wallpaper, with a letter rail and fast search.
- Search falls back to the web with the engine you choose.
- Folders, hidden apps, long-press app menu, and per-app daily time limits.
- Optional height and panel styles.

### Search

Apps and people first, then the web. Opens from the drawer, a tile, or an upward swipe on the home screen.

### Theme and look

- Presets, a colour that follows your wallpaper (Material You style, updating live), or a custom seed colour. Tonal, vibrant, expressive and mono variants.
- Light, dark or follow the system.
- Tile styles, ground (background) options, text size.
- App icon styles, shapes, icon packs and forced icons for apps that do not supply a themed one.
- Save the whole setup (layout, theme, fonts, icons, drawer) as a named look and switch between looks.
- 20 bundled fonts, or import your own `.ttf` / `.otf`.

### Built-in apps

Open them from the Koma apps hub (a tile or the pinned drawer entry).

- **Clock:** alarms with groups, labels, repeat days and snooze; world clock with a time converter; stopwatch with laps; timer; and Focus. Alarms and timers ring over the lock screen and a power-button press on the lock screen closes them.
- **Focus:** a timer you slide to start, bags of allowed apps, routines, stats, +15 minutes, slide to exit, and a strict mode that locks Koma's settings. Blocking apps outside Koma needs the accessibility service.
- **Notes:** headings, colours, archive and a bin.
- **Reminders:** tasks with dates and times, alerts at the time you set, late and open counts.
- **Calculator:** basic, scientific and programmer modes, plus unit and currency conversion with live rates.

### Settings

Koma Settings (pinned in the drawer) has pages for layout, looks, theme, fonts, app icons, the drawer, folders, hidden apps, app limits, home gestures, weather, music, calendar, search, and system permissions and defaults. About shows the version, the privacy policy, credits and how to reach the author.

## Permissions

Nothing is asked for until a feature needs it. Refusing a permission just leaves the matching tile empty.

| Permission | Used for |
| --- | --- |
| Contacts, Call log | People and Recent calls tiles, contact search |
| Calendar | Calendar tile |
| Approximate location, Internet | Weather tile (rounded coordinates only), web search |
| Usage access | Screen time tile, app limits |
| Notification access | Now playing tile (reads and controls media sessions) |
| Accessibility service | Focus Lock and system-action gestures |
| Exact alarms, full-screen intent, notifications, wake lock | Alarms, timers and reminders |
| Bluetooth connect | Bluetooth tile |
| Set wallpaper | Setting the wallpaper from a gallery picture |

The full policy is in `PRIVACY.md` and in Settings > About > Privacy policy. Koma has no account, no analytics and no ads.

## Getting started

Requirements: a recent Android Studio, JDK 17 or newer (the one bundled with Android Studio is fine), and an Android 8.0+ device or emulator.

1. Clone the repository and open the folder in Android Studio. Let Gradle sync.
2. Run the `app` configuration on a device or emulator.
3. Press Home and choose Koma as the default launcher. Koma asks the first time and whenever it is not the default.

From the command line: `./gradlew assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.

A few features need a switch in system settings, which Koma links to: Usage access (screen time), Notification access (music), and the Koma accessibility service (Focus Lock outside Koma, system gestures).

## Release build

1. Create a keystore once: `keytool -genkeypair -v -keystore koma-release.jks -alias koma -keyalg RSA -keysize 2048 -validity 10000`. Back it up. Losing it means you can never update the app.
2. Add `keystore.properties` in the project root. It is git-ignored.
   ```
   storeFile=koma-release.jks
   storePassword=...
   keyAlias=koma
   keyPassword=...
   ```
3. Raise `versionCode` (and `versionName`) in `app/build.gradle.kts` for every release.
4. Build: `./gradlew bundleRelease` for the Play Store (`app/build/outputs/bundle/release/`) or `./gradlew assembleRelease` for an APK.
5. Test the release build on a real device. R8 minify and resource shrinking are on, so open every screen once.

Release builds keep only English resources, drop the dependency-info blob and strip debug logging.

## Project layout

```
app/src/main/java/app/koma/launcher/
  MainActivity.kt      HOME entry point, package-change receiver
  data/                model, preferences, JSON stores, apps, icon engine, focus engine,
                       calendar, weather, media, alarms and timers, reminders, widget host
  ui/                  theme and kit, glyphs, tile grid, tiles, drawer, search, settings,
                       built-in apps (clock, focus, notes, tasks, calculator), edit UI,
                       home screen and state
app/src/main/res/      fonts, drawables, XML config
PRIVACY.md             privacy policy
```

State lives in `HomeState`, which holds the layout, preferences and navigation and saves after every change. Screens are plain composables that read it.

## Design notes

- **Small by design.** Only Compose UI and Foundation are used. Layout is stored as a small JSON file through the platform's `org.json`, and tile positions are not stored: tiles are packed in list order, which keeps the saved layout tiny and always valid.
- **No hard-coded colours.** Everything is drawn from the current palette, so any theme or wallpaper recolours the whole launcher, including the built-in apps.
- **Networking** is a single `HttpURLConnection` call for weather (Open-Meteo) and currency rates; no networking library.
- **Everything stays on the device.** Settings, notes, tasks and history live in the app's private storage, and backups are off.

## Fonts

Bundled fonts are open source (SIL OFL) and trimmed to Latin characters: Archivo Black, Jost, Space Grotesk, Sora, JetBrains Mono, DM Serif Display, Lexend, Fuzzy Bubbles, Gaegu, Josefin Sans, Kalam, Manrope, Merienda, Montserrat, Nothing You Could Do, Nunito, Pangolin, Righteous, Sour Gummy and Tsukimi Rounded.

## Privacy

The policy is in `PRIVACY.md` and inside the app (Settings > About). In short: no account, no analytics, no ads, nothing uploaded. The only data that leaves the phone is the rounded location sent to Open-Meteo for the Weather tile and currency-rate requests.

## Author

Made by ha0himanshuarora. Contact: ha0himanshuarora@gmail.com
