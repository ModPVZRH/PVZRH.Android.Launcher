# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

Dedicated Android launcher and mod manager for Plants Vs Zombies Fusion.

## Target

- Package: `com.LanPiaoPiao.PlantsVsZombiesRH`
- Unity activity: `com.unity3d.player.UnityPlayerActivity`
- Unity version: `2022.3.62f1c1`
- ABI: `arm64-v8a`
- Launcher package: `com.pvzrh.android.launcher`

## Credits

### Projects Used & Referenced

- [BepInEx](https://github.com/BepInEx/BepInEx) — Unity IL2CPP modding framework, the core plugin loader
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Android Unity IL2CPP Runtime Container by Starlight team
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — Custom BepInEx fork for Android
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — Generic BepInEx Android launcher with modpack management
- [dotnet/runtime](https://github.com/dotnet/runtime) — .NET Runtime, built from source with OpenSSL crypto backend
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0, replacing BoringSSL for ARM64 Android crypto
- [Pine](https://github.com/canyie/Pine) — ART Java method hook framework
- [Dobby](https://github.com/jmpews/Dobby) — Native hook framework for ARM64
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — IL2CPP reverse engineering tool

### Lead Developers

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## Guide

- [Contributing](CONTRIBUTING.md) — How to contribute to the project
- [Translate](TRANSLATING.md) — Help translate the launcher into your language
- [Troubleshooting](https://modpvzrh.github.io/troubleshooting) — Common issues and fixes

## Build

There is a CI build in the GitHub Actions page.

Run from this directory:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Each build increments `ci-version.txt` and sets the APK version to:

```text
0.0.1-ci.<number>
```

## Install

For a clean test installation:

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

Uninstalling removes launcher-private data and settings. It does not remove the external PVZRH data directory.

## External Data

Runtime BepInEx data is stored under:

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

The directory contains plugins, configuration, logs, modpacks, and vanilla state.

## Injection Model

The launcher injects BepInEx into the game process using **Pine** (Java method hooking framework):

1. `createPackageContext()` for PVZRH to obtain its class loader and DEX access.
2. Install Pine hooks: bidirectional ClassLoader, Instrumentation, PackageManager, native library, and UnityPlayer.
3. Redirect the game activity to a manifest-registered `StubActivity` via `Instrumentation.execStartActivity` hook.
4. `Instrumentation.newActivity` restores the real game activity class and original Intent.
5. `Activity.attachBaseContext` hook wraps the Context with a three-way `CustomContextWrapper`:
   - **Game resources** (Assets, Resources, Theme) → PVZRH package context
   - **File/storage** (getFilesDir, SharedPreferences) → launcher Application
   - **Window services** (getDisplay, getSystemService) → original Activity base Context
6. `ClassLoader.findLibrary()` hook redirects native .so loading: game libs from game APK, fusion libs from launcher, .NET/il2cpp/unity libs from data directory.
7. `UnityPlayer` constructor hook sets the activity field and shows injection overlay.
8. `UnityPlayer.kill()` hook blocks the first call for 5 seconds to survive integrity checks.

All hooks are installed from Kotlin/Java — no native `libmain.so` bootstrap required.

## Debug Snapshots

After a crash, the next launcher start saves diagnostics under:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

Snapshots include launcher logs, logcat, crash logcat, package/activity state, process exit information, and available BepInEx logs.
