# Stash Tailscale Web UI Android App

Private Android WebView wrapper for accessing a Stash server over Tailscale.

Default server:

```text
http://100.102.126.109:9999/
```

## What this app does

- Opens Stash as a normal Android app through a WebView.
- Keeps access private through the external Tailscale Android app.
- Provides bottom navigation for Scenes, Groups, Studios, Tags, and contextual Search.
- Hides native controls by default; swipe down from the top to reveal Refresh, Status, Settings, and Logout.
- Supports normal Stash login through WebView cookies.
- Stores only the server URL. It does not store Stash passwords, API keys, or credentials.

## Security model

- Tailscale must be connected on the phone.
- No public Stash exposure is configured.
- No router port forwarding is used.
- The manifest requests only `android.permission.INTERNET`.
- Cleartext HTTP is limited to the configured Tailscale IP.

## Local release artifacts

The prepared APK and clean source ZIP are documented in [`RELEASE_ARTIFACTS.md`](RELEASE_ARTIFACTS.md).

## Build locally

From `android-stash-wrapper`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\bootstrap-android-toolchain.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1
```

Debug APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Phone setup

1. Install and connect Tailscale on Android.
2. Install the APK.
3. Open Stash from the app.
4. Log in with Stash normally.
