# Stash Tailscale Web UI Android App

Private Android WebView wrapper for accessing Stash through Tailscale.

## Included

- Kotlin Android WebView wrapper for `http://100.102.126.109:9999/`.
- Bottom navigation for Scenes, Groups, Studios, Tags, and contextual Search.
- Swipe-down native toolbar for Refresh, Status, Settings, and Logout.
- WebView cookie/session support for normal Stash login.
- Stash display mode persistence for scene lists.
- Fullscreen WebView video support.
- Custom Stash app icon.
- Local Android bootstrap/build scripts.

## Security Notes

- Tailscale remains external and must be connected on Android.
- No router port forwarding is used.
- No public Stash exposure is configured.
- No Stash passwords, API keys, or credentials are stored by the wrapper.
- Android cleartext HTTP is limited to the configured Tailscale IP.

## Artifacts

- `app-debug.apk`: sideloadable debug APK for immediate testing.
- `Stash-Tailscale-web-UI-Android-app-source.zip`: clean project source archive.

## Phone Setup

1. Install and connect Tailscale on Android.
2. Install the APK.
3. Open the app.
4. Log in with Stash normally.
