# PVZRH Android Launcher

Dedicated Android launcher and mod manager for PlantsVsZombiesRH.

## Target

- Package: `com.LanPiaoPiao.PlantsVsZombiesRH`
- Unity activity: `com.unity3d.player.UnityPlayerActivity`
- Unity version: `2022.3.62f1c1`
- ABI: `arm64-v8a`
- Launcher package: `com.pvzrh.android.launcher`

## Credits
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore)
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher)
- [BepInEx.Android](https://github.com/NextBep/BepInEx.Android)

## Build

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

The launcher follows the FusionCore model:

1. Create a package context for PVZRH and obtain its class loader.
2. Install class-loader, instrumentation, package-manager, Activity, and native-library hooks.
3. Start the registered `StubActivity`.
4. Restore `UnityPlayerActivity` through `Instrumentation.newActivity` in the launcher process.
5. Wrap only `attachBaseContext` with a three-way Context wrapper.
6. Keep the UnityPlayer constructor argument as the real Activity.

The Context wrapper routes game resources to PVZRH, launcher storage to the launcher Application, and window services to the original Activity base Context.

## Debug Snapshots

After a crash, the next launcher start saves diagnostics under:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

Snapshots include launcher logs, logcat, crash logcat, package/activity state, process exit information, and available BepInEx logs.
