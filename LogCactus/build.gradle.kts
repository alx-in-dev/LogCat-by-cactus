import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("java")
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.cactus"
version = "1.3.12"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("com.formdev:flatlaf:3.5.1")
    implementation("com.formdev:flatlaf-extras:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("io.insert-koin:koin-core:3.5.6")
    // Только JsonElement-API для MCP (без compiler-плагина): официальный MCP SDK требует Kotlin 2.x.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

// Пакет compose.desktop.application сам регистрирует "run" — раньше это делал
// плагин application (теперь убран, иначе конфликт имён задач). Системные
// свойства -Dlogcactus.* передаются так же, как раньше: читаются с самого
// Gradle-инвока (`./gradlew run -Dlogcactus.backend=adb`) и пробрасываются в
// запускаемую JVM через jvmArgs — поведение `./gradlew run` не поменялось.
compose.desktop {
    application {
        mainClass = "com.cactus.logcactus.standalone.ComposeLogcatApplicationKt"
        // Без этого `./gradlew run` (голый java-процесс, не настоящий .app bundle)
        // показывал в Dock/меню подсказку "java" вместо имени приложения —
        // упакованный .dmg этим не страдает (там CFBundleName уже верный), но
        // при разработке через gradlew это заметно (баг, замеченный пользователем).
        // -Dapple.awt.application.name не сработал (проверено эмпирически) —
        // -Xdock:name того же семейства, что и -Xdock:icon (его сам compose.desktop
        // уже подставляет для иконки, и она реально работает).
        // Имя иконки/Dock намеренно короче заголовка окна (см. title в Window(...)
        // в ComposeLogcatApplication.kt) — две независимые настройки по просьбе пользователя.
        // -Xdock:* — macOS-only JVM-флаг, на других ОС ломает запуск.
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            jvmArgs += "-Xdock:name=Logcat"
        }
        // Профиль показал: живых данных ~25 МБ, а heap раздувался до 1+ ГБ мусором
        // (по умолчанию Xmx = 25% RAM, G1 не спешит отдавать память ОС).
        jvmArgs += listOf("-Xmx768m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=${System.getProperty("user.home")}/.logcactus", "-XX:+UseSerialGC", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30")
        jvmArgs += "-Dlogcactus.backend=${System.getProperty("logcactus.backend", "adb")}"
        System.getProperty("logcactus.adb.path")?.let { jvmArgs += "-Dlogcactus.adb.path=$it" }
        System.getProperty("logcactus.package")?.let { jvmArgs += "-Dlogcactus.package=$it" }
        System.getProperty("logcactus.pid")?.let { jvmArgs += "-Dlogcactus.pid=$it" }
        System.getProperty("logcactus.mcp")?.let { jvmArgs += "-Dlogcactus.mcp=$it" }
        System.getProperty("logcactus.mcp.port")?.let { jvmArgs += "-Dlogcactus.mcp.port=$it" }
        buildTypes.release.proguard {
            // Release-сборка: ProGuard только сжимает/оптимизирует байткод (без обфускации).
            configurationFiles.from(project.file("proguard-rules.pro"))
            obfuscate.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Logcat"
            // jlink-runtime приложения урезан, и без этого модуля JFR (jcmd JFR.start) недоступен —
            // нужен для профилирования; стоит пару МБ.
            // jdk.httpserver — встроенный MCP-сервер; без него упакованное приложение не стартует с -Dlogcactus.mcp.
            modules("jdk.jfr", "jdk.httpserver")
            // group/version в начале файла — "1.3.7", а jpackage под macOS
            // требует строгий major.minor.patch — задаём отдельно только для дистрибутива.
            packageVersion = "1.3.12"
            macOS {
                iconFile.set(project.file("icons/AppIcon.icns"))
                bundleID = "com.cactus.logcactus"
                // Подпись Developer ID включается, только если задано имя сертификата (часть строки
                // "Developer ID Application: <Имя> (<TEAM>)", jpackage сам добавляет префикс):
                //   ./gradlew packageReleaseDmg -PmacSignIdentity="<Имя>"   (или в ~/.gradle/gradle.properties)
                // Без свойства сборка не подписывается. Нотаризацию делаем вручную, Gradle-задача
                // notarizeReleaseDmg передаёт "@keychain:..." в notarytool строкой и получает 401:
                //   codesign --force --sign "Developer ID Application: <Имя> (<TEAM>)" --timestamp <dmg>
                //   xcrun notarytool submit <dmg> --keychain-profile <профиль> --wait
                //   xcrun stapler staple <dmg>
                providers.gradleProperty("macSignIdentity").orNull?.let { signName ->
                    signing {
                        sign.set(true)
                        identity.set(signName)
                    }
                }
            }
            windows {
                iconFile.set(project.file("icons/AppIcon.ico"))
                // По умолчанию у compose.desktop оба выключены (false) — инсталлятор
                // ничего не предлагал добавить ни в Пуск, ни на рабочий стол
                // (пользователь заметил). "Закрепить на панели быстрого доступа"
                // через MSI в принципе не делается — у Windows Installer нет для
                // этого API, только сам пользователь через контекстное меню.
                shortcut = true
                menu = true
                menuGroup = "Logcat"
                // Фиксированный UUID — иначе jpackage генерит новый на каждую
                // сборку, и Windows Installer не понимает, что это апгрейд той же
                // программы (может неправильно накатиться поверх старой версии).
                upgradeUuid = "8e4a86ca-6b44-4a07-924a-04bef056edd1"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Большие диалоги в тестах рисуются внутри сцены, а не отдельными окнами ОС.
    systemProperty("logcactus.inlineDialogs", "true")
    // По умолчанию тесты гоняют старый список; Canvas-тесты включают свой режим явно и возвращают "false".
    systemProperty("logcactus.canvas", "false")
}
