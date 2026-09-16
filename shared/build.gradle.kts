plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

/**
 * Общее у телефона и компьютера: база, настройки, разбор и экраны.
 *
 * Целей две, и обе — JVM: Android и Windows (`desktop`). Поэтому в `commonMain`
 * работают `java.time` и `java.io` — Kotlin не собирает для такой пары
 * отдельных «общих» метаданных, а каждая цель компилирует общий код своим
 * компилятором со своей JDK. Переписывать даты на kotlinx-datetime ради
 * платформ, которых у Askya нет, незачем.
 *
 * То, что устроено у двух систем по-разному, — выбор файла, ссылки, будильник,
 * путь к базе, — объявлено в `commonMain` через expect и лежит своим
 * исполнением в `androidMain` и `desktopMain`.
 */
kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    compilerOptions {
        // expect/actual классы — всё ещё «бета» по мнению компилятора, хотя
        // ведут себя стабильно; без флага каждый даёт предупреждение.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            // Набор значков остановился на 1.7.3: дальше Compose Multiplatform
            // его не выпускает, но и 1.7.3 работает с нынешним Compose.
            api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api(compose.components.resources)

            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            api("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")

            api("androidx.room:room-runtime:2.8.4")
            api("androidx.datastore:datastore-preferences-core:1.1.7")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        androidMain.dependencies {
            api("androidx.datastore:datastore-preferences:1.1.7")
            api("androidx.activity:activity-compose:1.9.3")
            api("androidx.core:core-ktx:1.15.0")
        }
        val desktopMain by getting {
            dependencies {
                // SQLite, собранный вместе с приложением: у Windows своей
                // SQLite для Java нет, а у Android она системная.
                implementation("androidx.sqlite:sqlite-bundled:2.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                // На телефоне org.json лежит в самой системе, на компьютере — нет.
                implementation("org.json:json:20240303")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        val desktopTest by getting {
            dependencies {
                implementation("org.json:json:20240303")
            }
        }
    }
}

android {
    namespace = "app.askya.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    // Схема в репозитории — чтобы миграции писались по diff'у, а не на глаз.
    // Каталог прежний по смыслу, но переехал вместе с базой из `app`.
    schemaDirectory("$projectDir/schemas")
}

/*
 * Путь к настоящей базе — для разовой проверки миграции перед выпуском:
 *
 *   gradlew :shared:desktopTest --tests "*RealDbMigrationCheck*" -Paskya.realdb=<файл из Слепка>
 *
 * Без свойства проверка молча пропускается: чужих баз в репозитории нет, а
 * новую миграцию лучше один раз прогнать на той базе, что стоит на телефоне,
 * чем узнать о ней от человека после обновления.
 */
tasks.withType<Test>().configureEach {
    providers.gradleProperty("askya.realdb").orNull?.let { systemProperty("askya.realdb", it) }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.8.4")
    add("kspDesktop", "androidx.room:room-compiler:2.8.4")
}

compose.resources {
    packageOfResClass = "app.askya.resources"
    publicResClass = true
}
