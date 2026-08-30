# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

プラントvsゾンビ フュージョン専用の Android ランチャーおよびMod管理ツール。

## 対象

- パッケージ名：`com.LanPiaoPiao.PlantsVsZombiesRH`
- Unity Activity：`com.unity3d.player.UnityPlayerActivity`
- Unity バージョン：`2022.3.62f1c1`
- ABI：`arm64-v8a`
- ランチャーパッケージ名：`com.pvzrh.android.launcher`

## クレジット

### 使用・参照されているプロジェクト

- [BepInEx](https://github.com/BepInEx/BepInEx) — Unity IL2CPP モジュールフレームワーク、コアプラグインローダー
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Starlight チームの Android Unity IL2CPP ランタイムコンテナ
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — カスタム Android BepInEx フォーク
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — モッドパック管理対応の汎用 BepInEx Android ランチャー
- [dotnet/runtime](https://github.com/dotnet/runtime) — .NET ランタイム、OpenSSL 暗号化バックエンドでソースから構築
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0、ARM64 Android 暗号化用に BoringSSL を置換
- [Pine](https://github.com/canyie/Pine) — ART Java メソッドフックフレームワーク
- [Dobby](https://github.com/jmpews/Dobby) — ARM64 ネイティブフックフレームワーク
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — IL2CPP リバースエンジニアリングツール

### メイン開発者

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## ガイド

- [貢献ガイド](CONTRIBUTING.md) — プロジェクトへの貢献方法
- [翻訳ガイド](TRANSLATING.md) — ランチャーの翻訳を手伝う
- [トラブルシューティング](https://modpvzrh.github.io/troubleshooting) — よくある問題と解決策

## ビルド

GitHub Actions ページにCIビルドがあります。

プロジェクトのルートディレクトリで実行：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK の出力先：

```text
app/build/outputs/apk/debug/app-debug.apk
```

各ビルドで `ci-version.txt` がインクリメントされ、APKバージョンは以下の形式になります：

```text
0.0.1-ci.<number>
```

## インストール

クリーンなテストインストール：

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

アンインストールするとランチャーのプライベートデータと設定が削除されます。外部のPVZRHデータディレクトリは削除されません。

## 外部データ

ランタイムのBepInExデータは以下に保存されます：

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

このディレクトリにはプラグイン、設定、ログ、Modパック、バニラ状態が含まれています。

## 注入モデル

ランチャーは **Pine**（Java メソッドフックフレームワーク）を使用して BepInEx をゲームプロセスに注入します：

1. `createPackageContext()` で PVZRH のクラスローダーと DEX アクセスを取得。
2. Pine フックをインストール：双方向 ClassLoader、Instrumentation、PackageManager、ネイティブライブラリ、UnityPlayer。
3. `Instrumentation.execStartActivity` フックでゲーム Activity をマニフェスト登録の `StubActivity` にリダイレクト。
4. `Instrumentation.newActivity` で実際のゲーム Activity クラスと元の Intent を復元。
5. `Activity.attachBaseContext` フックで Context を三重の `CustomContextWrapper` でラップ：
   - **ゲームリソース**（Assets、Resources、Theme）→ PVZRH パッケージコンテキスト
   - **ファイル/ストレージ**（getFilesDir、SharedPreferences）→ ランチャー Application
   - **ウィンドウサービス**（getDisplay、getSystemService）→ 元の Activity ベースコンテキスト
6. `ClassLoader.findLibrary()` フックでネイティブ .so のロードをリダイレクト：ゲームライブラリはゲーム APKから、Fusion ライブラリはランチャーから、.NET/il2cpp/unity ライブラリはデータディレクトリから。
7. `UnityPlayer` コンストラクターフックで activity フィールドを設定し、注入オーバーレイを表示。
8. `UnityPlayer.kill()` フックで最初の呼び出しを 5 秒間ブロックし、整合性チェックを回避。

すべてのフックは Kotlin/Java からインストールされ、ネイティブ `libmain.so` ブートストラップは不要です。

## デバッグスナップショット

クラッシュ後、次回のランチャー起動時に以下のパスに診断情報が保存されます：

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

スナップショットにはランチャーログ、logcat、クラッシュlogcat、パッケージ/Activity状態、プロセス終了情報、利用可能なBepInExログが含まれます。
