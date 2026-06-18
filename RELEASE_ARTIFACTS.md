# Stash Tailscale Web UI Android App Release Artifacts

## v1.1.0

- APK: `Stash-Wrapper-v1.1.0.apk`
- Source: `Stash-Tailscale-web-UI-Android-app-v1.1.0-source.zip`
- Checksums: `SHA256SUMS.txt`

```text
5210471C5107B3A1A2531BC21C84D840749ABF7F148940CC890D12C4317D4FA8  Stash-Wrapper-v1.1.0.apk
910E60F62F6EC86222CF56D468B22389A7D3DCD4F6C5CE8070DE2AA3EB15A439  Stash-Tailscale-web-UI-Android-app-v1.1.0-source.zip
```

The APK is an R8-optimized release build signed with the certificate used by v1.0.0, allowing an in-place update that preserves app data. The source archive excludes Android SDKs, build caches, generated output, screenshots, signing material, and local configuration.

## Security

- Stash remains reachable only through Tailscale.
- No router forwarding or public exposure is configured.
- No Stash credentials or signing passwords are included.
- Android cleartext HTTP remains restricted to `100.102.126.109`.
