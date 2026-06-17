# Stash Tailscale Web UI Android App Release Artifacts

Prepared locally on 2026-06-17.

## Latest local artifacts ready for GitHub Release upload

- Debug APK: `C:\Users\jmswo\Documents\Codex\2026-06-16\create-a-windows-setup-package-for\android-stash-wrapper\release\20260617-124304\app-debug.apk`
- Clean source ZIP: `C:\Users\jmswo\Documents\Codex\2026-06-16\create-a-windows-setup-package-for\android-stash-wrapper\release\20260617-124304\Stash-Tailscale-web-UI-Android-app-source.zip`

## SHA-256

```text
0351F42BCC46922236F1360BE93423B16FB5B5817EF8E92852C409EFCEECE61C  app-debug.apk
838F41C9DB3D0BDC4F5E3999ECDE74321C04AD8801820FE4642E7D89D410A4A0  Stash-Tailscale-web-UI-Android-app-source.zip
```

## Build notes

This build adds mobile playback transcode support in the Android wrapper:

- Intercepts Stash scene stream requests in the WebView.
- Rewrites scene playback to Stash streaming transcode URLs by default.
- Defaults to `STANDARD_HD` / 720p for lower bandwidth use away from the LAN.
- Adds settings to choose 720p, 480p, 1080p, or Original playback.
- Does not store Stash passwords, API keys, or credentials.

## Excluded from source ZIP

The clean source ZIP excludes generated/local tooling folders and secrets:

- `.android-sdk/`
- `.gradle/`
- `.kotlin/`
- `.tools/`
- `app/build/`
- `build/`
- `release/`
- `signing/`
- `local.properties`
- `*.jks`
- `*.keystore`

## Manual GitHub Release step

Create or update a GitHub Release in this repository and upload the two artifacts above:

1. `app-debug.apk`
2. `Stash-Tailscale-web-UI-Android-app-source.zip`

The current Codex GitHub connector can update repository text files but does not expose a release-asset upload endpoint. Use the local files listed above for the binary upload.