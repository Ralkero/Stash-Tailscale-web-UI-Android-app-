# Stash Tailscale Web UI Android App v1.3.0

Private Android WebView wrapper for accessing Stash through Tailscale.

## v1.3.0 Compact Menu Update

- Changes the top-right Stash overflow menu from a large square grid to a narrow vertical list.
- Keeps each destination's existing Stash icon and label in a compact, touch-friendly row.
- Keeps utility actions grouped in a small footer below the destination list.

## v1.2.0 Navigation Update

- Removes Stash's redundant persistent mobile toolbar from the bottom of the WebView.
- Moves Stash's existing hamburger control to a compact top-right overlay.
- Keeps Images, Markers, Galleries, Performers, Statistics, Settings, Help, Donate, and Logout in the expanded menu.
- Hides Scenes, Groups, Studios, and Tags from that menu because they are already available in the native bottom toolbar.
- Reclaims the page space previously reserved for Stash's bottom toolbar.

## Included

- Kotlin Android WebView wrapper for `http://100.102.126.109:9999/`.
- Bottom navigation for Scenes, Groups, Studios, Tags, and contextual Search.
- Swipe-down native toolbar for Refresh, Status, Settings, and Logout.
- WebView cookie/session support for normal Stash login.
- Stash display mode persistence for scene lists.
- Fullscreen WebView video support.
- Custom Stash app icon.
- Local Android bootstrap/build scripts.

## v1.1.0 Optimization Update

- Fresh installs now use Stash's original direct stream by default; existing playback preferences are preserved.
- Mobile 480p/720p playback uses Stash's native Video.js source selection without an Android byte proxy.
- Repeated navigation uses Stash's SPA router with a full-load fallback.
- Search focus and scene display synchronization perform fewer delayed injections.
- Cookie persistence flushes when the app pauses instead of after every navigation.
- WebView caching continues to honor Stash's HTTP cache headers so upgrades do not leave stale UI assets.
- Release builds now use optimized R8 shrinking.

## Security Notes

- Tailscale remains external and must be connected on Android.
- No router port forwarding is used.
- No public Stash exposure is configured.
- No Stash passwords, API keys, or credentials are stored by the wrapper.
- Android cleartext HTTP is limited to the configured Tailscale IP.

## Artifacts

- `Stash-Wrapper-v1.3.0.apk`: R8-optimized APK signed for seamless updates from earlier versions.
- `Stash-Tailscale-web-UI-Android-app-v1.3.0-source.zip`: clean project source archive.
- `SHA256SUMS.txt`: SHA-256 checksums for both release artifacts.

## Phone Setup

1. Install and connect Tailscale on Android.
2. Install the APK.
3. Open the app.
4. Log in with Stash normally.
