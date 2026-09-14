import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

/**
 * Askya для Windows.
 *
 * Экраны, база и настройки — те же, что у телефона: всё это модуль `shared`.
 * Здесь окно, место на диске, где лежат данные, напоминания в трее, Слепок
 * и сборка установщика. Разделы — AskyaDay, Scroll и Ledger; Echo и AskyaV
 * остаются телефону (см. «Windows-версия» в README).
 */
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    // Среда отрисовки Compose под ту систему, на которой собирают: Skia для
    // Windows x64. Установщик собирается на Windows и под Windows.
    implementation(compose.desktop.currentOs)
    implementation("androidx.sqlite:sqlite-bundled:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

compose.desktop {
    application {
        mainClass = "app.askya.desktop.MainKt"

        nativeDistributions {
            // Exe и Msi — оба установщика Windows; собираются задачами
            // `packageExe` и `packageMsi` и оба требуют WiX Toolset 3 в PATH.
            // Без WiX собирается `createDistributable` — папка с Askya.exe,
            // которую можно просто скопировать.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Askya"
            // Номер установщика — три числа, как их понимает Windows; первые
            // два — те же, что у версии телефона.
            packageVersion = "2.9.0"
            // Описание Windows показывает именем программы — в заголовке
            // уведомления, в диспетчере задач. Латиницей и одним словом:
            // русский текст jpackage кладёт в exe не в той кодировке, и
            // напоминание приходило от «Р›РёС‡РЅС‹Р№ Р±Р»РѕРєРЅРѕС‚».
            description = "Askya"
            vendor = "Askya"
            copyright = "Askya"

            // Модули JDK, которые нужны сверх базовых: java.sql — драйверу
            // SQLite, jdk.unsupported — Compose и корутинам.
            modules("java.sql", "jdk.unsupported", "java.naming")

            windows {
                menuGroup = "Askya"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Один и тот же на все версии: по нему Windows узнаёт, что новая
                // сборка ставится поверх старой, а не рядом с ней.
                upgradeUuid = "4b0e9b8c-7f0a-4d3e-9a41-a5c1f2d9e6b7"
                iconFile.set(project.file("icon/askya.ico"))
            }
        }

        // Пробный запуск на отдельной папке данных, а не на настоящей:
        //   gradlew :desktop:run -Paskya.home=C:\temp\askya-try
        providers.gradleProperty("askya.home").orNull?.let { jvmArgs += "-Daskya.home=$it" }

        buildTypes.release.proguard {
            // Сжатие выключено: установщик и так весит десятки мегабайт из-за
            // среды Java, а ProGuard ломает Room и сериализацию молча — ровно
            // так же, как R8 у телефона (см. `proguard-rules.pro`).
            isEnabled.set(false)
        }
    }
}

/**
 * Askya на постоянное место — без установщика и WiX:
 *   gradlew :desktop:installAskya
 *
 * Собранная папка копируется в `%LOCALAPPDATA%\Programs\Askya` — туда же, куда
 * программы ставят себя сами для одного пользователя, — и в «Пуске» появляется
 * ярлык. Из `build` Askya запускать нельзя: `clean` сотрёт её вместе с
 * автозапуском, который на неё смотрит. Данные при этом не трогаются: они в
 * `%LOCALAPPDATA%\Askya`, отдельно от программы.
 *
 * Та же задача и обновляет: новая сборка ложится поверх старой, лишние файлы
 * старой уходят. Запущенную Askya Windows перезаписать не даст — поэтому о
 * ней спрашивается заранее, словами, а не ошибкой копирования на полпути.
 */
val installAskya by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Кладёт Askya в %LOCALAPPDATA%\\Programs\\Askya и делает ярлык в «Пуске»"
    dependsOn("createDistributable")

    val local = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
    val target = File(local, "Programs/Askya")
    from(layout.buildDirectory.dir("compose/binaries/main/app/Askya"))
    into(target)

    doFirst {
        val tasklist = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq Askya.exe", "/NH")
            .redirectErrorStream(true).start()
        val running = tasklist.inputStream.bufferedReader().readText()
        tasklist.waitFor()
        if ("Askya.exe" in running) {
            throw GradleException("Askya запущена — выйдите из неё через значок у часов и повторите.")
        }
    }

    doLast {
        // Ярлык умеет делать только сама Windows — через её WScript.Shell.
        val appData = System.getenv("APPDATA") ?: return@doLast
        val link = File(appData, "Microsoft/Windows/Start Menu/Programs/Askya.lnk")
        val exe = File(target, "Askya.exe")
        fun quoted(file: File) = "'" + file.absolutePath.replace("'", "''") + "'"
        val script = "\$s = (New-Object -ComObject WScript.Shell).CreateShortcut(${quoted(link)}); " +
            "\$s.TargetPath = ${quoted(exe)}; \$s.WorkingDirectory = ${quoted(target)}; " +
            "\$s.IconLocation = ${quoted(exe)}; \$s.Description = 'Askya'; \$s.Save()"
        val shell = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectErrorStream(true).start()
        val out = shell.inputStream.bufferedReader().readText()
        if (shell.waitFor() != 0) throw GradleException("ярлык в «Пуске» не сделался: $out")
        logger.lifecycle("Askya: ${exe.absolutePath}")
    }
}
