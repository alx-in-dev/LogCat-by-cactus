# Архитектура: Standalone Logcat из Android Studio
Дата: 2026-02-20

## ADR (Architecture Decision Records)
### ADR-1: Phase-1 через re-hosting на IntelliJ Platform runtime
- Контекст: текущий Logcat UI жёстко интегрирован с IntelliJ API (`ToolWindow`, `RunnerLayoutUi`, `ActionSystem`, `Disposer`) и Android plugin wiring (`android-plugin.xml`).
- Решение: в Phase-1 сохранить текущий UI/поведение, вынеся Logcat в отдельное standalone-приложение на IntelliJ Platform runtime с отдельным bootstrap-модулем.
- Альтернативы:
  - Полный rewrite UI (Compose Desktop/JavaFX/Swing) сразу.
  - Попытка запускать исходный код только как plugin внутри Android Studio.
- Последствия: минимальный риск потери UI-паритета, но сохраняется зависимость от IntelliJ API на первом этапе.

### ADR-2: Декомпозиция на Core и Host Adapters
- Контекст: сейчас UI, сервисы и host-интеграция частично смешаны.
- Решение: разделить на:
  - `logcat-core` (поток логов, форматирование, фильтрация, state);
  - `host-studio-adapter` (ToolWindow/facet/services Android Studio);
  - `host-standalone-adapter` (окно standalone + bootstrap).
- Альтернативы: прямой перенос существующих классов без разграничения.
- Последствия: упрощается тестирование и постепенное снижение platform lock-in.

### ADR-3: Adapter-first для platform-bound зависимостей
- Контекст: device selection, preferences storage, toolbar/actions и lifecycle зависят от host APIs.
- Решение: выделить портовые интерфейсы (`DeviceProvider`, `PreferencesStore`, `LogStreamGateway`, `UiHost`) и реализовать адаптеры для Studio и standalone.
- Альтернативы: условная логика `if (studio) ... else ...` в тех же классах.
- Последствия: выше начальная стоимость рефакторинга, но ниже риск регрессий и проще миграция Phase-2.

## Компоненты

### 1. Existing Studio Logcat (источник)
- Модуль/пакет: `tools/adt/idea/android/src/com/android/tools/idea/logcat/*`
- Ключевые классы:
  - `AndroidLogcatToolWindowFactory` — создание ToolWindow-контента.
  - `LogcatPanel` — сборка panel-layout (device/search + console center).
  - `AndroidLogcatView` — orchestration устройства, фильтров, toolbar, receiver lifecycle.
  - `AndroidLogConsole` — console actions, regex filter UI.
  - `AndroidLogcatService` — lifecycle listeners и поток `adb logcat`.
- Wiring: `tools/adt/idea/android/src/META-INF/android-plugin.xml` (`facet.toolWindow`, services, extension point `com.android.logcat.filterProvider`).

### 2. logcat-core (новый переносимый слой)
- Тип: library module.
- Ответственность:
  - контракты потока логов;
  - форматирование/фильтрация;
  - состояния UI (selected device/client/filter/query/regex);
  - orchestration без прямой зависимости на ToolWindow/ActionSystem.
- Включает переносимые части текущей реализации:
  - логика `AndroidLogcatReceiver` и форматтеров;
  - модель фильтрации и state updates;
  - listener contract для потока сообщений.

### 3. host-studio-adapter (совместимость с текущим Studio)
- Тип: adapter module.
- Ответственность:
  - bridge между `logcat-core` и текущим Studio host;
  - сохранение текущего поведения ToolWindow/facet lifecycle;
  - интеграция с проектными сервисами (`AndroidLogcatPreferences`, `PersistentAndroidLogFilters`).

### 4. host-standalone-adapter (новый)
- Тип: standalone app bootstrap + host adapter.
- Ответственность:
  - поднимать отдельное окно Logcat в standalone-контейнере;
  - подключать `logcat-core` через портовые интерфейсы;
  - обеспечить визуально близкий layout и toolbar behavior.

### 5. resources-pack (UI parity assets)
- Тип: resources module.
- Содержимое:
  - иконки logcat toolbar/toolwindow;
  - формы/рендер-конфигурации;
  - текстовые ключи/локализация, связанные с Logcat UI.

