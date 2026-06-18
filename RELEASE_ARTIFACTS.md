# Stash Tailscale Web UI Android App Release Artifacts

## v1.5.0

- APK: `Stash-Wrapper-v1.5.0.apk`
- Source: `Stash-Tailscale-web-UI-Android-app-v1.5.0-source.zip`
- Checksums: `SHA256SUMS.txt`

```text
2A0C54D9717F6E046BBB225093D2822F51EB9E8177573E1A400F23B671F98155  Stash-Wrapper-v1.5.0.apk
24F9CF679D5487C90A25953EF19EA5729F2657FBF1D1E367189C4C6A7DAFF8E6  Stash-Tailscale-web-UI-Android-app-v1.5.0-source.zip
```

This update consolidates WebView enhancement scheduling, removes a rapid-tab race, routes contextual Search through the SPA, and adds native ripple, route-selection, dark-load, and progress feedback. The repeat device benchmark improved janky frames from 0.81% to 0.34% and p95 frame time from 10 ms to 8 ms.

## v1.4.0

- APK: `Stash-Wrapper-v1.4.0.apk`
- Source: `Stash-Tailscale-web-UI-Android-app-v1.4.0-source.zip`
- Checksums: `SHA256SUMS.txt`

```text
6A61692ED9B5ECFE8DC7A243C0A13638F904B01C6624D242E8FE16D64164B999  Stash-Wrapper-v1.4.0.apk
5D9E30AD0AD81C9D20B3F376627A8CFDFA58DACB0CCAB8E99CFE84AF9C65CE6D  Stash-Tailscale-web-UI-Android-app-v1.4.0-source.zip
```

This update tightens the overflow panel around its destination rows and promotes Settings to a full gear-and-label list entry.

## v1.3.0

- APK: `Stash-Wrapper-v1.3.0.apk`
- Source: `Stash-Tailscale-web-UI-Android-app-v1.3.0-source.zip`
- Checksums: `SHA256SUMS.txt`

```text
4E86B47472FA9A310F72A3111F2C0E1BDBECB8AE1101AD3AC78AD8A9B871FECF  Stash-Wrapper-v1.3.0.apk
A770A7CCFB3B8A3E158BC011CDB3B5A205CC6080699111C2ECCD21C9D715B54D  Stash-Tailscale-web-UI-Android-app-v1.3.0-source.zip
```

This update changes the top-right overflow menu from a large grid to a narrow, touch-friendly vertical list while retaining Stash's original icons and routing behavior.

## v1.2.0

- APK: `Stash-Wrapper-v1.2.0.apk`
- Source: `Stash-Tailscale-web-UI-Android-app-v1.2.0-source.zip`
- Checksums: `SHA256SUMS.txt`

```text
F15973F11554A4A91AFCEFA31B77498606E136EF16599F81F6CCB84F3E12F822  Stash-Wrapper-v1.2.0.apk
F1697FED638EA20B327102D4ECB8D3C6EC84CB67574CE710AD4A0D5F2E86BAC1  Stash-Tailscale-web-UI-Android-app-v1.2.0-source.zip
```

This update removes Stash's redundant mobile bottom toolbar, relocates its native hamburger toggle to the top-right, and filters destinations already present in the wrapper's native bottom navigation.

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
