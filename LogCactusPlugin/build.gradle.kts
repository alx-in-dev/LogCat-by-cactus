plugins {
    id("java")
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.cactus"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Для воспроизводимой сборки и верификатора; локальная установка нужна только для runIde (см. ai/architecture.md).
        local(providers.gradleProperty("studioPath").orElse("/Applications/Android Studio.app"))
        bundledPlugin("org.jetbrains.android")
        // Compose и Jewel — те, что уже есть в IDE: свои копии в плагин не кладём (две версии Skiko в одной JVM не работают).
        bundledModule("intellij.platform.compose")
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.compose.runtime.desktop")
        bundledModule("intellij.libraries.skiko")
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi") }
}

// Чистая (без UI) логика основного приложения подключается как исходники, а не копируется в репозиторий плагина.
val sharedSrc = layout.projectDirectory.dir("../LogCactus/src/main/kotlin")
val syncShared by tasks.registering(Sync::class) {
    from(sharedSrc) {
        include(
            "com/android/tools/idea/logcat/standalone/core/LogEntry.kt",
            "com/android/tools/idea/logcat/standalone/core/LogLevel.kt",
            "com/android/tools/idea/logcat/standalone/core/LogQueryMatcher.kt",
            "com/android/tools/idea/logcat/standalone/core/AdbLongLogcatParser.kt",
            "com/android/tools/idea/logcat/standalone/core/LogcatFilterCriteria.kt",
            "com/android/tools/idea/logcat/standalone/core/LogcatFilterEngine.kt",
            "com/cactus/logcactus/presentation/logcat/RowDetails.kt",
            "com/cactus/logcactus/presentation/logcat/UrlDetails.kt",
            "com/cactus/logcactus/presentation/logcat/BugReport.kt",
            "com/cactus/logcactus/presentation/logcat/CrashTracker.kt",
            "com/cactus/logcactus/standalone/DeviceInfo.kt",
            "com/cactus/logcactus/standalone/ForegroundApp.kt",
            "com/cactus/logcactus/standalone/DeviceActions.kt",
            "com/cactus/logcactus/standalone/WifiAdb.kt",
            "com/cactus/logcactus/standalone/SessionFile.kt",
            "com/cactus/logcactus/standalone/AtomicFiles.kt",
            "com/cactus/logcactus/presentation/logcat/SessionDiff.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared"))
}
kotlin.sourceSets.main { kotlin.srcDir(syncShared) }

intellijPlatform {
    buildSearchableOptions = false
    // Подпись и публикация: секреты только из переменных окружения, в репозитории их нет.
    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
    pluginVerification {
        ides { local(providers.gradleProperty("studioPath").orElse("/Applications/Android Studio.app")) }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set(provider { null })
        }
    }
}

tasks.runIde {
    // В свежей песочнице у Студии нет каталога whatsnew и она пишет в журнал NoSuchFileException (к плагину не относится).
    doFirst { sandboxSystemDirectory.get().asFile.resolve("whatsnew").mkdirs() }
    args(rootDir.resolve("../LogCactus").canonicalPath)
}
