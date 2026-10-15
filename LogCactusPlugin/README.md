# LogCactus для Android Studio

Плагин-просмотрщик logcat: язык фильтра LogCactus (`tag:`, `level:`, `package:mine`, `age:`, `is:crash`, `-исключение`), краши и ANR, закладки, свёртка стектрейсов, раскрытие JSON/Intent, баг-репорт одним кликом. UI — Jewel + Compose самой Студии; основное приложение LogCactus (`../LogCactus`) не затрагивается, общая логика подключается как исходники (`syncShared` в `build.gradle.kts`).

## Что умеет
Вкладки (как у оригинального Logcat), фильтр на языке LogCactus с автодополнением, `package:mine`, закладки, краши и ANR, баг-репорт, свёртка стеков, JSON/Intent/URL, меню устройства и приложения (Wi-Fi, рестарт, очистка, скриншот, сессии). Подробнее — `../docs/architecture-plugin.md`.

## Сборка и запуск
```bash
export JAVA_HOME=<JDK 21, например JBR из Android Studio>   # нужен JDK 21
./gradlew runIde        # песочная Студия; окно LogCactus внизу (View → Tool Windows → LogCactus)
./gradlew test verifyPlugin
./gradlew buildPlugin   # build/distributions/*.zip
```
Путь к Студии — `-PstudioPath=...` (по умолчанию `/Applications/Android Studio.app`).

## Установка в свою Студию
`./gradlew buildPlugin`, затем распаковать `build/distributions/logcactus-plugin-<ver>.zip` в `~/Library/Application Support/Google/AndroidStudio<версия>/plugins/` (каталог зависит от версии Студии, сейчас `2026.2.1`) и перезапустить Студию; либо Settings → Plugins → Install from Disk.

## Дальше
Публикация: `signPlugin`/`publishPlugin` с токенами из переменных окружения (в репозитории секретов нет). Описание архитектуры и правил — `../docs/architecture-plugin.md`.
