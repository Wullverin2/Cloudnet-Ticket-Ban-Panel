# TCCB Panel Android App

Native Android shell for the TicketConsoleCloudBan web panel.

The app loads the existing panel in a WebView, stores the panel URL locally, and enables JavaScript plus DOM storage. It uses the same login flow as the browser panel. New panel sessions are stored server-side in the panel store, so the login survives panel restarts.

## Develop

1. Open `android-app` in Android Studio.
2. Install Android SDK 36 if Android Studio asks for it.
3. Start the app on an emulator or device.
4. Default emulator URL: `http://10.0.2.2:8088`.

On a real device, enter the reachable panel domain or server IP, for example `https://panel.example.de`.

## Build

```bash
gradle :app:assembleDebug
```

This project does not commit a Gradle wrapper yet. Use Android Studio's build action or an installed Gradle 9.4.1 setup with Java 17.
