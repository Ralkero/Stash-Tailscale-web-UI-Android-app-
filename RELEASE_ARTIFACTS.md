# Stash Tailscale Web UI Android App Release Artifacts

Prepared locally on 2026-06-17.

## Local artifacts ready for GitHub Release upload

- Debug APK: `C:\Users\jmswo\Documents\Codex\2026-06-16\create-a-windows-setup-package-for\android-stash-wrapper\release\20260617-091154\app-debug.apk`
- Clean source ZIP: `C:\Users\jmswo\Documents\Codex\2026-06-16\create-a-windows-setup-package-for\android-stash-wrapper\release\20260617-091154\Stash-Tailscale-web-UI-Android-app-source.zip`
- Release notes: `C:\Users\jmswo\Documents\Codex\2026-06-16\create-a-windows-setup-package-for\android-stash-wrapper\release\20260617-091154\RELEASE_NOTES.md`

## SHA-256

```text
488373C22DF6F75B1603DD65CA8363AC1F344DBD06D5CB379978490CFF3A5255  app-debug.apk
630CD444DF9EEFEEC18E593877581DDD4BE4F97BBF5D0CF33F97EF4286D794B7  Stash-Tailscale-web-UI-Android-app-source.zip
```

## Notes

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

Create a new GitHub Release in this repository and upload the two artifacts above:

1. `app-debug.apk`
2. `Stash-Tailscale-web-UI-Android-app-source.zip`

Use `RELEASE_NOTES.md` as the release description.
