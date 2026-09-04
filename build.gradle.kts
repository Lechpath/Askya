plugins {
    // 8.9.1, а не 8.7: этого требует androidx.health.connect — библиотека,
    // через которую раздел Active читает намеренное часами. Меньшей версии,
    // которая бы её устроила, нет; более старая сборка библиотеки есть, но она
    // alpha, а в этом проекте alpha не держат.
    //
    // Gradle менять не пришлось: 8.9 требует 8.11.1, и он уже стоит.
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
