# Karter

Android head-unit style home launcher (landscape, large touch targets, driving dashboard).

## Install (debug)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First setup

1. Open **Karter** and complete the onboarding wizard.
2. Set **Karter** as the default home app when prompted (required to continue).
3. Grant optional permissions as needed:
   - **Location** — weather and GPS speed on the dashboard
   - **Contacts / call log / phone** — built-in Phone screen
   - **Notification access** — Now playing track title and album art
   - **Nearby devices (Bluetooth)** — connected device names on the dashboard

## Main features

- **Dashboard** — media controls, speed, weather, Bluetooth, volume
- **Dock** — maps, music, phone, settings (apps can be customized in Settings)
- **App drawer** — all launcher apps; hide apps from Settings → App drawer → **Hidden apps**
- **Phone** — recents, searchable contacts, dial pad

## Settings

- Theme, permissions, dock shortcuts, driving assistants (seatbelt TTS, speed heat tint)
- **Privacy & data** — what the app accesses
- **Setup assistant** — run onboarding again without losing shortcuts or hidden apps

## Target devices

Designed for **landscape** Android head units and tablets. Phone portrait is supported but not optimized.

## Privacy

Location coordinates are sent only to [Open-Meteo](https://open-meteo.com/) for weather. Contacts and call history stay on device. See in-app **Settings → Privacy & data**.

## Website (Play Store)

Static pages for Google Play listing links live in [`website/`](website/). They are **not** included in the APK — host them separately (e.g. GitHub Pages). See [`website/README.md`](website/README.md).

## Build

- `minSdk` 26, `compileSdk` 35
- Kotlin + Jetpack Compose + Material 3
