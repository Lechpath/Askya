plugins {
    // 8.9.1, а не 8.7: этого требует androidx.health.connect — библиотека,
    // через которую раздел Active читает намеренное часами. Меньшей версии,
    // которая бы её устроила, нет; более старая сборка библиотеки есть, но она
    // alpha, а в этом проекте alpha не держат.
    //
    // Gradle менять не пришлось: 8.9 требует 8.11.1, и он уже стоит.
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false

    // Kotlin 2.2, а не 2.1: этого требует Windows-версия. Экраны, база и
    // настройки у телефона и компьютера общие (модуль `shared`), и собираются
    // они мультиплатформенными сборками Compose, навигации и Room — а самая
    // старая стабильная навигация, которая умеет работать вне Android, сама
    // собрана Kotlin 2.2.20 и более старому компилятору не читается.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("androidx.room") version "2.8.4" apply false
}
