import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        versionCode = 20
        versionName = "2.9"

        // Одна платформа: arm64-v8a.
        //
        // Ограничение появилось вместе с libVLC: у него в каждой сборке лежат
        // нативные библиотеки под все четыре платформы Android, и каждая —
        // десятки мегабайт. Сперва ушли x86 и x86_64 — они эмуляторам.
        // Теперь ушла и armeabi-v7a, и это тридцать девять мегабайт из ста
        // десяти: один её libvlc.so весил больше, чем всё остальное
        // приложение вместе с байткодом.
        //
        // Цена названа прямо: на 32-битном телефоне сборка не поставится
        // вовсе — система скажет, что пакет не подходит устройству. Таких
        // телефонов не выпускают с 2018 года, и ни одного из них нет среди
        // тех, ради которых Askya пишется.
        //
        // Всё остальное в приложении — байткод и работает везде.
        ndk {
            abiFilters += listOf("arm64-v8a")
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
            // R8: выбросить неиспользуемое и переименовать остальное.
            //
            // Прежде было выключено, и половину выпуска составлял байткод
            // набора значков material-icons-extended — тысячи штук ради ста
            // тридцати восьми, которые Askya показывает. Выбрасывать их по
            // одному вручную значило бы держать список в голове; R8 считает
            // сам.
            //
            // Что при этом нельзя трогать и почему — в `proguard-rules.pro`.
            // Читать его стоит целиком перед каждой правкой: R8 ломает не
            // сборку, а работу, и молча.
            isMinifyEnabled = true
            isShrinkResources = true
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

dependencies {
    // Всё общее с Windows-версией — база, настройки, разбор и экраны AskyaDay,
    // Scroll, Ledger — лежит в `shared`. Здесь остаётся то, что есть только у
    // телефона: Echo, AskyaV, виджеты, будильники, шторка, мосты.
    implementation(project(":shared"))

    // Та же линейка, что стоит за Compose Multiplatform 1.9.3 в модуле
    // `shared`: ui 1.9.4 и material3 1.4.0. Разойдись они — Gradle всё равно
    // взял бы старшую, но уже молча.
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    // База объявлена в `shared`, и Room разбирает её там же; сюда она
    // приходит готовой.
    implementation("androidx.room:room-runtime:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // org.json входит в android.jar, но в JVM-тестах это заглушка: при
    // isReturnDefaultValues она молча возвращает пустоту вместо разбора.
    // Настоящая реализация в тестовом classpath перекрывает её; на устройстве
    // по-прежнему используется системная.
    testImplementation("org.json:json:20240303")
}
