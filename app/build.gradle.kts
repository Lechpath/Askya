import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * Ключ, которым подписывается выпуск.
 *
 * Лежит он вне проекта, а пути и пароли к нему — в `keystore.properties` в
 * корне, и оба закрыты от git. Причина не в осторожности вообще: репозиторий
 * публичный, а ключом, попавшим в чужие руки, подписывают что угодно от имени
 * Askya, и телефон примет это как обновление.
 *
 * Файла нет — сборка выпуска остаётся неподписанной, но собирается. Так и
 * должно быть: на чужом компьютере проект должен собираться, а не падать на
 * отсутствии чужого секрета.
 */
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.askya"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.askya"
        // minSdk 26 даёт java.time нативно — desugaring не нужен.
        minSdk = 26
        targetSdk = 36
        // Номер сборки растёт на единицу с каждой выпущенной сборкой — по нему
        // система решает, что ставить поверх чего; имя версии — то, что видит
        // человек в настройках, в строке «Askya».
        versionCode = 16
        versionName = "2.5"

        // Только те платформы, которые бывают у телефонов.
        //
        // Это ограничение появилось вместе с libVLC: у него в каждой сборке
        // лежат нативные библиотеки под все четыре платформы Android, и
        // каждая — десятки мегабайт. x86 и x86_64 остаются эмуляторам; на
        // руках их нет ни у одного устройства, ради которого Askya пишется.
        // Всё остальное в приложении — байткод и работает везде.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val store = signingProps.getProperty("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Своим ключом, а не отладочным. Отладочная сборка помечена
            // `debuggable`, и на телефоне, кроме своего, это значит, что базу
            // приложения — записи, расписание, Ledger — вычитает любой
            // компьютер с включённой отладкой. Раздавать такое нельзя.
            //
            // Ключа нет — подписи нет: неподписанную сборку система не поставит
            // и скажет об этом, а подписанная отладочным ключом молча выдала бы
            // себя за выпуск.
            signingConfig = if (signingProps.isEmpty) null else signingConfigs.getByName("release")
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Схема в репозитории — чтобы миграции писались по diff'у, а не на глаз.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Видео AskyaV. Единственная тяжёлая зависимость приложения и единственная,
    // которую нечем заменить: системный MediaPlayer знает mp4, webm и часть
    // mkv, а от раздела ждут VLC — avi, flv, wmv, ts, ogv, субтитры внутри
    // контейнера, выбор звуковой дорожки. Всё это умеет ровно libVLC, потому
    // что внутри у него ffmpeg, и написать это «своими руками», как разбор
    // epub, невозможно: там четыреста строк, здесь — сотни тысяч.
    //
    // Ветка 3.x, а не 4.0: у четвёртой в Maven только eap, а плеер — не то
    // место, где стоит жить на предрелизах.
    implementation("org.videolan.android:libvlc-all:3.7.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test"))
    // Нужен ради runTest: разбор рассказа — suspend-функция, потому что за ней
    // однажды встанет модель, а не только правила.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // org.json входит в android.jar, но в JVM-тестах это заглушка: при
    // isReturnDefaultValues она молча возвращает пустоту вместо разбора.
    // Настоящая реализация в тестовом classpath перекрывает её; на устройстве
    // по-прежнему используется системная.
    testImplementation("org.json:json:20240303")
}
