# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

Launcher e gerenciador de mods Android dedicado para Plants Vs Zombies Fusion.

## Destino

- Pacote: `com.LanPiaoPiao.PlantsVsZombiesRH`
- Activity do Unity: `com.unity3d.player.UnityPlayerActivity`
- Versão do Unity: `2022.3.62f1c1`
- ABI: `arm64-v8a`
- Pacote do launcher: `com.pvzrh.android.launcher`

## Créditos

- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore)
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher)
- [BepInEx.Android](https://github.com/NextBep/BepInEx.Android)

## Compilação

Há uma compilação CI na página do GitHub Actions.

Execute neste diretório:

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK é salvo em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Cada compilação incrementa `ci-version.txt` e define a versão do APK como:

```text
0.0.1-ci.<number>
```

## Instalação

Para uma instalação limpa de teste:

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

A desinstalação remove os dados privados e configurações do launcher. Ela não remove o diretório de dados externo do PVZRH.

## Dados Externos

Os dados do BepInEx em tempo de execution são armazenados em:

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

O diretório contém plugins, configuração, logs, modpacks e estado vanilla.

## Modelo de Injeção

O launcher segue o modelo do FusionCore com correções adicionais:

1. Cria um contexto de pacote para o PVZRH e obtém seu class loader.
2. Instala hooks de class loader, instrumentation, package manager, Activity e biblioteca nativa.
3. Inicia a `StubActivity` registrada.
4. Restaura o `UnityPlayerActivity` através de `Instrumentation.newActivity` no processo do launcher.
5. Envolve apenas o `attachBaseContext` com um wrapper de contexto de três vias.
6. Mantém o argumento do construtor do UnityPlayer como a Activity real.

O wrapper de contexto roteia os recursos do jogo para o PVZRH, o armazenamento do launcher para a Application do launcher e os serviços de janela para o base Context da Activity original.

## Capturas de Depuração

Após um crash, a próxima inicialização do launcher salva diagnósticos em:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

As capturas incluem logs do launcher, logcat, logcat de crash, estado do pacote/Activity, informações de saída do processo e logs disponíveis do BepInEx.
