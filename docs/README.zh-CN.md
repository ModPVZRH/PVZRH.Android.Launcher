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

### 使用和引用的项目

- [BepInEx](https://github.com/BepInEx/BepInEx) — Unity IL2CPP 模组框架，核心插件加载器
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Starlight 团队的 Android Unity IL2CPP 运行时容器
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — 自定义 Android BepInEx 分支
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — 通用 BepInEx Android 启动器，支持整合包管理
- [dotnet/runtime](https://github.com/dotnet/runtime) — .NET 运行时，使用 OpenSSL 加密后端从源码构建
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0，替代 BoringSSL 用于 ARM64 Android 加密
- [Pine](https://github.com/canyie/Pine) — ART Java 方法 Hook 框架
- [Dobby](https://github.com/jmpews/Dobby) — ARM64 原生 Hook 框架
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — IL2CPP 逆向工程工具

### 主要开发者

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## 指南

- [贡献指南](CONTRIBUTING.md) — 如何参与项目贡献
- [翻译指南](TRANSLATING.md) — 帮助将启动器翻译成你的语言
- [故障排查](https://modpvzrh.github.io/zh/troubleshooting) — 常见问题与解决方案

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

启动器使用 **Pine**（Java 方法 Hook 框架）将 BepInEx 注入游戏进程：

1. 通过 `createPackageContext()` 获取 PVZRH 的 ClassLoader 和 DEX 访问权限。
2. 安装 Pine Hook：双向 ClassLoader、Instrumentation、PackageManager、原生库和 UnityPlayer。
3. 通过 `Instrumentation.execStartActivity` Hook 将游戏 Activity 重定向到清单中注册的 `StubActivity`。
4. `Instrumentation.newActivity` 恢复真实的游戏 Activity 类和原始 Intent。
5. `Activity.attachBaseContext` Hook 用三路 `CustomContextWrapper` 包装 Context：
   - **游戏资源**（Assets、Resources、Theme）→ PVZRH 包上下文
   - **文件/存储**（getFilesDir、SharedPreferences）→ 启动器 Application
   - **窗口服务**（getDisplay、getSystemService）→ 原始 Activity 基础 Context
6. `ClassLoader.findLibrary()` Hook 重定向原生 .so 加载：游戏库来自游戏 APK，Fusion 库来自启动器，.NET/il2cpp/unity 库来自数据目录。
7. `UnityPlayer` 构造函数 Hook 设置 activity 字段并显示注入覆盖层。
8. `UnityPlayer.kill()` Hook 阻止首次调用 5 秒以通过完整性检查。

所有 Hook 均通过 Kotlin/Java 安装，无需原生 `libmain.so` 引导。

## 调试快照

崩溃后，下次启动器启动时会在以下路径保存诊断信息：

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

快照包含启动器日志、logcat、崩溃 logcat、包/Activity 状态、进程退出信息和可用的 BepInEx 日志。
