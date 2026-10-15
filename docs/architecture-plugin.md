# LogCactus для Android Studio — архитектура плагина

## Решение (ADR-1): отдельное приложение, а не перенос основного
Основное приложение LogCactus (`../LogCactus`, Compose Desktop 1.6.11, Material) **не меняется**. Плагин — отдельный проект,
написанный под IDE по её правилам. Из основного приложения берутся только чистые (без UI) исходники логики; они подключаются
как `srcDir` и не копируются (см. `build.gradle.kts`, `sharedCore`).

Причины: Студия приносит свои Compose 1.10 + Skiko + Jewel; приложение с собственным Compose в плагине падает
(`UnsatisfiedLinkError … _nFlushAndSubmit`). Перевод основного приложения на ту же
платформу — отдельная большая работа без выгоды для пользователей приложения.

## Платформа
- Цель: Android Studio 2026.1 (build 261), `since-build=261`, без `until-build`. Сборка против локальной Студии (`-PstudioPath`).
- Kotlin 2.4.10 (классы Студии скомпилированы с метаданными 2.4), `kotlin.plugin.compose`, JDK 21 (jbr-21).
- UI — Jewel + Compose из самой IDE (`bundledModule(...)` + `<dependencies><module .../>` в plugin.xml), тема `SwingBridgeTheme`
  (подхватывает тему IDE автоматически). Собственных копий Compose/Jewel/Skiko/coroutines в плагине нет (`kotlin.stdlib.default.dependency=false`).
- Окно: tool window через `ToolWindow.addComposeTab` (Jewel).

## Слои
- `adb/` — работа с устройствами: путь к adb берётся у Студии (`AdbFileProvider.fromProject`), поток `adb logcat -v long` —
  `Flow` с корректным завершением процесса при отмене (без shutdown-hook'ов и своих потоков).
- `LogCactusProjectService` (project-level `@Service`) — состояние сессии (буфер, фильтр, закладки, краши), `CoroutineScope`
  выдаёт платформа (инъекция в конструктор) → отмена при закрытии проекта.
- `ui/` — Compose-экран на Jewel; состояние читает из сервиса.
- Общая логика (srcDir из основного приложения): `LogEntry`, `LogLevel`, `LogQueryMatcher` (язык фильтра), `AdbLongLogcatParser`,
  `CrashTracker`, `RowDetails`, `BugReport`.

## Правила
- Никаких `Runtime.addShutdownHook`, `System.getProperty`-конфигурации, daemon-потоков, смены context classloader.
- Всё освобождаемое — через `Disposable`/`CoroutineScope` платформы.
- Настройки — `PersistentStateComponent` (+ `Configurable`), не файлы в `~`.
- Тема/шрифты/цвета только из Jewel/IDE, никаких своих палитр.
- Релиз: `verifyPlugin` на recommended IDE, `pluginIcon.svg`, change-notes, подпись/публикация через переменные окружения.

## Структура кода (актуально на 2026-10-02)
- `LogcatTabs` — штатные вкладки окна Студии (`ToolWindow.contentManager`), «+» через `ToolWindowEx.setTabActions` (стоит сразу за вкладками, как у оригинального Logcat). Одна вкладка = одна `LogcatSession` со своим `CoroutineScope`; закрытие вкладки (`Content.setDisposer`) останавливает поток adb. Окно должно иметь `canCloseContents="true"` (атрибут во множественном числе, иначе нет «×» и закрытия средней кнопкой). Живые вкладки (устройство + фильтр + имя из Rename Tab) сохраняются в `LogcatSettings.tabs`; вкладки из файла сессии — нет.
- `LogcatSession` — обычный класс (не служба): буфер, фильтр, закладки, краши, `package:mine`, пауза. Буфер при переполнении вытесняет сначала записи, скрытые фильтром.
- `ViewOptions` (project service) — вид и история фильтров, общие для всех вкладок. `DeviceMonitor` (project service) — один опрос `adb devices` на проект.
- `ui/`: `LogcatPanel` (список одной строкой на запись, выделение по символам, ПКМ-меню, закладки, прилипание к концу по итогам прокрутки), `ToolStrip` (боковая панель), `Header` (устройство + фильтр с автодополнением, плашка mine/pinned), `ConfigurePopup`, `RowText`/`Rows` (отрисовка и свёртка стеков/JSON/URL), `FilterCompletion`.
- `tools/`: `DeviceTools` (действия над приложением, Wi-Fi, сессии, скриншот), `Dialogs`.
- `adb/`: `AdbClient` (adb Студии через `AdbFileProvider`), `ProcessNames` (pid → процесс/пакет, новый pid узнаётся сразу, бюджет 8 вызовов ps за 10 с).
- Общий код из основного приложения (`syncShared`): LogEntry/LogLevel/LogQueryMatcher/AdbLongLogcatParser/Filter*, RowDetails, UrlDetails, BugReport, CrashTracker, DeviceInfo, ForegroundApp, DeviceActions, WifiAdb, SessionFile, SessionDiff, AtomicFiles. В плагине подмены-шимы: `StackFolder` (только isFrame), `AdbShell`, `AdbLocator` (adb Студии).

## Грабли, на которые уже наступили
- Studio обновляется сама (сейчас 2026.2, build 262; каталог настроек `AndroidStudio2026.2.1`, раньше `2026.1.4`): плагин ставить в `plugins/` нужного каталога; при обновлении Gradle иногда падает на разрешении bundled-модулей — помогает `./gradlew --stop`.
- Нужен Kotlin ≥ 2.4 (метаданные классов Studio).
- Окно в `runIde` открывается вручную; в свежей песочнице нужен каталог `system_runIde/whatsnew` (создаётся в `build.gradle.kts`).
- `Dropdown`/`PopupMenu` Jewel внутри списка не годятся для ПКМ-меню — меню рисуется на уровне панели в точке нажатия.

## Объём v1
Сделано: фильтр (язык LogCactus, автодополнение, история/избранное), `package:mine` с плашкой pin, вкладки, поток логов с цветами из схемы редактора, пауза/очистка/«scroll to end» как переключатель, выделение мышью по символам, ПКМ-меню, закладки (F2), краши/ANR, баг-репорт (выделение / видимые / закладки), свёртка стеков, JSON/Intent, длинные URL столбиком, настройка формата (шестерёнка + Settings), меню устройства и приложения (Wi-Fi, рестарт, force stop, кэш/данные, deep link, App info, скриншот, сессии, сравнение).
Подсветка синтаксиса фильтра: `ui/FilterHighlight.kt` (лексер с позициями по правилам `LogQueryMatcher` + `OutputTransformation.addStyle`, цвета из схемы редактора `DEFAULT_*`), тест `FilterHighlightTest`; глазами в Студии не проверена.
Не сделано: UI-тесты, подпись/публикация (токены из env), горизонтальная прокрутка работает только при выключенном переносе, имя вкладки по устройству.
