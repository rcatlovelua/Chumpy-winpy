# Chumpy for Windows — early port

Windows is a separate Kotlin/JVM desktop application in `windows-app`. The original Android app in `:app` is kept intact; this branch does not replace or modify the upstream project.

## Implemented in this milestone

- Import and validation of supported share links using the ported Chumpy parser (VMess, VLESS, Trojan, Shadowsocks, SOCKS, Hysteria2 and WireGuard).
- Local profile-link storage in `%LOCALAPPDATA%\\Chumpy\\profiles.txt`.
- HTTP(S) subscription URLs, manual refresh, and parsing of plain-text or Base64 subscription bodies.
- QR decoding from image files (PNG/JPG/BMP) using ZXing.
- Xray config generation for the local SOCKS/system-proxy mode.
- sing-box config generation for Windows TUN mode, including a TUN inbound and common outbound protocols/transports.
- Launch/stop of the external native engine; the Xray process is started hidden through PowerShell and its child PID is tracked for disconnect.
- The Windows CI build downloads the official Xray-core Windows x64 release and bundles `xray.exe` as an app resource; on first launch it is copied to `%LOCALAPPDATA%\\Chumpy\\bin\\xray.exe`.
- System-proxy mode backs up the current user's Windows proxy registry values and restores them on disconnect or normal app close.
- GitHub Actions builds the Windows distribution and packages a self-contained Windows app image with `Chumpy.exe` and uploads it as a workflow artifact.

## Current limitations

This is an active port, **not yet feature-equivalent to Android**. Live camera QR scanning, full profile editing, subscription update policies, traffic counters/statistics, themes, and the remaining Android settings/screens still need Windows implementations.

The Android `libv2ray.aar`, `VpnService`, Room database, and Android UI are not Windows binaries. Windows uses external native executables:

- System proxy: bundled Xray-core `xray.exe`, with the generated local HTTP proxy inbound on `127.0.0.1:10809`. You can still select another compatible `xray.exe` manually.
- TUN: sing-box `sing-box.exe`, with the generated TUN config. Run Chumpy as Administrator and install/enable the official Wintun driver as required by your sing-box build.

Xray and sing-box JSON formats are different. Do not swap their config files. Some less common transports are intentionally rejected by the first TUN config generator rather than producing a broken config.

The `xray/xray_no_window.cmd`, `.ps1`, and `.vbs` files are standalone manual-launch helpers. They expect a separate `xray.exe` and a file named `config.json`; the desktop app does not invoke them because it creates a per-profile `xray-windows.json` and manages the engine process itself. The desktop app launches Xray hidden directly and does not need these wrappers.

## Build (JDK 21)

```powershell
.\\gradlew.bat -p windows-app run
.\\gradlew.bat -p windows-app installDist
```

The CI distribution is published under **Actions → Windows desktop build → Artifacts → Chumpy-Windows**.
