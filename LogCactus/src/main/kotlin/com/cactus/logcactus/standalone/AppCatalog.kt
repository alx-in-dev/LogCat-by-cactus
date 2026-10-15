package com.cactus.logcactus.standalone

import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.Executors
import javax.imageio.ImageIO

data class AppInfo(
  val packageName: String,
  val label: String,
  val iconFile: Path?
)

/**
 * Списывает установленные на устройстве приложения (для фильтра "app:") и
 * достаёт человекочитаемое имя + иконку каждого без скачивания полных APK:
 * на устройстве вырезаются только AndroidManifest.xml + resources.arsc
 * (обычно сотни КБ, не десятки МБ), из них собирается локально мини-APK,
 * который парсит `aapt2 dump badging` — оттуда берутся label и путь к
 * ресурсу иконки, который затем вырезается из настоящего APK ПРЯМО НА
 * УСТРОЙСТВЕ через `unzip` (на устройстве он есть штатно) и на комп
 * скачивается только сама иконка (обычно 10-50КБ).
 *
 * Все adb-обращения батчатся: НЕ по 4-6 вызовов на приложение, а один
 * push+shell+pull на фазу для ВСЕХ приложений разом (см. resolveAll/runBulkScript)
 * — иначе на реальном устройстве с ~150-200 сторонними пакетами список
 * приложений в "app:" собирается заметно медленно и визуально дёргано.
 *
 * Многие современные приложения (minSdk/targetSdk >= 26) объявляют иконку
 * только как adaptive icon (`res/.../ic_launcher.xml`, `<adaptive-icon>`) —
 * это не растровая картинка, и её "в лоб" не распаковать. Разрешаем это в
 * два уровня (оба проверены на реальных пакетах с устройства):
 *  1. Тот же mipmap-ресурс, что и эта XML, часто содержит СОСЕДНИЕ растровые
 *     варианты (PNG/WEBP на ldpi..xxxhdpi) — заложены для API < 26 / бэкапа.
 *     Если такой сосед есть (2GIS/AliExpress/SafetyCore) — берём его, без
 *     докачки чего-либо ещё.
 *  2. Если соседа нет, разбираем саму adaptive-icon XML (`aapt2 dump xmltree`)
 *     на background/foreground слои — у части приложений (типичный Google-шаблон:
 *     Docs/Keep/Translate/...) они САМИ растровые (mipmap с density-вариантами) и
 *     компонуются локально в один PNG (java.awt.Graphics2D). Если слои сами по
 *     себе — векторные XML без единого растрового варианта нигде (типично для
 *     Ozon/Яндекс/Сбербанк и части Google-иконок нового стиля) — растеризовать
 *     их без полноценного vector drawable рендерера нельзя, иконка не показывается
 *     (сознательный пробел, а не баг).
 *
 * Результаты кэшируются на диск (~/.logcactus/app-icons/), повторные запуски
 * читают из кэша мгновенно.
 */
