plugins {
    // 8.13 — из-за R8 внутри. R8 из 8.9 не знал метаданных Kotlin 2.2 и на
    // каждом классе выпуска писал «An error occurred when parsing kotlin
    // metadata» — сотни строк, за которыми не видно настоящих предупреждений.
    // Сборка при этом была рабочей, но проверять её приходилось на телефоне,
    // а не по журналу.
    //
    // Последняя из линейки 8, а не 9: девятая меняет устройство сборки целиком —
    // Kotlin встроен в сам плагин, а у мультиплатформенного `shared` плагин
    // Android другой. Ради R8 столько переделывать незачем. 8.13 требует
    // Gradle 8.13 и новее — стоит 8.14.3.
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false

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
