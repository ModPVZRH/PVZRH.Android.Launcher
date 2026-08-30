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

### Projetos Utilizados e Referenciados

- [BepInEx](https://github.com/BepInEx/BepInEx) — Framework de modding Unity IL2CPP, o carregador principal de plugins
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Contêiner de Runtime Unity IL2CPP para Android pela equipe Starlight
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — Fork customizado do BepInEx para Android
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — Launcher genérico BepInEx para Android com gerenciamento de modpacks
- [dotnet/runtime](https://github.com/dotnet/runtime) — Runtime .NET, compilado do fonte com backend criptográfico OpenSSL
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0, substituindo BoringSSL para criptografia ARM64 Android
- [Pine](https://github.com/canyie/Pine) — Framework de hook de métodos Java ART
- [Dobby](https://github.com/jmpews/Dobby) — Framework de hook nativo para ARM64
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — Ferramenta de engenharia reversa IL2CPP

### Desenvolvedores Principais

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## Guia

- [Guia de Contribuição](CONTRIBUTING.md) — Como contribuir para o projeto
- [Guia de Tradução](TRANSLATING.md) — Ajude a traduzir o launcher para seu idioma
- [Solução de Problemas](https://modpvzrh.github.io/troubleshooting) — Problemas comuns e soluções

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

O launcher injeta BepInEx no processo do jogo usando **Pine** (framework de hook de métodos Java):

1. `createPackageContext()` para PVZRH para obter seu class loader e acesso DEX.
2. Instala hooks Pine: ClassLoader bidirecional, Instrumentation, PackageManager, biblioteca nativa e UnityPlayer.
3. Redireciona a activity do jogo para uma `StubActivity` registrada no manifest via hook `Instrumentation.execStartActivity`.
4. `Instrumentation.newActivity` restaura a classe real da activity do jogo e a Intent original.
5. Hook `Activity.attachBaseContext` envolve o Context com um `CustomContextWrapper` de três vias:
   - **Recursos do jogo** (Assets, Resources, Theme) → contexto do pacote PVZRH
   - **Arquivos/armazenamento** (getFilesDir, SharedPreferences) → Application do launcher
   - **Serviços de janela** (getDisplay, getSystemService) → Context base da Activity original
6. Hook `ClassLoader.findLibrary()` redireciona o carregamento de .so nativos: libs do jogo vêm do APK, libs do Fusion vêm do launcher, libs .NET/il2cpp/unity vêm do diretório de dados.
7. Hook do construtor `UnityPlayer` define o campo activity e mostra overlay de injeção.
8. Hook `UnityPlayer.kill()` bloqueia a primeira chamada por 5 segundos para sobreviver a verificações de integridade.

Todos os hooks são instalados via Kotlin/Java — nenhum bootstrap nativo `libmain.so` necessário.

## Capturas de Depuração

Após um crash, a próxima inicialização do launcher salva diagnósticos em:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

As capturas incluem logs do launcher, logcat, logcat de crash, estado do pacote/Activity, informações de saída do processo e logs disponíveis do BepInEx.
