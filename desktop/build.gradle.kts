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
