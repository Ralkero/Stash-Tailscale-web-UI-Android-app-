# Android Stash Wrapper

Private Android WebView wrapper for the Stash instance reachable over Tailscale.

Default server:

```text
http://100.102.126.109:9999/
```

## Security model

- Tailscale remains external and must be connected on the phone.
- The app stores only the Stash server URL.
- The app stores only one playback preference: whether to prefer Stash's lower-resolution stream endpoints.
- It does not store Stash passwords, API keys, or cookies outside the normal Android WebView cookie store.
- It does not use `addJavascriptInterface`.
- The manifest requests only `android.permission.INTERNET`.
- Cleartext HTTP is allowed only for `100.102.126.109`; other custom servers must use HTTPS.

## Mobile playback

Fresh installs default to Stash's original direct stream. Existing installs keep their saved playback preference during an update.

While a video is open, tap the gear immediately to the left of playback speed to switch quality. The menu always includes Source, then only the transcode resolutions at or below that video's source resolution. The wrapper keeps the current playback position and play/pause state while changing quality. A manual choice remains active for later videos during the current app session, capped to each video's source resolution, and takes priority over the automatic mobile-playback preference.

For slower remote connections, open the hidden toolbar, tap Settings, and enable "Prefer mobile playback transcodes." The wrapper selects Stash's native MP4 source directly inside its Video.js player instead of proxying video bytes through Android:

```text
/scene/<id>/stream.mp4?resolution=STANDARD_HD
```

The Settings screen can still choose the automatic startup preference. The in-player gear exposes all requested resolutions even when Stash hides higher choices because of its global maximum-transcode setting. If Stash cannot construct a stream for a scene, its normal source remains available. All traffic remains private inside Tailscale.

## Fullscreen and video sizing

The Video.js fullscreen button enters Android immersive fullscreen and hides both system bars until you swipe from an edge or exit fullscreen. The size button immediately to the left of fullscreen offers Fit, Fill / Crop, Zoom 110%, Zoom 125%, and Stretch. The selected size mode remains active for later videos during the current app session.

Each fresh app launch opens the first Scenes page. Login cookies and scene display mode are still retained, but stale pagination and the previously visited detail page are not restored.

## Gallery gestures

Open an image in Stash's lightbox, then swipe left for the next image or right for the previous image. The wrapper only handles deliberate horizontal single-finger swipes inside the lightbox image area; vertical motion, taps, controls, and pinch gestures keep their normal behavior.

Configure Stash's native transcode settings from this project:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\configure-stash-mobile-transcodes.ps1 -Apply
```

Queue a small validation job for one scene:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\configure-stash-mobile-transcodes.ps1 -Apply -GenerateSceneId 404
```

Queue the whole library only when the PC can run for a long time and you have enough disk space:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\configure-stash-mobile-transcodes.ps1 -Apply -GenerateAll
```

## Build

After the Android command-line tools, SDK, JDK, and Gradle wrapper are available:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

If SDK package installation stops at license acceptance, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\accept-android-sdk-licenses.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\bootstrap-android-toolchain.ps1
```

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Private release APK

To create a local release keystore and signed APK without storing passwords in files:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1
```

The script prompts for keystore/key passwords. The keystore is created under `signing\`, which is ignored by `.gitignore`.

Release APK:

```text
app\build\outputs\apk\release\app-release.apk
```

## Phone install

1. Install/open Tailscale on Android.
2. Confirm the phone is connected to the same tailnet.
3. Install the APK.
4. Open the Stash app.
5. Log in with Stash's normal login screen.

## Runtime controls

- The persistent bottom toolbar opens Scenes, Groups, Studios, Tags, and contextual Search.
- Stash's hamburger menu is pinned to the top-right and presents the remaining destinations plus Settings as a compact icon list, with secondary utility actions below.
- `Refresh`: reloads the WebView.
- `Status`: checks whether the Stash URL is reachable.
- `Settings`: changes the server URL.
- `Logout`: clears WebView cookies and reloads Stash.

## Responsiveness

- Bottom destinations and contextual Search use Stash's SPA router instead of reloading the WebView.
- Wrapper DOM enhancements share one frame-coalesced scheduler and scope observation to the navbar and active video player.
- Full page loads use a thin progress indicator; SPA transitions remain immediate.