class AppCatalog {
  private val cacheDir: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "app-icons")
  private val labelCacheFile: Path = cacheDir.resolve("labels.properties")
  private val labelCache: Properties = loadLabelCache()

  /** Локальный, не-adb lookup уже резолвнутого label'а (для "App name" колонки в логе — без сети/adb на каждую строку). */
  fun cachedLabel(packageName: String): String? = labelCache.getProperty(packageName)

  fun listPackages(serial: String): List<Pair<String, String>> {
    val adb = AdbLocator.path()
    val process = runCatching {
      ProcessBuilder(adb, "-s", serial, "shell", "pm", "list", "packages", "-3", "-f").redirectErrorStream(true).start()
    }.getOrNull() ?: return emptyList()

    val result = mutableListOf<Pair<String, String>>()
    runCatching {
      BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
        var line = reader.readLine()
        while (line != null) {
          parsePackageLine(line)?.let(result::add)
          line = reader.readLine()
        }
      }
      process.waitFor()
    }
    return result
  }

  // "package:/data/app/.../base.apk=com.example.app"
  private fun parsePackageLine(line: String): Pair<String, String>? {
    val trimmed = line.trim().removePrefix("package:")
    val eq = trimmed.lastIndexOf('=')
    if (eq < 0) return null
    val apkPath = trimmed.substring(0, eq)
    val pkg = trimmed.substring(eq + 1).trim()
    if (pkg.isEmpty() || apkPath.isEmpty()) return null
    // Значения уходят в shell-скрипт на устройстве — только безопасные символы.
    if (!SAFE_PACKAGE.matches(pkg) || !SAFE_PATH.matches(apkPath)) return null
    return apkPath to pkg
  }

  private sealed interface IconSpec {
    data class Direct(val path: String) : IconSpec
    data class Composite(val backgroundPath: String, val foregroundPath: String) : IconSpec
  }

  private data class Resolved(val idx: Int, val apkPath: String, val pkg: String, val label: String?, val icon: IconSpec?)

  /**
   * Резолвит label+icon для ВСЕХ пакетов сразу, батчами. Блокирующий вызов —
   * гонять в фоновом потоке. [onEach] может быть вызван ДВАЖДЫ на один пакет:
   * сперва с готовым label и iconFile=null (сразу после локального парсинга
   * badging, до докачки иконок — чтобы список не ждал самую медленную фазу
   * пустым), затем повторно с иконкой, когда она докачана/скомпонована.
   * Вызывающий код должен заменять запись по packageName, а не только добавлять.
   */
  fun resolveAll(serial: String, packages: List<Pair<String, String>>, onEach: (AppInfo) -> Unit) {
    Files.createDirectories(cacheDir)

    val pending = mutableListOf<Pair<String, String>>() // apkPath, pkg
    for ((apkPath, pkg) in packages) {
      val cachedIcon = cacheDir.resolve("$pkg.png").takeIf { Files.exists(it) }
      val cachedLabel = labelCache.getProperty(pkg)
      if (cachedIcon != null && cachedLabel != null) {
        onEach(AppInfo(pkg, cachedLabel, cachedIcon))
      } else {
        pending.add(apkPath to pkg)
      }
    }
    if (pending.isEmpty()) return

    val aapt2 = findAapt2()
    if (aapt2 == null) {
      // Без aapt2 иконок не будет вовсе, но названия пакетов пусть хоть
      // отобразятся как есть — фильтр всё равно рабочий (app: == package:).
      pending.forEach { (_, pkg) -> onEach(AppInfo(pkg, labelCache.getProperty(pkg) ?: pkg, null)) }
      return
    }

    val work = Files.createTempDirectory("logcactus-bulk")
    val remoteRoot = "/data/local/tmp/lct_appcat"
    var pool: java.util.concurrent.ExecutorService? = null
    try {
      // Фаза 1: одним push+shell+pull вытащить AndroidManifest.xml + resources.arsc
      // для ВСЕХ pending-пакетов разом (вместо 2 pull на каждое приложение).
      val remote1 = "$remoteRoot/p1"
      val script1 = buildString {
        appendLine("rm -rf $remote1; mkdir -p $remote1")
        for ((idx, entry) in pending.withIndex()) {
          val (apkPath, _) = entry
          appendLine("mkdir -p $remote1/$idx")
          appendLine("cd $remote1/$idx && unzip -o '$apkPath' 'AndroidManifest.xml' 'resources.arsc' >/dev/null 2>&1")
        }
      }
      val phase1Local = runBulkScript(serial, script1, remote1, work.resolve("p1"))
      if (phase1Local == null) {
        pending.forEach { (_, pkg) -> onEach(AppInfo(pkg, labelCache.getProperty(pkg) ?: pkg, null)) }
        return
      }

      // Фаза 2 (локально, параллельно — больше не трогает adb): badging + попытка
      // найти растровый "сосед" adaptive-icon XML (уровень 1, см. описание класса).
      // Для тех, где соседа не нашлось, откладываем в needsDeepResolve — им
      // нужно докачать саму XML и разобрать background/foreground (уровень 2).
      data class NeedsDeepResolve(val idx: Int, val apkPath: String, val pkg: String, val label: String?, val iconXmlPath: String, val dir: Path)

      // Daemon-потоки: незавершённый разбор иконок не должен удерживать процесс при выходе из приложения.
      val workers = Executors.newFixedThreadPool(minOf(8, Runtime.getRuntime().availableProcessors())) { r ->
        Thread(r, "app-catalog").apply { isDaemon = true }
      }
      pool = workers
      val resolved = java.util.Collections.synchronizedList(mutableListOf<Resolved>())
      val needsDeep = java.util.Collections.synchronizedList(mutableListOf<NeedsDeepResolve>())
      pending.mapIndexed { idx, (apkPath, pkg) ->
        workers.submit {
          val dir = phase1Local.resolve(idx.toString())
          val manifest = dir.resolve("AndroidManifest.xml")
          val resourcesArsc = dir.resolve("resources.arsc")
          if (!Files.exists(manifest) || !Files.exists(resourcesArsc)) {
            resolved.add(Resolved(idx, apkPath, pkg, null, null))
            return@submit
          }
          val mini = dir.resolve("mini.apk")
          zipEntries(mini, listOf(manifest to "AndroidManifest.xml", resourcesArsc to "resources.arsc"))
          val badging = runBadging(mini, aapt2)
          val iconPath = badging?.iconResPath
          when {
            iconPath == null -> resolved.add(Resolved(idx, apkPath, pkg, badging?.label, null))
            !iconPath.endsWith(".xml") -> resolved.add(Resolved(idx, apkPath, pkg, badging.label, IconSpec.Direct(iconPath)))
            else -> {
              val resourcesDump = dumpResources(mini, aapt2)
              val sibling = resourcesDump?.let { resolveRasterSibling(it, iconPath) }
              if (sibling != null) {
                resolved.add(Resolved(idx, apkPath, pkg, badging.label, IconSpec.Direct(sibling)))
              } else {
                needsDeep.add(NeedsDeepResolve(idx, apkPath, pkg, badging.label, iconPath, dir))
              }
            }
          }
        }
      }.forEach { it.get() }

      // Фаза 2.5 (bulk): докачать сами adaptive-icon XML для needsDeep-набора —
      // нужны, чтобы разобрать background/foreground (уровень 2 fallback'а).
      if (needsDeep.isNotEmpty()) {
        val remote25 = "$remoteRoot/p25"
        val script25 = buildString {
          appendLine("rm -rf $remote25; mkdir -p $remote25")
          for (n in needsDeep) {
            appendLine("mkdir -p $remote25/${n.idx}")
            appendLine("cd $remote25/${n.idx} && unzip -o '${n.apkPath}' '${n.iconXmlPath}' >/dev/null 2>&1")
          }
        }
        val phase25Local = runBulkScript(serial, script25, remote25, work.resolve("p25"))
        if (phase25Local != null) {
          needsDeep.map { n ->
            workers.submit {
              val xmlFile = phase25Local.resolve(n.idx.toString()).resolve(n.iconXmlPath)
              val manifest = n.dir.resolve("AndroidManifest.xml")
              val resourcesArsc = n.dir.resolve("resources.arsc")
              if (!Files.exists(xmlFile)) {
                resolved.add(Resolved(n.idx, n.apkPath, n.pkg, n.label, null))
                return@submit
              }
              val mini3 = n.dir.resolve("mini3.apk")
              zipEntries(mini3, listOf(manifest to "AndroidManifest.xml", resourcesArsc to "resources.arsc", xmlFile to n.iconXmlPath))
              val xmlDump = dumpXmlTree(mini3, n.iconXmlPath, aapt2)
              val resourcesDump = dumpResources(mini3, aapt2)
              val icon = if (xmlDump != null && resourcesDump != null) {
                val layers = parseAdaptiveLayerHex(xmlDump)
                val bg = layers["background"]?.let { resolveBestRasterForId(resourcesDump, it) }
                val fg = layers["foreground"]?.let { resolveBestRasterForId(resourcesDump, it) }
                if (bg != null && fg != null) IconSpec.Composite(bg, fg) else null
              } else null
              resolved.add(Resolved(n.idx, n.apkPath, n.pkg, n.label, icon))
            }
          }.forEach { it.get() }
        } else {
          needsDeep.forEach { n -> resolved.add(Resolved(n.idx, n.apkPath, n.pkg, n.label, null)) }
        }
      }
      workers.shutdown()

      // Отдаём label сразу для всех, не дожидаясь докачки иконок — список
      // "app:" перестаёт визуально казаться зависшим на всё время фазы 3.
      for (r in resolved) {
        val label = r.label?.takeIf { it.isNotBlank() } ?: r.pkg
        runCatching { labelCache.setProperty(r.pkg, label) }
        onEach(AppInfo(r.pkg, label, null))
      }
      runCatching { saveLabelCache() }

      // Фаза 3: одним push+shell+pull докачать САМИ иконки — как прямые файлы,
      // так и оба слоя для тех, что нужно компоновать.
      val withIcon = resolved.filter { it.icon != null }
      if (withIcon.isEmpty()) return

      val remote3 = "$remoteRoot/p3"
      val script3 = buildString {
        appendLine("rm -rf $remote3; mkdir -p $remote3")
        for (r in withIcon) {
          when (val icon = r.icon!!) {
            is IconSpec.Direct -> if (SAFE_PATH.matches(icon.path)) appendLine("unzip -o -p '${r.apkPath}' '${icon.path}' > $remote3/${r.idx}.icon 2>/dev/null")
            is IconSpec.Composite -> if (SAFE_PATH.matches(icon.backgroundPath) && SAFE_PATH.matches(icon.foregroundPath)) {
              appendLine("unzip -o -p '${r.apkPath}' '${icon.backgroundPath}' > $remote3/${r.idx}.bg 2>/dev/null")
              appendLine("unzip -o -p '${r.apkPath}' '${icon.foregroundPath}' > $remote3/${r.idx}.fg 2>/dev/null")
            }
          }
        }
      }
      val phase3Local = runBulkScript(serial, script3, remote3, work.resolve("p3")) ?: return

      for (r in withIcon) {
        val label = r.label?.takeIf { it.isNotBlank() } ?: r.pkg
        val dest = cacheDir.resolve("${r.pkg}.png")
        val iconFile = when (val icon = r.icon!!) {
          is IconSpec.Direct -> {
            val pulled = phase3Local.resolve("${r.idx}.icon")
            if (Files.exists(pulled) && Files.size(pulled) > 0) {
              runCatching { Files.copy(pulled, dest, StandardCopyOption.REPLACE_EXISTING) }.getOrNull()
              dest.takeIf { Files.exists(it) }
            } else null
          }
          is IconSpec.Composite -> {
            val bg = phase3Local.resolve("${r.idx}.bg").takeIf { Files.exists(it) && Files.size(it) > 0 }
            val fg = phase3Local.resolve("${r.idx}.fg").takeIf { Files.exists(it) && Files.size(it) > 0 }
            if (bg != null && fg != null && compositeIcon(bg, fg, dest)) dest else null
          }
        }
        onEach(AppInfo(r.pkg, label, iconFile))
      }
    } finally {
      pool?.shutdownNow()
      runCatching { work.toFile().deleteRecursively() }
      runCatching { shell(serial, "rm -rf $remoteRoot") }
    }
  }

  private data class Badging(val label: String?, val iconResPath: String?)

  /** Только локальный парсинг (манифест+resources.arsc уже на диске) — без adb. */
  private fun runBadging(mini: Path, aapt2: Path): Badging? {
    return try {
      val process = ProcessBuilder(aapt2.toString(), "dump", "badging", mini.toString())
        .redirectErrorStream(true)
        .start()
      val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
      process.waitFor()

      val labelMatch = Regex("application: label='([^']*)'").find(output)
      // Предпочитаем самую крупную плотность (обычно xxxhdpi/xxhdpi) — чётче иконка.
      val iconMatch = Regex("application-icon-640:'([^']*)'").find(output)
        ?: Regex("application-icon-480:'([^']*)'").find(output)
        ?: Regex("application-icon-320:'([^']*)'").find(output)
        ?: Regex("application: label='[^']*' icon='([^']*)'").find(output)
      Badging(labelMatch?.groupValues?.get(1), iconMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() })
    } catch (_: Exception) {
      null
    }
  }

  private fun dumpResources(mini: Path, aapt2: Path): String? = runCatching {
    val process = ProcessBuilder(aapt2.toString(), "dump", "resources", mini.toString())
      .redirectErrorStream(true)
      .start()
    val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    process.waitFor()
    text
  }.getOrNull()

  private fun dumpXmlTree(mini: Path, filePath: String, aapt2: Path): String? = runCatching {
    val process = ProcessBuilder(aapt2.toString(), "dump", "xmltree", mini.toString(), "--file", filePath)
      .redirectErrorStream(true)
      .start()
    val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    process.waitFor()
    text
  }.getOrNull()

  /**
   * В выводе `aapt2 dump resources` каждая resource-запись выглядит так:
   *   resource 0x7f110000 mipmap/icon
   *     (ldpi) (file) res/cH.png type=PNG
   *     ...
   *     (anydpi) (file) res/2r.xml type=XML
   * Заголовок идёт ПЕРЕД перечислением файлов-конфигураций (не после). Находим
   * блок, содержащий [xmlIconPath] среди своих файлов, и берём из него лучший
   * растровый (PNG/WEBP) вариант — самую крупную плотность.
   */
  private fun resolveRasterSibling(resourcesDump: String, xmlIconPath: String): String? {
    val lines = resourcesDump.lines()
    var blockStart = -1
    for (i in lines.indices) {
      val trimmed = lines[i].trim()
      if (trimmed.startsWith("resource 0x")) {
        blockStart = i
        continue
      }
      if (blockStart >= 0 && trimmed.contains(xmlIconPath)) {
        return bestRasterInBlock(lines, blockStart)
      }
      if (blockStart >= 0 && trimmed.isNotEmpty() && !FILE_LINE.matches(trimmed) &&
        !trimmed.startsWith("type ") && !trimmed.startsWith("Package") && !trimmed.startsWith("()")
      ) {
        // Вышли за пределы текущего блока файлов, не найдя xmlIconPath — сбрасываем.
        blockStart = -1
      }
    }
    return null
  }

  /** То же самое, но когда уже точно знаем resource id (0x-hex) — для слоёв adaptive icon. */
  private fun resolveBestRasterForId(resourcesDump: String, hex: String): String? {
    val lines = resourcesDump.lines()
    val headerIdx = lines.indexOfFirst { it.trim().startsWith("resource 0x$hex ") }
    if (headerIdx < 0) return null
    return bestRasterInBlock(lines, headerIdx)
  }

  private fun bestRasterInBlock(lines: List<String>, blockStart: Int): String? {
    val candidates = mutableListOf<Pair<String, String>>()
    var j = blockStart + 1
    while (j < lines.size) {
      val m = FILE_LINE.find(lines[j].trim()) ?: break
      candidates.add(m.groupValues[1] to m.groupValues[2])
      j++
    }
    return candidates
      .filter { (_, path) -> path.endsWith(".png", true) || path.endsWith(".webp", true) }
      .minByOrNull { (density, _) -> DENSITY_ORDER.indexOf(density).let { if (it < 0) 999 else it } }
      ?.second
  }

  /**
   * `aapt2 dump xmltree` для `<adaptive-icon>` выглядит так (два варианта
   * встречаются на реальных устройствах — прямой атрибут и вложенный <bitmap>):
   *   E: background
   *     A: ...:drawable(...)=@0x7f110001          // прямой вариант (2GIS/Ozon)
   *   E: foreground
   *     E: bitmap
   *       A: ...:src(...)=@0x7f110005             // вложенный вариант (Google apps)
   * В обоих случаях первый попавшийся hex ПОСЛЕ входа в слой и ДО следующего
   * слоя — то, что нужно, поэтому глубина вложенности не важна.
   */
  private fun parseAdaptiveLayerHex(xmlDump: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var currentLayer: String? = null
    for (raw in xmlDump.lineSequence()) {
      val trimmed = raw.trim()
      val layerMatch = Regex("^E:\\s*(background|foreground|monochrome)\\b").find(trimmed)
      if (layerMatch != null) {
        currentLayer = layerMatch.groupValues[1]
        continue
      }
      if (currentLayer != null && !result.containsKey(currentLayer) && trimmed.startsWith("A:")) {
        val hex = Regex("=@0x([0-9a-fA-F]+)").find(trimmed)?.groupValues?.get(1)
        if (hex != null) result[currentLayer] = hex
      }
    }
    return result
  }

  private fun compositeIcon(background: Path, foreground: Path, target: Path): Boolean {
    return try {
      val bgImg = ImageIO.read(background.toFile()) ?: return false
      val fgImg = ImageIO.read(foreground.toFile()) ?: return false
      val size = maxOf(bgImg.width, bgImg.height, fgImg.width, fgImg.height)
      val canvas = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val g = canvas.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      // Скруглённый квадрат — грубое приближение системной маски adaptive icon
      // (реальная форма зависит от лаунчера/OEM, единой маски не существует).
      val corner = size * 0.22
      g.clip = RoundRectangle2D.Double(0.0, 0.0, size.toDouble(), size.toDouble(), corner, corner)
      g.drawImage(bgImg, 0, 0, size, size, null)
      g.drawImage(fgImg, 0, 0, size, size, null)
      g.dispose()
      Files.createDirectories(target.parent)
      ImageIO.write(canvas, "png", target.toFile())
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Гоняет один shell-скрипт на устройстве одним push+shell (вместо десятков
   * отдельных adb-вызовов) и забирает результат одним рекурсивным pull.
   * `adb pull <remoteDir> <localBase>` кладёт содержимое НЕ прямо в localBase,
   * а в `localBase/<basename remoteDir>` — учитываем это при возврате пути.
   */
  private fun runBulkScript(serial: String, script: String, remoteDir: String, localBase: Path): Path? {
    val adb = AdbLocator.path()
    return runCatching {
      val scriptFile = Files.createTempFile("logcactus_bulk", ".sh")
      Files.writeString(scriptFile, script)
      val remoteScript = "/data/local/tmp/lct_script_${System.nanoTime()}.sh"
      exec(adb, "-s", serial, "push", scriptFile.toString(), remoteScript)
      exec(adb, "-s", serial, "shell", "sh", remoteScript)
      runCatching { Files.deleteIfExists(scriptFile) }
      runCatching { shell(serial, "rm -f $remoteScript") }
      Files.createDirectories(localBase)
      exec(adb, "-s", serial, "pull", remoteDir, localBase.toString())
      val resolved = localBase.resolve(remoteDir.substringAfterLast('/'))
      resolved.takeIf { Files.isDirectory(it) } ?: localBase
    }.getOrNull()
  }

  private fun exec(vararg cmd: String): Int {
    val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().readText()
    return process.waitFor()
  }

  private fun shell(serial: String, command: String): Boolean {
    val adb = AdbLocator.path()
    return runCatching {
      val process = ProcessBuilder(adb, "-s", serial, "shell", command).redirectErrorStream(true).start()
      process.inputStream.bufferedReader().readText()
      process.waitFor() == 0
    }.getOrDefault(false)
  }

  private fun zipEntries(target: Path, entries: List<Pair<Path, String>>) {
    java.util.zip.ZipOutputStream(Files.newOutputStream(target)).use { zos ->
      for ((source, name) in entries) {
        zos.putNextEntry(java.util.zip.ZipEntry(name))
        Files.copy(source, zos)
        zos.closeEntry()
      }
    }
  }

  private fun findAapt2(): Path? {
    val explicit = System.getProperty("logcactus.aapt2.path")
    if (!explicit.isNullOrBlank()) return Paths.get(explicit).takeIf { Files.exists(it) }

    // Зашитый в приложение aapt2 — в приоритете над SDK: работает и без Android SDK.
    Aapt2Locator.bundled()?.let { return it }

    val sdkRoot = System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
      ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
      ?: defaultSdkRoot()
    val buildTools = Paths.get(sdkRoot, "build-tools")
    if (!Files.isDirectory(buildTools)) return null
    val exe = if (System.getProperty("os.name").lowercase().contains("win")) "aapt2.exe" else "aapt2"
    return Files.list(buildTools).use { stream ->
      stream
        .filter { Files.exists(it.resolve(exe)) }
        // Числовое сравнение версий: 34.0.0 новее 9.0.0 (строковая сортировка ставила "9" выше "34").
        .sorted { a, b -> compareVersions(b.fileName.toString(), a.fileName.toString()) }
        .findFirst()
        .map { it.resolve(exe) }
        .orElse(null)
    }
  }

  private fun defaultSdkRoot(): String {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    return when {
      os.contains("win") -> (System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local") + "\\Android\\Sdk"
      os.contains("mac") -> Paths.get(home, "Library", "Android", "sdk").toString()
      else -> Paths.get(home, "Android", "Sdk").toString()
    }
  }

  private fun loadLabelCache(): Properties {
    val props = Properties()
    if (Files.exists(labelCacheFile)) {
      runCatching { Files.newInputStream(labelCacheFile).use { props.load(it) } }
    }
    return props
  }

  private fun saveLabelCache() {
    Files.createDirectories(cacheDir)
    runCatching { AtomicFiles.write(labelCacheFile) { labelCache.store(it, null) } }
  }

  companion object {
    /** Сравнивает «34.0.0-rc1»-подобные версии по числовым компонентам. */
    internal fun compareVersions(a: String, b: String): Int {
      val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
      val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
      for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
        if (c != 0) return c
      }
      return 0
    }

    private val DENSITY_ORDER = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi", "tvdpi", "nodpi")

    // "type=XXX" в хвосте строки печатается не всегда (напр. для .webp aapt2
    // его часто опускает, хотя для .png печатает) — не полагаемся на него,
    // растровость определяем по расширению пути в bestRasterInBlock().
    private val SAFE_PACKAGE = Regex("""[\w.]+""")
    private val SAFE_PATH = Regex("""[\w./@+=,:~-]+""")
    private val FILE_LINE = Regex("^\\((\\w+)\\)\\s*\\(file\\)\\s*(res/[\\w./@+=,-]+)(?:\\s+type=(\\w+))?$")
  }
}
