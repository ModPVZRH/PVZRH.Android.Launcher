# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

植物大戰殭屍融合版的 Android 啟動器與模組管理器。

## 目標

- 遊戲套件名稱：`com.LanPiaoPiao.PlantsVsZombiesRH`
- Unity Activity：`com.unity3d.player.UnityPlayerActivity`
- Unity 版本：`2022.3.62f1c1`
- ABI：`arm64-v8a`
- 啟動器套件名稱：`com.pvzrh.android.launcher`

## 致謝

### 使用和引用的專案

- [BepInEx](https://github.com/BepInEx/BepInEx) — Unity IL2CPP 模組框架，核心插件載入器
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Starlight 團隊的 Android Unity IL2CPP 運行時容器
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — 自訂 Android BepInEx 分支
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — 通用 BepInEx Android 啟動器，支援整合包管理
- [dotnet/runtime](https://github.com/dotnet/runtime) — .NET 運行時，使用 OpenSSL 加密後端從原始碼建置
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0，替代 BoringSSL 用於 ARM64 Android 加密
- [Pine](https://github.com/canyie/Pine) — ART Java 方法 Hook 框架
- [Dobby](https://github.com/jmpews/Dobby) — ARM64 原生 Hook 框架
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — IL2CPP 逆向工程工具

### 主要開發者

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## 指南

- [貢獻指南](CONTRIBUTING.md) — 如何參與專案貢獻
- [翻譯指南](TRANSLATING.md) — 幫助將啟動器翻譯成你的語言
- [故障排除](https://modpvzrh.github.io/zh/troubleshooting) — 常見問題與解決方案

## 建置

GitHub Actions 頁面有 CI 建置。

在專案根目錄執行：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 輸出路徑：

```text
app/build/outputs/apk/debug/app-debug.apk
```

每次建置會遞增 `ci-version.txt`，APK 版本格式為：

```text
0.0.1-ci.<number>
```

## 安裝

全新測試安裝：

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

解除安裝會清除啟動器的私有資料和設定，但不會刪除外部 PVZRH 資料目錄。

## 外部資料

執行時 BepInEx 資料儲存在：

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

該目錄包含外掛程式、設定、日誌、整合包和原版狀態。

## 注入模型

啟動器使用 **Pine**（Java 方法 Hook 框架）將 BepInEx 注入遊戲行程：

1. 透過 `createPackageContext()` 取得 PVZRH 的 ClassLoader 和 DEX 存取權限。
2. 安裝 Pine Hook：雙向 ClassLoader、Instrumentation、PackageManager、原生函式庫和 UnityPlayer。
3. 透過 `Instrumentation.execStartActivity` Hook 將遊戲 Activity 重新導向到清單中註冊的 `StubActivity`。
4. `Instrumentation.newActivity` 還原真實的遊戲 Activity 類別和原始 Intent。
5. `Activity.attachBaseContext` Hook 用三路 `CustomContextWrapper` 包裝 Context：
   - **遊戲資源**（Assets、Resources、Theme）→ PVZRH 套件上下文
   - **檔案/儲存**（getFilesDir、SharedPreferences）→ 啟動器 Application
   - **視窗服務**（getDisplay、getSystemService）→ 原始 Activity 基礎 Context
6. `ClassLoader.findLibrary()` Hook 重新導向原生 .so 載入：遊戲庫來自遊戲 APK，Fusion 庫來自啟動器，.NET/il2cpp/unity 庫來自資料目錄。
7. `UnityPlayer` 建構函式 Hook 設定 activity 欄位並顯示注入覆蓋層。
8. `UnityPlayer.kill()` Hook 阻止首次呼叫 5 秒以通過完整性檢查。

所有 Hook 均透過 Kotlin/Java 安裝，無需原生 `libmain.so` 引導。

## 除錯快照

當機後，下次啟動器啟動時會在以下路徑儲存診斷資訊：

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

快照包含啟動器日誌、logcat、崩潰 logcat、套件/Activity 狀態、程序結束資訊和可用的 BepInEx 日誌。
