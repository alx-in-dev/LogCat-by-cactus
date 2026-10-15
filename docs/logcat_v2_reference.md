# Карта бизнес-логики New Logcat V2 (справочник для сверки с LogCactus)

Дата: 2026-09-29
Источник: [github.com/JetBrains/android](https://github.com/JetBrains/android), модуль `logcat/`, ветка `master` (снимок на момент разбора — 2026-09-29, репозиторий активно обновляется). Лицензия Apache-2.0.

Разбор сделан выборочно: прочитаны полностью ключевые файлы (presenter, panel, header, format-модель, device-модель, filter-модель, часть actions), остальные — по сигнатурам классов/функций. Список непрочитанных файлов — в конце.

## Архитектура и state-модель

В реальном Logcat V2 **нет separate presenter+controller+UI-bridge**, как в LogCactus. Вместо этого:

- `LogcatPresenter` (`LogcatPresenter.kt`, интерфейс) — контракт "что показывать и как управлять сессией". Расширяет `TagsProvider, PackageNamesProvider, ProcessNamesProvider, Disposable`. Ключевые методы: `reloadMessages()`, `applyFilter(filter: LogcatFilter?)`, `clearMessageView()`, `restartLogcat()`, `reloadFile()`, `isLogcatEmpty()`, `processMessages(messages: List<LogcatMessage>)` (suspend), `appendMessages(textAccumulator, context)` (suspend), `getConnectedDevice()/getSelectedDevice()/getSelectedItem()`, `applyLogcatSettings(...)`, `countFilterMatches(filter)`, `foldImmediately()`, `isLogcatPaused()/pauseLogcat()/resumeLogcat()`, `getFilter()/setFilter(...)`, `isSoftWrapEnabled()/setSoftWrapEnabled(...)`, `getBacklogMessages()`, `enterInvisibleMode()` (suspend), `isShowing()`, `openLogcatFile(path)`, `setProguardMapping(path)`, `var formattingOptions: FormattingOptions`.
- `LogcatMainPanel` (`LogcatMainPanel.kt`, 1111 строк) — **реализует `LogcatPresenter` сам**, то есть панель UI и presenter — один класс (`internal class LogcatMainPanel ... : ..., LogcatPresenter`). Это отличается от LogCactus, где `ComposeUiBridge` (UI state/rendering) и `LogcatSessionController` (session lifecycle/device+stream orchestration) разделены — разделение в LogCactus чище, но не 1:1 архитектурно с оригиналом (и это нормально, не требует исправления).
- `LogcatHeaderPanel` — device combo box + filter text field, `GroupLayout` (см. отдельный раздел ниже), уже перенесено в LogCactus.
- `LogcatToolWindowFactory` — регистрация tool window, создание вкладок через `SplittingTabsToolWindowFactory` (общий механизм IntelliJ Platform для "многосессионных" tool windows — объясняет, откуда в Studio берутся вкладки Logcat/Logcat(2)/...). LogCactus реализует вкладки вручную (свой `LogcatTab` + ручное управление жизненным циклом), что функционально эквивалентно, но не переиспользует платформенный механизм (ожидаемо — платформенного `SplittingTabsToolWindowFactory` вне IntelliJ Platform не существует).
- `LogcatPanelConfig` — персистентная конфигурация одной вкладки/панели: `device`, `file`, `formattingConfig` (Preset/Custom), `filter`, `filterMatchCase`, `isSoftWrap`, `proguardFile`. Сериализуется в JSON (Gson) и хранится в workspace-файле проекта. LogCactus хранит часть этого (`~/.logcactus/preferences.properties`, `saved-filters.properties`), но не привязывает конфигурацию к конкретной вкладке — у нас все вкладки используют общие glbal preferences, а не per-tab config, как в оригинале.
- `LogcatService` (`service/LogcatService.kt`, интерфейс) — источник данных: `readLogcat(serialNumber, sdk: AndroidApiLevel, duration, maxHistoryEntries): Flow<List<LogcatMessage>>` и `clearLogcat(serialNumber)`. Использует библиотеку `adblib` напрямую (не шелл `adb`), с явным лимитом истории и таймаутом. LogCactus (`AdbLogStreamGateway`) шеллит `adb -s <serial> logcat -v long` — функционально похоже, но без ограничения `maxHistoryEntries` и без учёта `sdk` (в оригинале SDK влияет на эффективность чтения логов на новых устройствах).

## Полная карта toolbar-действий

Источник: `LogcatMainPanel.createToolbarActions()` (единственное место, где строится тулбар).

| # | Класс действия | Что реально делает | Иконка | В LogCactus | Куда добавить |
|---|---|---|---|---|---|
| 1 | `ClearLogcatAction` | Очищает буфер логов на экране | `AllIcons.Actions.GC` | ✅ есть (`clear.svg`, тот же файл) | — |
| 2 | `PauseLogcatAction` | Toggle пауза/резюме; текст меняется Pause↔Resume | `AllIcons.Actions.Pause` | ✅ есть | — |
| 3 | `RestartOrReloadLogcatAction` | Переподключает поток логов (для файла — перечитывает файл) | `AllIcons.Actions.Restart`/`.Refresh` | ✅ есть (`restartStream()`, реальный unsubscribe+resubscribe) | — |
| 4 | `ScrollToTheEndToolbarAction` | Прокрутка к последней строке (платформенное действие консоли, не Logcat-специфичное) | стандартная консольная иконка | ✅ есть как toggle "follow" (немного другая семантика: у нас continuous-follow toggle, в оригинале — разовый прыжок в конец + platform autoscroll) | низкий приоритет — уточнить семантику |
| 5 | `PreviousOccurrenceToolbarAction` | **ИСПРАВЛЕНО 2026-09-29** (было ошибочно описано как навигация по совпадениям поиска/фильтра): по `LogcatOccurrenceNavigator.kt` — переход между строками **стектрейса** (regex `^\s*at .+\(.+\)$`), не связано с фильтром/поиском вообще. В IDE клик ещё открывает файл в редакторе — вне IDE не применимо | `previousOccurence_dark.svg` (платформенная) | ✅ есть (навигация по regex, без открытия файла — не применимо standalone) | — |
| 6 | `NextOccurrenceToolbarAction` | То же, вперёд по стектрейсу | `nextOccurence_dark.svg` | ✅ есть | — |
| 7 | `LogcatToggleUseSoftWrapsToolbarAction` | Toggle переноса строк сообщения (влияет на editor soft wrap) | `AllIcons.Actions.ToggleSoftWrap` | ✅ есть (`wrapLines`, упрощённо: `maxLines=1` вместо честного horizontal-scroll) | доработать: implement реальный horizontal scroll вместо clip |
| 8 | `ImportLogcatAction` | Импорт лога из сохранённого файла (виден только под флагом `LOGCAT_EXPORT_IMPORT_ENABLED`) | — | ❌ нет | низкий приоритет (флаг выключен по умолчанию и в оригинале) |
| 9 | `SaveLogcatAction` | Экспорт текущего лога в файл (тоже под флагом) | — | ❌ нет | низкий приоритет |
| 10 | `LogcatFormatAction` | Открывает popup с пресетами формата (Standard/Compact) + "Modify Views..." | `AllIcons.Actions.Properties` | ⚠️ частично — есть popup "Configure view" с иным набором контролов (наши Time/Tag/Process чекбоксы + Process/Level дропдауны), не пресеты формата оригинала | см. раздел "Формат/колонки" ниже |
| 11 | `LogcatSplitterActions` | Открыть popup управления split-view (создать доп. панель рядом) | — | ❌ нет | высокая сложность, архитектурно не тривиально для standalone-окна |
| 12 | `SetProguardMappingAction` | Подключить ProGuard/R8 mapping-файл для деобфускации стектрейсов (под флагом) | — | ❌ нет | низкий приоритет |
| 13 | `ScreenshotAction` | Скриншот подключённого устройства (не окна Logcat!) | — | ❌ нет | требует доступа к экрану устройства через adb (`adb exec-out screencap`) — реализуемо, но это скриншот **устройства**, не лог-панели |
| 14 | `ScreenRecorderAction` | Запись видео с экрана устройства (не с UI!) | — | ❌ нет | средняя сложность через `adb shell screenrecord` |

**Важное уточнение**, ранее не учтённое: пункты 13-14 (`ScreenshotAction`/`ScreenRecorderAction`) — это **скриншот/запись экрана ПОДКЛЮЧЁННОГО УСТРОЙСТВА**, а не самого приложения Logcat! Это меняет оценку сложности реализации — не нужен window capture, а нужен `adb exec-out screencap -p` / `adb shell screenrecord`, что не сложнее, чем то, что уже делает `AdbLogStreamGateway`.

Дополнительные действия вне основного тулбара (context-menu / внутренние, не в `createToolbarActions()`):

| Класс | Назначение | В LogCactus |
|---|---|---|
| `CopyMessageTextAction` | Копировать текст сообщения(й) из выделения | частично — есть `SelectionContainer`, нет отдельного пункта меню, но копирование через Cmd+C доступно стандартно |
| `CreateScratchFileAction` | Открыть JSON/XML, найденный в теле сообщения, как scratch-файл в IDE | ❌ нет (и не имеет смысла вне IDE) |
| `IgnoreAppAction` / `IgnoreTagAction` | Добавить app/tag в игнор-лист (скрыть будущие сообщения) | ❌ нет |
| `LogcatFoldLinesLikeThisAction` | Свернуть все похожие строки (folding) | ❌ нет |
| `ToggleFilterAction` | Добавить/убрать терм под курсором в текущий фильтр (быстрый inline-toggle) | ❌ нет |
| `TerminateAppActions.ForceStopAppAction` / `KillAppAction` | Force-stop / kill процесса на устройстве прямо из Logcat | ❌ нет |
| `LogcatFormatPresetAction.Standard/Compact` | Быстрое переключение пресета формата (часть popup `LogcatFormatAction`) | ❌ нет как пресет (см. ниже) |
| `LogcatFormatModifyViewsAction` | Открыть диалог тонкой настройки формата (`LogcatFormatDialog`) | ⚠️ частично эквивалент — наш popup "Configure view", но другой набор полей |

## Устройства

`DeviceComboBox` (`devices/DeviceComboBox.kt`, 444 строки) — `ComboBox<DeviceComboItem>`, где `DeviceComboItem` — sealed class с вариантами как минимум `DeviceItem` (реальное/виртуальное устройство) и `FileItem` (открытый файл лога, отображается в том же селекторе как "источник"!). Модель устройства — `Device` (`devices/Device.kt`) — sealed class:
- `sdk: Int` — API level (у нас `apiLevel: String`, работает аналогично).
- Ветки создаются фабричными функциями `createPhysical(manufacturer, model, ...)` и `createEmulator(avdName, avdPath, ...)`.
- Есть `DeviceSerializer` (Gson) для персистентности в `LogcatPanelConfig`.

Обновление списка устройств идёт через `IDeviceComboBoxDeviceTracker`/`DeviceComboBoxDeviceTracker` (слушает события подключения/отключения) и `ConnectedDeviceFinder` — событийная модель (`DeviceEvent`), а не polling. LogCactus (`AdbDeviceCatalog`) делает **опрос по запросу** (`adb devices -l` + `getprop` при клике refresh) — функционально близко, но без live-обновления при физическом подключении/отключении устройства без нажатия refresh. Это осознанный компромисс, не критично для parity с точки зрения тестировщиков, но стоит знать.

Важно: реальный селектор устройства может также содержать **открытый файл лога** как альтернативный "источник" (`FileItem`) — у LogCactus такого режима нет вообще (это тесно связано с `files/LogcatFileData.kt`, `LogcatFileParser.kt`, `ImportLogcatAction` — весь этот функционал в LogCactus отсутствует).

## Фильтры

Язык фильтров подтверждён (`filters/LogcatFilterCompletionContributor.kt` + `LogcatBundle.properties`) — уже перенесён в LogCactus как статический список подсказок:
- Ключи: `tag:`, `age:`, `is:`, `level:`, `line:`, `message:`, `name:`, `package:`, `package:mine`, `process:`.
- Модификаторы на string-ключах (`message/package/tag/process/line`): негация (`-key:`), regex (`key~:`), regex+негация (`-key~:`), точное совпадение (`key=:`), точное+негация (`-key=:`) — **LogCactus поддерживает только regex (`key~:`) и негацию (`-key:`) по README, но НЕ поддерживает `key=:` (точное совпадение) как отдельный оператор** — это реальный пробел.
- `is:` принимает значения `crash`, `firebase`, `stacktrace`, плюс все уровни логов (`is:error` и т.п.) — у LogCactus `is:` есть в списке подсказок (только что добавлен), но неизвестно, поддерживает ли `LogcatFilterEngine` семантику `is:crash`/`is:firebase`/`is:stacktrace` — **это нужно проверить отдельно в коде `LogcatFilterEngine`/`LogQueryMatcher`, я это не проверял в рамках этого прохода**.
- Операторы согласно `LogcatBundle.properties` (подтверждено дословно): `~` для regex, `-` для негации, term'ы с одинаковым ключом комбинируются через OR, разные негированные термы — через AND, поддержаны скобки и `&`/`|`. Это совпадает с тем, что уже задокументировано в README LogCactus.
- **Реальный парсер — полноценный PSI-based language plugin** (`filters/parser/` — отдельная директория с грамматикой, не прочитана подробно в этом проходе) с error-аннотацией (`LogcatFilterErrorAnnotator`), syntax highlighting (`LogcatFilterSyntaxHighlighter`) и completion (`LogcatFilterCompletionContributor`, реагирует на позицию курсора через PSI tree, а не на "текущее слово после последнего пробела", как в LogCactus). LogCactus реализует **упрощённую версию**: статический список из 10 подсказок, фильтруемый по префиксу последнего "токена" в строке — рабочая, но не PSI-aware альтернатива (не понимает контекст типа "я внутри скобок" или "я ввожу значение для already-typed key").
- `AndroidLogcatFilterHistory` — история фильтров как отдельный сервис (не прочитан подробно). LogCactus имеет свою историю через `QueryHistoryService`/`FileQueryHistoryRepository` — функционально похоже.

## Формат/колонки

Это самое большое структурное расхождение. Реальная модель — `FormattingOptions` (`messages/FormattingOptions.kt`):

```
data class FormattingOptions(
  timestampFormat: TimestampFormat,     // DATETIME | TIME, toggle enabled
  processThreadFormat: ProcessThreadFormat,  // style (PID/BOTH/...), toggle enabled
  tagFormat: TagFormat,                 // maxLength, hideDuplicates, toggle enabled
  appNameFormat: AppNameFormat,         // maxLength, hideDuplicates, toggle enabled
  processNameFormat: ProcessNameFormat, // тот же тип что appNameFormat, отдельный toggle
  levelFormat: LevelFormat              // toggle enabled, фиксированная ширина 4
)
```

Два пресета — `Style.STANDARD` и `Style.COMPACT` (конкретные значения — в `AndroidLogcatFormattingOptions`, не прочитано подробно), либо `Custom` с ручными значениями. **Каждое строковое поле (tag/appName) умеет `hideDuplicates`** — не показывать тег/pid повторно, если он совпадает с предыдущей строкой (заменяется пробелами) — **этого нет в LogCactus вообще**, а это заметная визуальная особенность настоящего Logcat при быстром потоке однотипных логов.

LogCactus сейчас (`ComposeUiBridge`): `showTime/showTag/showProcess` (bool) + `timeWidth/tagWidth/processWidth` (Int, ручная настройка символьной ширины через +/-) — нет концепции "пресетов", нет `hideDuplicates`, нет отдельного toggle для PID/Package/ProcessName (они все смешаны в одну "Process" колонку через `formatLine()` в `ComposeUiBridge`).

Итог: `LogcatFormatAction` (иконка `AllIcons.Actions.Properties`, уже скопирована как `configure-view.svg`) в оригинале — это выбор пресета формата + доступ к тонкой настройке, а не то, что мы туда положили (Process/Level dropdowns + column toggles). Наш popup "Configure view" — samostoятельное, не 1:1 решение, которое просто использует ту же иконку по смысловой близости ("настройка отображения").

## Цвета/темы

Уже перенесено точно (см. `ThemePalette.kt`, значения проверены против `resources/colorSchemes/LogcatColorSchemeDarcula.xml`): `LOGCAT_V2_LEVEL_*` (фон+текст бейджа) и `LOGCAT_V2_MESSAGE_*` (цвет текста сообщения) — 1:1 hex.

**Новая находка, не перенесённая в LogCactus**: `LogcatColors.getTagColor(tag: String)` — реальный Logcat дополнительно красит **сам текст тега** (в колонке Tag) индивидуальным цветом на основе хеша строки тега через `ColorPaletteManager` (палитра из `resources/palette/logcat-tags-palette.json`, файл не скачан и не проверен в этом проходе). То есть в оригинале у каждого уникального тега (`ActivityManager`, `MyApp`, ...) — свой стабильный цвет, независимый от уровня лога. LogCactus красит текст сообщения только по уровню (`levelColor`), тег никак не выделяется цветом. Это заметное визуальное расхождение, которое видно на любом реальном скриншоте Studio (разноцветные имена тегов) и отсутствует у нас.

## Итоговая таблица расхождений (приоритеты)

| Область | Оригинал | LogCactus сейчас | Что сделать для паритета | Сложность |
|---|---|---|---|---|
| Цвет тега | Каждый уникальный тег — свой цвет (hash → палитра) | Тег не подсвечивается отдельно | Добавить hash-based цвет тега в `formatLine`/рендер строки (можно взять простую HSV-палитру, не обязательно точный JSON-файл) | низкая-средняя |
| `key=:` точное совпадение | Есть как модификатор всех string-ключей | Нет (только `~` regex и `-` негация) | Добавить в `LogcatFilterEngine`/`LogQueryMatcher` + в список автодополнения | средняя |
| Hide duplicates (tag/pid) | Повторяющийся tag/pid в соседних строках заменяется пробелами | Всегда показывается | Добавить toggle + логику в `formatLine` (нужен доступ к предыдущей отрендеренной строке) | низкая-средняя |
| Previous/Next occurrence | Навигация по строкам стектрейса (не поиск/фильтр!) | ✅ реализовано (2026-09-29) | — | — |
| Горячие клавиши | Ни у одного toolbar-действия Logcat нет default keyboard-shortcut (только Settings > Keymap для ручного назначения). Единственный реальный — generic Ctrl+Space (базовое автодополнение) | Ctrl+Space подключён (2026-09-29) | — | — |
| Формат-пресеты (Standard/Compact) | Реальные пресеты + custom, `hideDuplicates`, PID/ProcessName отдельно | Наш popup — другой набор полей (не 1:1) | Либо переименовать наш popup честно (не "Format"), либо реализовать настоящие пресеты | средняя |
| Screenshot/Screen record устройства | `adb exec-out screencap` / `adb shell screenrecord` | Нет | Реализуемо через новые adb-команды в `AdbDeviceCatalog`-подобном классе | низкая-средняя (проще, чем казалось раньше — это не захват UI-окна) |
| Per-tab персистентная конфигурация | `LogcatPanelConfig` на вкладку (device/filter/format/softwrap) | Общие глобальные preferences на все вкладки | Хранить `LogcatPanelConfig`-подобный снимок на каждый `LogcatTab` | средняя |
| Открытие файла лога как источника | `FileItem` в device combo, `ImportLogcatAction`, `files/` модуль | Нет вообще | Новый режим "источник = файл" + парсер формата файла | высокая |
| Split view | `LogcatSplitterActions` | Нет | Архитектурно нетривиально для standalone Compose-окна | высокая |
| PSI-aware автодополнение фильтра | Полноценный language plugin с грамматикой (`filters/parser/`, не прочитан), error-подсветка | ✅ реализовано (2026-09-29) — контекст по позиции курсора (KEY / level-is-age VALUE / tag-package-process VALUE), `TextFieldValue` вместо `String`, реальные tag/package/process из потока логов | Полный PSI-парсер с грамматикой и error-annotator (`LogcatFilterErrorAnnotator`) не реализован — не нужен для функционального паритета, только для inline-подсветки синтаксических ошибок | осталось: низкая (error-annotator), делать не стали — не запрашивалось |
| Funnel-иконка = история фильтров + счётчик | Клик по funnel в поле фильтра открывает список прошлых фильтров с колонкой "число совпадений" (кап "99+") | ✅ реализовано (2026-09-29, обнаружено пользователем по скриншоту) — та же история, что и у часов, + `ui.countMatches()` на каждый пункт | — | — |
| Ignore app/tag, force-stop/kill процесса | Есть в контекстном меню | Нет | Новые adb-команды + UI context menu | средняя |
| README LogCactus vs реальный Compose UI | — | README упоминает Snapshot, Saved filters editor (диалог), context-menu Copy Line/Copy Message/Clear, статус "Visible/Total" — **ни одно из этого не найдено в текущем `LogcatComposeScreen.kt`/`ComposeUiBridge.kt`** (похоже, относится к более ранней Swing-версии до миграции на Compose) | Актуализировать README под текущий Compose UI, либо перенести описанный функционал | — (это не расхождение с оригиналом, а внутренняя рассинхронизация документации LogCactus) |

## Непрочитанные файлы (пробелы этого прохода)

Не скачаны/не прочитаны из-за ограничений времени/токенов в рамках одного прохода:
- `filters/parser/` (вся директория — грамматика PSI-парсера фильтров)
- `filters/LogcatFilterSyntaxHighlighter.kt`, `LogcatFilterErrorAnnotator.kt`, `LogcatFilterParseException.kt`, `LogcatMessageWrapper.kt`
- `resources/palette/logcat-tags-palette.json` (палитра цветов тегов)
- `messages/AndroidLogcatFormattingOptions.kt` — скачан, но не прочитан подробно (там конкретные значения пресетов STANDARD/COMPACT)
- `messages/MessageFormatter.kt`, `MessageProcessor.kt`, `MessageBacklog.kt`, `TextAccumulator.kt`, `DocumentAppender.kt`, `AutoProguardMessageRewriter.kt`, `ExceptionMessageRewriter.kt`, `ProguardMessageRewriter.kt`, `RetraceUtils.kt`
- `folding/`, `hyperlinks/` (обе директории целиком — сворачивание стектрейсов, автоссылки на исходники SDK)
- `files/LogcatFileIo.kt`, `LogcatFileParser.kt` (частично — `LogcatFileData.kt` не прочитан)
- `util/` — большая часть файлов (кроме `FilterHint.kt`)
- `settings/` — `IgnoreValuesTextField.kt`, `LogcatApplicationSettingsConfigurable*.kt`
- Некоторые action-файлы скачаны, но не прочитаны построчно (только grep сигнатур): `ActionExtensions.kt`, `LogcatKeymapExtension.kt`, `PopupActionGroupAction.kt`
