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

- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore)
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher)
- [BepInEx.Android](https://github.com/NextBep/BepInEx.Android)

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

ランチャーはFusionCoreモデルに基づき、追加の修正を施しています：

1. PVZRHのパッケージコンテキストを作成し、クラスローダーを取得。
2. クラスローダー、Instrumentation、PackageManager、Activity、ネイティブライブラリのフックをインストール。
3. 登録された `StubActivity` を起動。
4. ランチャープロセス内の `Instrumentation.newActivity` を通じて `UnityPlayerActivity` を復元。
5. `attachBaseContext` のみを3層のContextラッパーで包む。
6. UnityPlayerのコンストラクタ引数を実際のActivityとして保持。

ContextラッパーはゲームリソースをPVZRHへ、ランチャーストレージをランチャーApplicationへ、ウィンドウサービスを元のActivityのbase Contextへルーティングします。

## デバッグスナップショット

クラッシュ後、次回のランチャー起動時に以下のパスに診断情報が保存されます：

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

スナップショットにはランチャーログ、logcat、クラッシュlogcat、パッケージ/Activity状態、プロセス終了情報、利用可能なBepInExログが含まれます。
