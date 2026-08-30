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

Лаунчер следует модели FusionCore с дополнительными исправлениями:

1. Создаёт контекст пакета для PVZRH и получает его загрузчик классов.
2. Устанавливает хуки загрузчика классов, инструментации, менеджера пакетов, Activity и нативных библиотек.
3. Запускает зарегистрированную `StubActivity`.
4. Восстанавливает `UnityPlayerActivity` через `Instrumentation.newActivity` в процессе лаунчера.
5. Оборачивает только `attachBaseContext` трёхуровневой обёрткой контекста.
6. Сохраняет аргумент конструктора UnityPlayer как реальную Activity.

Обёртка контекста направляет ресурсы игры в PVZRH, хранилище лаунчера в Application лаунчера, а оконные сервисы в базовый Context оригинальной Activity.

## Отладочные снимки

После аварии при следующем запуске лаунчер сохраняет диагностику в:

```text
/data/user/0/com.pvzrh.android.launcher/files/debug/<timestamp>/
```

Снимки включают логи лаунчера, logcat, логи аварий, состояние пакета/Activity, информацию о завершении процесса и доступные логи BepInEx.