## Потоки данных
1. Host bootstrap (studio или standalone) создаёт `LogcatSessionController`.
2. `DeviceProvider` публикует текущий `DeviceContext`/client selection.
3. `LogStreamGateway` подписывается на устройство и поставляет поток `LogEntry`.
4. `LogcatReceiver/Formatter` нормализует и форматирует сообщения.
5. `FilterEngine` применяет level/filter/query/regex.
6. `LogcatViewModelState` обновляет UI state.
7. `UiHost` рендерит консоль, фильтры и toolbar actions.
8. `PreferencesStore` сохраняет пользовательские настройки фильтров/формата.

## Контракты (псевдокод)
```kotlin
interface LogStreamGateway {
  fun subscribe(deviceId: String, listener: (LogEntry) -> Unit): Subscription
  fun clear(deviceId: String)
}

data class LogEntry(
  val timestamp: Long,
  val level: String,
  val tag: String,
  val pid: Int,
  val message: String,
)

interface DeviceProvider {
  fun selectedDevice(): DeviceRef?
  fun selectedClient(): ClientRef?
  fun addListener(listener: DeviceSelectionListener)
}

interface PreferencesStore {
  fun load(): LogcatUiPreferences
  fun save(prefs: LogcatUiPreferences)
}

interface UiHost {
  fun render(state: LogcatUiState)
  fun showError(text: String)
}

class LogcatSessionController(
  private val devices: DeviceProvider,
  private val logs: LogStreamGateway,
  private val prefs: PreferencesStore,
  private val ui: UiHost,
) {
  fun start()
  fun stop()
  fun onAction(action: LogcatAction)
}
```

## Границы и соответствие требованиям
- Сохраняем UI-паритет за счёт re-hosting существующих компонентов в Phase-1.
- Минимизируем риски через адаптеры, а не through rewrite.
- Оставляем путь к Phase-2: постепенное вытеснение IntelliJ-specific API из `logcat-core`.

## Конвейер рендера логов (standalone Compose, с 1.1.0)
Поток: `adb logcat -v long` → `AdbLongLogcatParser` → `ProcessMetadataResolver` (pid→имя процесса/пакет, `adb shell ps`, троттлинг, фоновое обновление) → `LogcatSessionController` → `LogcatUiState.append` (скользящее окно 5000 строк, неизменяемые снапшоты) → `ComposeUiBridge.render` (складывает `pendingState`, цикл рендера каждые 66 мс) → `flushPendingRender`:
1. `LogSnapshotIndex.update` (worker) — инкрементально по дельте окна: отфильтрованные строки (уровень + query + выбранный процесс), список процессов, теги/пакеты/имена процессов для автодополнения. Дельта определяется по идентичности `LogEntry` (выпало k строк спереди + новые в хвосте), иначе полная пересборка.
2. `LogRenderCache.render` — форматирует только новые строки в `RenderedLine` (с стабильным `id`), при сдвиге окна переиспользует готовые; сигнатура (`renderSignature`) сбрасывает кэш при смене колонок/темы/фильтров отображения.
3. `visibleRows` (Compose state) → `LazyColumn(key = RenderedLine.id)`; автоскролл к концу — `snapshotFlow` по id последней строки.

Инварианты (нарушение = деградация перфа или сломанный автоскролл):
- чтения `ui.visibleRows` не должны попадать в тело верхнеуровневого `LogcatComposeScreen` (только внутрь отдельных composable/`snapshotFlow`/`LazyColumn`), иначе на каждое обновление перекомпонируется весь экран;
- `LogEntry` в снапшотах — те же экземпляры (сравнение по `===`), нельзя копировать записи при append/фильтре;
- ключ эффекта автоскролла — id последней строки, а не размер списка (размер постоянен при полном буфере).


## Инструменты тестировщика (standalone Compose, 1.2, не выпущено)

