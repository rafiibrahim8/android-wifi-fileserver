# WiFi File Share (Android)

Android app to share files from your phone over local Wi-Fi using a browser.

It runs an HTTP server on your device, shows a URL + QR code, and provides a simple web UI for browsing/downloading files.

## Features

- Local file server over Wi-Fi (`NanoHTTPD`)
- Web UI for:
  - Directory browsing with breadcrumb navigation
  - File download
  - Copy direct link per file
  - Optional file upload
  - Optional folder creation
  - Optional ZIP download for directories
- Read-only server mode (download-only)
- Optional basic authentication (user/password)
- Transfer tracking in-app (Home + Transfers tabs)
- Foreground service notification while server is running

## Requirements

- Android Studio (latest stable recommended)
- Android SDK 34
- JDK 17
- Android device/emulator with API 24+

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

## Run

1. Open the project in Android Studio.
2. Install and launch the app.
3. Go to **Settings** and choose a **Root folder**.
4. (Optional) Configure:
   - Anonymous access / auth
   - Allow uploads
   - Read Only Fileserver
   - Allow ZIP download
5. Go to **Home** and tap **START SERVER**.
6. Open the shown URL (or scan QR) from another device on the same Wi-Fi network.

## Important Notes

- Both devices must be on the same network.
- Server URL is currently HTTP.
- In **Read Only Fileserver** mode:
  - Upload is disabled
  - Folder creation is disabled
  - Web UI hides related controls

## Tech Stack

- Kotlin
- AndroidX (AppCompat, Fragment, Preference, Lifecycle)
- `org.nanohttpd:nanohttpd`
- `androidx.documentfile`
- ZXing (`com.google.zxing:core`) for QR generation

