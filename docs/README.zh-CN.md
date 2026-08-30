# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

植物大战僵尸融合版的 Android 启动器与模组管理器。

## 目标

- 游戏包名：`com.LanPiaoPiao.PlantsVsZombiesRH`
- Unity Activity：`com.unity3d.player.UnityPlayerActivity`
- Unity 版本：`2022.3.62f1c1`
- ABI：`arm64-v8a`
- 启动器包名：`com.pvzrh.android.launcher`

## 致谢

- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore)
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher)
- [BepInEx.Android](https://github.com/NextBep/BepInEx.Android)

## 构建

GitHub Actions 页面有 CI 构建。

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

每次构建会递增 `ci-version.txt`，APK 版本格式为：

```text
0.0.1-ci.<number>
```

## 安装

全新测试安装：

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

卸载会清除启动器的私有数据和设置，但不会删除外部 PVZRH 数据目录。

## 外部数据

运行时 BepInEx 数据存储在：

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

该目录包含插件、配置、日志、整合包和原版状态。

## 注入模型

启动器基于 FusionCore 模型并进行了额外修复：

1. 为 PVZRH 创建包上下文并获取其类加载器。
2. 安装类加载器、Instrumentation、PackageManager、Activity 和原生库 hook。
3. 启动注册的 `StubActivity`。
4. 通过启动器进程中的 `Instrumentation.newActivity` 恢复 `UnityPlayerActivity`。
5. 仅对 `attachBaseContext` 使用三层 Context 包装器。
6. 保留 UnityPlayer 构造函数参数为真实 Activity。

Context 包装器将游戏资源路由到 PVZRH，启动器存储路由到启动器 Application，窗口服务路由到原始 Activity 的 base Context。

## 调试快照

崩溃后，下次启动器启动时会在以下路径保存诊断信息：

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

快照包含启动器日志、logcat、崩溃 logcat、包/Activity 状态、进程退出信息和可用的 BepInEx 日志。
