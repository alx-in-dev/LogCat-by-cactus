# LogCactus

Android Logcat как отдельное десктопное приложение (Kotlin + Compose Multiplatform, macOS и Windows).

## Структура
- `src/main/kotlin/.../standalone/core` — переносимый core (контракты, state, controller).
- `src/main/kotlin/.../presentation/logcat/compose` — Compose UI (`LogcatComposeScreen.kt`, `ComposeUiBridge.kt`).
- `../docs/` — архитектура (`architecture-app.md`, `architecture-plugin.md`) и справочник по New Logcat V2 (`logcat_v2_reference.md`); история изменений — `../CHANGELOG.md`.

## Текущее состояние
- Текущая версия: **1.3.10** (см. `../CHANGELOG.md`).
- UI полностью на Kotlin/Compose Multiplatform (JVM desktop), `src/main` не содержит Java.
- Вкладки Logcat/Logcat(2)/... + "+" — каждая со своей независимой сессией (устройство/фильтр/поток).
- Визуальный паритет с реальной Android Studio Logcat (New Logcat V2) активно приводится в соответствие: реальные иконки/цвета/палитра тегов из исходников `JetBrains/android` — см. `../docs/logcat_v2_reference.md` (карта расхождений).

## Запуск PoC
- Рекомендуемый JDK для запуска Gradle: **17**.
  - Пример для macOS: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew run -Dlogcactus.backend=adb`
- ADB backend (по умолчанию):
  - `./gradlew run`
  - или явно: `./gradlew run -Dlogcactus.backend=adb`
  - путь к adb можно переопределить: `-Dlogcactus.adb.path=/path/to/adb`
- Demo backend (любое значение, кроме `adb`, включает синтетический поток; `simple` = `demo`):
  - `./gradlew run -Dlogcactus.backend=demo`
  - скорость синтетического потока — `-Dlogcactus.simple.rate=N` (строк/с, по умолчанию ~3): воспроизводимая нагрузка для замеров производительности.
- Отладочные свойства: `-Dlogcactus.renderMs=N` — интервал цикла рендера списка логов (по умолчанию 66 мс).

## Фильтры (текущее приближение к Studio)
- Поля: `tag:`, `is:`, `level:`, `line:`, `message:`, `name:`, `package:`, `package:mine`, `process:`, `age:`.
- `app:` — **своя фича LogCactus** (в оригинальном Logcat такого ключа нет): псевдоним `package:` с UI-пикером приложений устройства с иконками в автодополнении (`app:com.example` работает идентично `package:com.example`). `aapt2` зашит в приложение (см. раздел про adb), Android SDK не нужен; работает только с реальным устройством (adb backend), не с demo. Список приложений и их иконки достаются батчами (один push+shell+pull на фазу для ВСЕХ приложений разом, не по 4-6 adb-вызовов на каждое) — на реальном устройстве с ~185 сторонними пакетами полный холодный проход занимает секунды/десятки секунд, а не минуты; названия приложений показываются сразу, иконки подгружаются следом. Adaptive icon (XML без единой картинки) разрешается в два уровня: сперва ищется растровый "сосед" той же resource-записи, а если его нет — background/foreground слои adaptive icon компонуются локально в один PNG. У части приложений (обычно с векторными Material-иконками нового стиля, без единого растрового варианта нигде в APK — Ozon, Яндекс-семейство, Сбербанк и т.п.) иконка всё равно не показывается — это осознанный пробел (нужен полноценный vector drawable рендерер), не баг.
- Операторы: `!`, `-` (например `-tag:Noise`), `&`/`&&`, `|`/`||`, скобки `( )`, а также неявный `AND` по пробелу.
- Модификаторы полей: `key~:pattern` — regex (например `tag~:My.*Tag`), `key=:value` — точное совпадение (например `tag=:MyApp`).
- `age:` поддерживает суффиксы времени `s/m/h/d` (например `age:30s`, `age:5m`).
- Фразы: используйте кавычки, например `message:\"receiver mode\"`.
- Автодополнение: подсказки по полям/модификаторам показываются по мере ввода, либо по Ctrl+Space на пустом поле (как в реальной Studio).
- `Cc` включает case-sensitive режим (по умолчанию регистронезависимый).
- `Pause` временно останавливает визуальное обновление потока логов.
- История запросов сохраняется между сессиями (иконка часов рядом с полем фильтра).
- "Configure view" (иконка настроек в левом тулбаре) — выбор процесса, минимального уровня, видимости колонок Time/Tag/App name/Process (каждая с настраиваемой шириной), "Hide duplicate tags". App name — человекочитаемое имя приложения (тот же резолвер label'ов, что у пикера `app:`), а не голый package id — реальный Logcat тоже держит это отдельно от Process/PID (`FormattingOptions.appNameFormat`). Для системных пакетов (вне `pm list packages -3`) label не резолвится, показывается package/process как фолбэк.
- Тег каждой строки красится в стабильный цвет по хешу (как в реальном Logcat), сам текст сообщения — по уровню лога.
- Навигация по строкам стектрейса (стрелки вверх/вниз в левом тулбаре) — переход между `at Foo.bar(File.java:1)` без выхода за пределы видимых строк.
- `Cmd+F`/`Ctrl+F` — поиск по видимым строкам лога (как обычный IntelliJ editor find, поверх которого в реальной Studio и рендерится Logcat): `Cc`/`W`/`.*` (case sensitive/whole word/regex), счётчик результатов, `Enter`/`Shift+Enter` или стрелки — навигация, `Escape` — закрыть.
- Список устройств обновляется автоматически (опрос `adb devices` раз в 2с) — устройство, подключённое уже после запуска приложения, появляется в дропдауне без перезапуска.
- Правый клик по строке лога — `Copy`, `Copy Message Text`, `Search with Google`, `Clear Logcat` (действует на строку под курсором, не на drag-выделение).
- Горизонтальный скролл текста сообщения, когда soft-wrap выключен (`wrap-text` в боковом тулбаре) — колонка уровня остаётся на месте.

Не реализовано (осознанно, за рамками текущего прохода — см. `../docs/logcat_v2_reference.md`): Snapshot/экспорт лога в файл, диалог редактирования сохранённых фильтров, статус "Visible / Total", split view (в контекстном меню строки — вместо Split Right/Split Down/Close используйте вкладки Logcat/+), Force stop application/Kill process/Explain This Selection в контекстном меню, screenshot/screen-record устройства.

## Инструменты тестировщика (в разработке, версия 1.2)

| Что | Как |
|---|---|
| Баг-репорт | кнопка в боковой панели или Cmd/Ctrl+Shift+C: Markdown с устройством, версией приложения, фильтром и логом. Строки: закладки, иначе хвост видимых; «Copy Bug Report Around This Line» в меню строки |
| Закладки | клик по бейджу уровня строки, F2 / Shift+F2 — переход |
| Стектрейсы | серии кадров свёрнуты (`▸ N stack frames`), клик раскрывает; отключается в Configure view |
| Ссылки, JSON, Intent | URL кликабельны; «Expand JSON / Intent» в контекстном меню строки |
| Краши и ANR | кнопка-предупреждение: группы, счётчик, переход, копирование трейса |
| Устройство | кнопка «телефон»: скриншот (`~/Pictures/LogCactus` + буфер), restart, force stop, clear data, deep link, app info, сессии |
| Сессии | Save / Open (новая вкладка) / Compare — файлы `.logcactus` |
| Wi-Fi | меню «телефон» → Connect via Wi-Fi…: из USB, pairing по коду (Android 11+), connect по IP:port, Scan |
| Справка | кнопка «?» в поле фильтра: хоткеи и синтаксис |
| `package:mine` | приложение на экране устройства, чип можно закрепить кликом |

Все кнопки имеют тултипы. UI проверяется headless (`ImageComposeScene`).

## Canvas-рендер списка (с 1.3.0 — по умолчанию)
Список логов рисуется одним `Canvas` вместо Compose-узлов на строку: CPU ниже примерно в 1,7–2,4 раза, RSS меньше. Поддерживается всё, что было в прежнем списке: выделение и копирование, контекстное меню, закладки, ссылки, свёртка стектрейсов, JSON/Intent, поиск Cmd+F, скроллбар. Аварийный откат на прежний список: запуск с `-Dlogcactus.canvas=false`. Отличие: у Canvas нет построчной доступности для экранных читалок (только описание списка целиком). Подробности, замеры и ограничения — `ai/impl_notes.md`.

## Сборка дистрибутивов (.dmg / .msi)
Общее для обеих платформ:
- `packageVersion` (`1.3.7` в `build.gradle.kts`) задан отдельно от `version` проекта (оба `1.3.7`, менять синхронно) — паковщику (`jpackage`) нужен строгий `major.minor.patch`.
- `id("application")`-плагин убран из `build.gradle.kts` (конфликтовал с задачей `run`, которую и так регистрирует `compose.desktop.application`) — `./gradlew run -Dlogcactus.backend=...` работает как раньше, системные свойства теперь пробрасываются через `jvmArgs` в блоке `compose.desktop { application { ... } }`.
- Имя иконки/Dock/`.app`/`.exe`/`.dmg`/`.msi` — "Logcat" (`packageName`), заголовок окна — "LogCat by cactus" (`title` в `Window(...)` в `ComposeLogcatApplication.kt`) — две независимые настройки, намеренно разные по просьбе пользователя.
- **aapt2 зашит так же** (`Aapt2Locator.kt`, `aapt2-NOTICE.txt`): macOS — universal Mach-O из build-tools 37.0.0 (`logcactus/bin/aapt2`), Windows — `logcactus/bin/win/aapt2.exe` из `com.android.tools.build:aapt2:9.4.1`; извлекается в `~/.logcactus/bin/`, приоритет выше SDK, `-Dlogcactus.aapt2.path` выше всего. Проверка — `Aapt2LocatorTest` (гонять на обеих ОС).
- **adb зашит прямо в приложение** (Apache 2.0, `adb-NOTICE.txt` рядом) — тот, кому передали дистрибутив, не должен сам ставить Android SDK: macOS — `src/main/resources/logcactus/bin/adb` (universal Mach-O arm64+x86_64), Windows — `src/main/resources/logcactus/bin/win/` (`adb.exe` + `AdbWinApi.dll` + `AdbWinUsbApi.dll`, platform-tools 37.0.1). `AdbLocator.kt` при первом обращении извлекает их в `~/.logcactus/bin/` (кэшируется) и использует в приоритете над системным поиском по `PATH`/известным путям установки. Явный `-Dlogcactus.adb.path=...` всё ещё в приоритете. Тест `AdbLocatorTest` проверяет извлечение и запуск `adb version` (гонять на обеих ОС).
  - Важный сопутствующий баг-фикс (macOS): раньше все adb-вызовы дефолтились на голое `"adb"` (PATH lookup) — работало из терминала/`./gradlew run` (наследуют шелл-PATH), но НЕ в упакованном `.app`: Finder/double-click даёт минимальный системный PATH без Android SDK/Homebrew — собранный `.dmg` показывал "No Devices", хотя `adb devices` в Terminal видел устройство.
- Runtime приложения (jlink) включает `jdk.jfr` (`modules("jdk.jfr")`) — для профилирования через `jcmd <pid> JFR.start`.
- JVM-аргументы приложения: `-Xmx384m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30` (по умолчанию heap раздувался до 25% RAM).
- Сборка требует JDK 17 от не-Homebrew вендора (например Corretto): `JAVA_HOME=~/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home ./gradlew ...` — иначе `checkRuntime` падает ("Homebrew's JDK distribution may cause issues with packaging").
- Иконки — `icons/AppIcon.icns` (macOS) и `icons/AppIcon.ico` (Windows), собраны из одного исходника (Python/PIL-пайплайн, не Xcode — своего Xcode-проекта у LogCactus нет).

### macOS
- `./gradlew packageDmg` — обычная (не минифицированная) сборка, `build/compose/binaries/main/dmg/Logcat-<version>.dmg`. Рекомендуется для разового "передать другу" — надёжнее и быстрее `packageReleaseDmg`.
- `./gradlew createReleaseDistributable` / `packageReleaseDmg` — минифицированная через ProGuard версия (`proguard-rules.pro`: `-dontwarn **`, без обфускации, keep своих пакетов); собирается (`.app` 118 МБ против 165 МБ), но по CPU/памяти от обычной **не отличается** (замерено) — для рассылки используем обычную `packageDmg`. Правила минимальные, на всех сценариях не проверялись.
- Собранный `.app`/`.dmg` — только под архитектуру машины сборки (сейчас arm64/Apple Silicon); на Intel-Маках не запустится, `jpackage` не кросс-компилирует.
- Подпись Developer ID включается свойством `-PmacSignIdentity="<Имя из сертификата>"` (см. комментарий в `build.gradle.kts`), нотаризация — вручную через `notarytool`. Без подписи Gatekeeper на чужом Mac покажет "unidentified developer": открыть через правый клик → Open (или System Settings → Privacy & Security → Open Anyway).

### Windows
- `gradlew.bat packageMsi` — `build/compose/binaries/main/msi/Logcat-<version>.msi`. Требует WiX Toolset v3.x (`candle.exe`/`light.exe` в PATH или зарегистрированы в реестре) — если не стоит, `compose.desktop.application` умеет сам докачать WiX 3.11.2 во время сборки (задачи `downloadWix`/`unzipWix`), можно и не ставить вручную.
- `windows { shortcut = true; menu = true; menuGroup = "Logcat" }` — по умолчанию compose.desktop эти флаги выключены (`false`), поэтому инсталлятор без них не предлагает ни ярлык на рабочий стол, ни пункт в "Пуск". "Закрепить на панели задач" через MSI не делается в принципе — у Windows Installer нет для этого API.
- `upgradeUuid` зафиксирован (иначе jpackage генерит новый на каждую сборку, и Windows Installer не распознаёт новую версию как апгрейд старой — может встать рядом отдельным пунктом вместо замены).
- Обновление: `.msi` — major upgrade (фиксированный `upgradeUuid`, проверено по таблицам `Upgrade`/`RemoveExistingProducts` в собранном файле): новая версия ставится поверх старой, установка более старой поверх новой блокируется. Работает только между сборками с одним и тем же `upgradeUuid`. Версию меняем в `version` и `packageVersion` синхронно.
- Архитектура/ОС собираются только под ту машину, где реально гоняется `gradlew` — `jpackage` не кросс-компилирует. Под Windows нужна отдельная Windows-машина.

## Релиз для друзей
1. Поднять `version` и `packageVersion` в `build.gradle.kts`, дописать `CHANGELOG.md`.
2. `JAVA_HOME=<Corretto 17> ./gradlew test packageDmg` (Mac) и `gradlew.bat test packageMsi` на Windows-машине (залить исходники, собрать, забрать `.msi`).
3. Собрать папку `build/release-<версия>/`: `.dmg`, `.msi`, `README-for-friends.txt` (Gatekeeper/SmartScreen, отладка по USB), `SHA256SUMS.txt`.
4. Дистрибутивы не подписаны: macOS — правый клик → Open или `xattr -dr com.apple.quarantine /Applications/Logcat.app`; Windows — SmartScreen «Подробнее → Выполнить в любом случае».

## Профилирование и замеры производительности
- Воспроизводимая нагрузка: `-Dlogcactus.backend=demo -Dlogcactus.simple.rate=100` (в `Logcat.app/Contents/app/Logcat.cfg` — строки `java-options=...`).
- CPU мерить через `top -l 6 -s 4 -pid <pid> -stats pid,cpu` (мгновенный), а **не** `ps %cpu` (сглаженное среднее занижает). Запускать напрямую `.../Logcat.app/Contents/MacOS/Logcat`, а не `open -g` (фоновый запуск завышал цифры). Шум большой — брать несколько прогонов, сравнивать A/B в одинаковых условиях.
- Профиль: `jcmd <pid> JFR.start settings=profile duration=30s filename=x.jfr`, затем `jfr print --events jdk.ExecutionSample,jdk.NativeMethodSample ...`. Java-сэмплы не видят нативный код Skia — для него смотреть `NativeMethodSample` или `sample <pid>` (macOS). Веса `jdk.ObjectAllocationSample` по классам ненадёжны — проверять счётчиками.
- Ориентиры версии 1.1.0: ~2% CPU без нагрузки, ~15–20% при 100 строк/с, ~30% при 300 строк/с; RSS ~300 МБ. Расход растёт с числом строк (раскладка текста Skia на каждую новую строку), а не с частотой кадров.

## JSON-тема (цвета UI)
Можно переопределить все ключевые цвета через JSON:

- Дефолтный путь: `~/.logcactus/theme.json`
- Или явно: `-Dlogcactus.theme.path=/absolute/path/theme.json`
- Пример: `theme.sample.json`
- Hot reload: файл темы отслеживается во время работы приложения и применяется автоматически (без перезапуска).

Поддерживаемые ключи:
- `uiBgEven`, `uiBgOdd`, `uiBgHover`, `uiBgActive`
- `uiTextActive`, `uiTextPrimary`, `uiTextMuted`
- `uiSeparator`, `uiAccent`
- `levelError`, `levelWarn`, `levelInfo`, `levelDebug`, `levelVerbose`, `levelAssert` — цвет текста сообщения по уровню
- `badgeErrorBg/Fg`, `badgeWarnBg/Fg`, `badgeInfoBg/Fg`, `badgeDebugBg/Fg`, `badgeVerboseBg/Fg`, `badgeAssertBg/Fg` — фон/текст буквы-бейджа уровня (I/D/W/E/V/A)

Дефолтные значения (`theme.sample.json`) взяты дословно из реальной цветовой схемы Android Studio New Logcat (`LogcatColorSchemeDarcula.xml`, `JetBrains/android`, Apache-2.0) — не приближены на глаз. Цвет тега (стабильный hash-based, `TagPalette.kt`) в JSON-теме пока не настраивается — палитра из 80 реальных цветов зашита в код.
