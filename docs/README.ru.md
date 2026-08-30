# PVZRH Android Launcher

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [Português (BR)](README.pt-BR.md) | [Русский](README.ru.md) | [日本語](README.ja.md)

Специализированный Android-лаунчер и менеджер модов для Plants Vs Zombies Fusion.

## Цель

- Пакет: `com.LanPiaoPiao.PlantsVsZombiesRH`
- Activity Unity: `com.unity3d.player.UnityPlayerActivity`
- Версия Unity: `2022.3.62f1c1`
- ABI: `arm64-v8a`
- Пакет лаунчера: `com.pvzrh.android.launcher`

## Благодарности

### Используемые и упомянутые проекты

- [BepInEx](https://github.com/BepInEx/BepInEx) — Фреймворк моддинга Unity IL2CPP, загрузчик плагинов
- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore) — Контейнер рантайма Unity IL2CPP для Android от команды Starlight
- [NextBep (BepInEx.Android)](https://github.com/NextBep/BepInEx.Android) — Пользовательский форк BepInEx для Android
- [BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher) — Универсальный лаунчер BepInEx для Android с управлением модпаками
- [dotnet/runtime](https://github.com/dotnet/runtime) — Рантайм .NET, собранный из исходников с криптографическим бэкендом OpenSSL
- [OpenSSL](https://github.com/openssl/openssl) — OpenSSL 3.4.0, заменяющий BoringSSL для криптографии ARM64 Android
- [Pine](https://github.com/canyie/Pine) — Фреймворк хуков Java-методов ART
- [Dobby](https://github.com/jmpews/Dobby) — Фреймворк нативных хуков для ARM64
- [Cpp2IL](https://github.com/SamboyCoding/Cpp2IL) — Инструмент реверс-инжиниринга IL2CPP

### Ведущие разработчики

HayashiUme · Gaoshu · NextBep

©2026 PVZRH Mod Dev

## Руководство

- [Руководство по участию](CONTRIBUTING.md) — Как внести вклад в проект
- [Руководство по переводу](TRANSLATING.md) — Помогите перевести лаунчер на ваш язык
- [Устранение неполадок](https://modpvzrh.github.io/troubleshooting) — Типичные проблемы и решения

## Сборка

CI-сборка доступна на странице GitHub Actions.

Выполните из этого каталога:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK сохраняется в:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Каждая сборка увеличивает `ci-version.txt` и устанавливает версию APK:

```text
0.0.1-ci.<number>
```

## Установка

Для чистой тестовой установки:

```powershell
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$apk = "E:\WindowsFile\BepInExt\PVZRH.Android.Launcher\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.pvzrh.android.launcher
& $adb install $apk
```

Удаление удаляет приватные данные и настройки лаунчера. Внешний каталог данных PVZRH не удаляется.

## Внешние данные

Данные BepInEx во время выполнения хранятся в:

```text
/storage/emulated/0/PVZRH_Launcher/com.LanPiaoPiao.PlantsVsZombiesRH/
```

Каталог содержит плагины, конфигурацию, логи, модпаки и ванильное состояние.

## Модель инъекции

Лаунчер внедряет BepInEx в процесс игры с помощью **Pine** (фреймворк хуков Java-методов):

1. `createPackageContext()` для PVZRH — получение class loader и доступа к DEX.
2. Установка Pine хуков: двунаправленный ClassLoader, Instrumentation, PackageManager, нативные библиотеки и UnityPlayer.
3. Перенаправление activity игры на зарегистрированную в манифесте `StubActivity` через хук `Instrumentation.execStartActivity`.
4. `Instrumentation.newActivity` восстанавливает реальный класс activity игры и оригинальный Intent.
5. Хук `Activity.attachBaseContext` оборачивает Context трёхслойным `CustomContextWrapper`:
   - **Игровые ресурсы** (Assets, Resources, Theme) → контекст пакета PVZRH
   - **Файлы/хранилище** (getFilesDir, SharedPreferences) → Application лаунчера
   - **Оконные сервисы** (getDisplay, getSystemService) → базовый Context оригинальной Activity
6. Хук `ClassLoader.findLibrary()` перенаправляет загрузку нативных .so: игровые библиотеки из APK, Fusion из лаунчера, .NET/il2cpp/unity из каталога данных.
7. Хук конструктора `UnityPlayer` устанавливает поле activity и показывает оверлей инъекции.
8. Хук `UnityPlayer.kill()` блокирует первый вызов на 5 секунд для прохождения проверок целостности.

Все хуки устанавливаются из Kotlin/Java — нативный `libmain.so` бутстрап не требуется.

## Отладочные снимки

После аварии при следующем запуске лаунчер сохраняет диагностику в:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

Снимки включают логи лаунчера, logcat, логи аварий, состояние пакета/Activity, информацию о завершении процесса и доступные логи BepInEx.
