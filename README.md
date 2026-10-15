# LogCat by cactus

Android Logcat вне Android Studio и внутри неё. Два проекта в одном репозитории:

![LogCat by cactus](docs/screenshot.png)

**Сайт:** https://alx-in-dev.github.io/LogCat-by-cactus/ · **Скачать:** [последний релиз](https://github.com/alx-in-dev/LogCat-by-cactus/releases/latest)

| Каталог | Что это |
|---|---|
| [`LogCactus/`](LogCactus) | Десктопное приложение (Kotlin + Compose Multiplatform, macOS и Windows): вкладки с независимыми сессиями, язык фильтра (`tag:`, `level:`, `package:mine`, `age:`, `is:crash`), краши и ANR, закладки, баг-репорт, Wi-Fi ADB, встроенный adb/aapt2 |
| [`LogCactusPlugin/`](LogCactusPlugin) | Плагин для Android Studio с тем же языком фильтра. UI — на Jewel/Compose самой IDE; общая логика подключается из `LogCactus/` как исходники (`syncShared`), поэтому репозиторий нужно клонировать целиком |

Документация (на русском): [`docs/`](docs), история изменений — [`CHANGELOG.md`](CHANGELOG.md).

English in one line: a standalone desktop Logcat viewer (Compose Desktop) plus an Android Studio plugin sharing one filter engine; docs are in Russian.

## Быстрый старт

Приложение (JDK 17):

```bash
cd LogCactus
./gradlew run          # нужен adb в PATH или встроенный (по умолчанию используется встроенный)
./gradlew test
./gradlew packageDmg   # macOS;  gradlew.bat packageMsi — Windows
```

Плагин (JDK 21, локальная Android Studio):

```bash
cd LogCactusPlugin
./gradlew test buildPlugin -PstudioPath="/Applications/Android Studio.app"
```

Подробности и параметры — в `LogCactus/README.md` и `LogCactusPlugin/README.md`.

## Code signing policy

Free code signing provided by [SignPath.io](https://signpath.io), certificate by [SignPath Foundation](https://signpath.org).
Windows-установщик (`.msi`) собирается из этого репозитория в [GitHub Actions](.github/workflows/windows-installer.yml)
и подписывается только после ручного одобрения каждого запроса. Подробнее — [`docs/code-signing.md`](docs/code-signing.md).

- Committers and reviewers: [alx-in-dev](https://github.com/alx-in-dev)
- Approvers: [alx-in-dev](https://github.com/alx-in-dev)

**Privacy policy.** Программы этого репозитория не передают информацию в другие сетевые системы, кроме случаев, когда этого
явно требует пользователь: ссылки открываются в браузере по клику, а `adb` подключается только к устройствам, которые
пользователь указал сам. Встроенный MCP-сервер (по умолчанию выключен) слушает только `127.0.0.1`.
macOS-сборка подписана Developer ID и нотаризована Apple.

## Подпись и публикация

Секретов в репозитории нет. Подпись macOS-сборки включается свойством `-PmacSignIdentity`, подпись и публикация плагина — переменными окружения `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`.

## Лицензия

[Apache License 2.0](LICENSE). Сторонние компоненты — в [`NOTICE`](NOTICE).