Компоненты (все чистые части покрыты юнит-тестами):
- `StackFolder` (+ `RowFold`, `RowDetails`) — пост-обработка списка `RenderedLine` *после* `LogRenderCache`: серии кадров стектрейса (`isFrame`, ≥ 4 подряд) → строка-заглушка, JSON/Intent → дочерние DETAIL-строки. Возвращает тот же экземпляр списка, если сворачивать нечего. `baseRows` (чистый кэш рендера) хранится в мосте, `visibleRows` = результат свёртки. Ключ раскрытия — `LogEntry` по идентичности (`expandedEntries`). Копирование/баг-репорт идут через `StackFolder.plainLines` (заглушки разворачиваются в кадры, DETAIL пропускаются).
- Парсер отдаёт **одну `LogEntry` на строку** long-формата, поэтому стектрейс — подряд идущие записи, а не многострочное сообщение.
- `CrashTracker` — инкрементальный детектор по несфильтрованному окну (`feed` берёт записи после последней обработанной по идентичности); группы живут дольше окна. Кормится в `flushPendingRender` на worker-потоке, методы `@Synchronized`.
- `BugReport`, `DeviceInfoCollector`, `DeviceActions`, `AdbShell` — `standalone`: разовые `adb shell` с таймаутом; пакет и URI валидируются перед подстановкой в shell (`[\w.]+`, без кавычек/пробелов).
- `SessionFile` (`.logcactus`: строка-сигнатура, `#ключ=значение`, записи через TAB с экранированием) и `SessionDiff`. Вкладка-сессия — `ComposeUiBridge` без `bind`/контроллера, данные подаются через `loadSession`.
- Целевой пакет действий и отчётов: слежение `mine` → выбранный процесс → приложение на экране (`resolvePackage`, блокирующий, только на IO).

Инварианты (нарушение = краш или перф-регрессия):
1. Тексты popup/меню внутри `SelectionContainer` должны быть под `DisableSelection` (см. `NoSelectionContextMenuRepresentation`), иначе при активном выделении падает `SelectionManager`.
2. Закладки держат `LogEntry` по идентичности (`===`), не по `equals` (data class): одинаковые строки не должны склеиваться.
3. Новые обработчики на строках списка (клики, `pointerInput`) вешаются только на те строки, которым нужны (`hasLinks`, `isStackFold`), — иначе растёт цена композиции строки.
4. Детекторы на горячем пути (`isFrame`, `mayBeExpandable`, URL) — без regex на каждую строку каждого кадра; полный разбор только для раскрытых/по запросу.

## ADR: встроенный MCP-сервер (прототип, 2026-10-04)
- **Решение:** MCP-сервер живёт внутри приложения (`com.cactus.logcactus.mcp`), HTTP на `127.0.0.1:7788` (`POST /mcp`, Streamable HTTP без SSE), JDK `HttpServer`, JSON-RPC руками поверх `kotlinx-serialization-json` (только `JsonElement`, без compiler-плагина). Официальный Kotlin SDK не берём: ему нужен Kotlin 2.x, а приложение держит 1.9.24 из-за Compose 1.6.11.
- **Почему внутри, а не отдельным процессом:** агент видит ту же сессию, что и человек (буфер, краши, закладки, `package:mine`), и не конфликтует за adb.
- **Только чтение:** `list_devices`, `get_logs(filter, level, scope=visible|buffer, limit)`, `get_crashes`, `get_bug_report(mode)`, `take_screenshot`. Действия над устройством (force stop, clear data…) сознательно не выдаются.
- **Безопасность:** слушает только loopback; `Host` и `Origin` проверяются (защита от DNS-rebinding); по умолчанию выключен — меню устройства → «AI agents» (запоминается файлом `~/.logcactus/mcp.enabled`) или `-Dlogcactus.mcp=true`, порт `-Dlogcactus.mcp.port`. Токена нет: прототип рассчитан на один локальный пользовательский сеанс.
- **Потоки:** состояние читается на Swing-потоке (`BridgeMcpSource`), долгие вызовы adb (отчёт, скриншот) — на потоке MCP.
- **Упаковка:** в jlink-runtime добавлен `jdk.httpserver` (без него `.dmg` не стартует с флагом). Упакованную сборку с MCP НЕ проверяли (только `./gradlew run` + curl на живом телефоне).
- **Известные ограничения:** работает с активной вкладкой (выбора вкладки нет); отчёт берёт приложение «на экране», поэтому в режиме `crashes` шапка может не совпадать с пакетом краша; нет токена и лимита запросов; плагин Студии без MCP.
- **Подключение клиента:** `claude mcp add --transport http logcactus http://127.0.0.1:7788/mcp`.
