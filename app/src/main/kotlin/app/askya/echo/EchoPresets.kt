package app.askya.echo

import kotlin.math.ln

/**
 * Заготовка эквалайзера — кривая подъёма, а не набор значений для конкретных
 * полос.
 *
 * Полос у телефона может быть пять, а может десять, и центральные частоты у
 * каждого производителя свои. Поэтому заготовка задана дюжиной опорных точек
 * ([REFERENCE_HZ]) в децибелах, а под полосы устройства она подгоняется
 * [levelsFor] — так «Rock» звучит роком и на пятиполосном эквалайзере, и на
 * десятиполосном.
 *
 * Системные заготовки устройства («Rock», «Jazz» от вендора) больше не
 * берутся: их набор на разных телефонах разный — где-то четыре, где-то
 * ни одной, — и человек, привыкший к «Vocal», на новом телефоне его не
 * находил. Свои двадцать четыре есть везде и звучат одинаково.
 */
data class EchoPreset(val name: String, val gainsDb: List<Float>)

/** Опорные частоты кривой: октавами от 31 Гц до 16 кГц. */
val REFERENCE_HZ = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

/**
 * Заготовки. Порядок — от «ничего не тронуто» к жанрам и дальше к особым
 * случаям: чаще всего нажимают первые.
 *
 * Кривые — классический набор, знакомый по десктопным плеерам: те же имена
 * означают то же самое, и человеку не нужно заново нащупывать, что скрыто за
 * словом «Lounge».
 */
val ECHO_PRESETS = listOf(
    EchoPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EchoPreset("Acoustic", listOf(4.5f, 4.5f, 3.5f, 1f, 1.5f, 1.5f, 3f, 3.5f, 3.5f, 2.5f)),
    EchoPreset("Bass boost", listOf(6f, 5f, 4f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
    EchoPreset("Bass cut", listOf(-6f, -5f, -4f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f)),
    EchoPreset("Classical", listOf(5f, 4f, 3f, 2.5f, -1.5f, -1.5f, 0f, 2f, 3f, 4f)),
    EchoPreset("Dance", listOf(4f, 7f, 5f, 0f, 2f, 3f, 5f, 4f, 3f, 0f)),
    EchoPreset("Deep", listOf(5f, 4f, 1.5f, 1f, 3f, 2f, 1.5f, -2f, -3.5f, -4.5f)),
    EchoPreset("Electronic", listOf(4f, 4f, 1f, 0f, -2f, 2f, 1f, 1f, 4f, 5f)),
    EchoPreset("Hip-hop", listOf(5f, 4.5f, 1.5f, 3f, -1f, -1f, 1.5f, -1f, 2f, 3f)),
    EchoPreset("Jazz", listOf(4f, 3f, 1.5f, 2f, -2f, -2f, 0f, 1f, 2f, 4f)),
    EchoPreset("Latin", listOf(5f, 3f, 0f, 0f, -2f, -2f, -2f, 0f, 3f, 5f)),
    EchoPreset("Live", listOf(-3f, 0f, 2f, 3f, 3.5f, 3.5f, 3f, 2.5f, 2.5f, 2f)),
    EchoPreset("Lounge", listOf(-3f, -1f, -1f, 2f, 4f, 2f, 0f, -2f, 2f, 1f)),
    EchoPreset("Loudness", listOf(6f, 4f, 0f, 0f, -2f, 0f, -1f, -5f, 5f, 1f)),
    EchoPreset("Night", listOf(4f, 3f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f)),
    EchoPreset("Piano", listOf(3f, 2f, 0f, 3f, 3f, 1f, 3f, 4f, 3f, 3f)),
    EchoPreset("Pop", listOf(-1.5f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1.5f)),
    EchoPreset("R&B", listOf(3f, 7f, 6f, 1f, -2f, -1.5f, 2f, 3f, 3f, 4f)),
    EchoPreset("Rock", listOf(5f, 4f, 3f, 1.5f, -0.5f, -1f, 0.5f, 3f, 4f, 4.5f)),
    EchoPreset("Small speakers", listOf(6f, 5f, 4f, 2.5f, 1f, 0f, -1f, -2f, -3f, -4f)),
    EchoPreset("Spoken word", listOf(-3.5f, 0f, 0f, 0.5f, 3.5f, 4f, 4.5f, 4f, 2f, 0f)),
    EchoPreset("Treble boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f)),
    EchoPreset("Treble cut", listOf(0f, 0f, 0f, 0f, 0f, -1f, -3f, -4f, -5f, -6f)),
    EchoPreset("Vocal boost", listOf(-1.5f, -3f, -3f, 1.5f, 4f, 4f, 3f, 1.5f, 0f, -1.5f)),
)

/**
 * Кривая, переложенная на полосы устройства.
 *
 * Частоты полос ложатся на кривую по логарифму: слух устроен октавами, и на
 * прямой шкале точка 250 Гц оказалась бы почти у нуля, а вся разница между
 * басом и серединой сжалась бы в первый процент ширины.
 *
 * Итог обрезается по пределам самого эквалайзера ([minMb], [maxMb]): у
 * большинства телефонов это ±15 дБ, но встречаются и ±12, и просить у системы
 * больше, чем она умеет, — значит получить отказ на всю полосу.
 */
fun EchoPreset.levelsFor(frequencies: List<Int>, minMb: Int, maxMb: Int): List<Int> =
    frequencies.map { hz -> (gainAt(hz) * 100).toInt().coerceIn(minMb, maxMb) }

/** Подъём кривой на частоте — линейно между соседними опорными точками. */
private fun EchoPreset.gainAt(hz: Int): Float {
    if (hz <= REFERENCE_HZ.first()) return gainsDb.first()
    if (hz >= REFERENCE_HZ.last()) return gainsDb.last()

    val upper = REFERENCE_HZ.indexOfFirst { it >= hz }
    val lower = upper - 1
    val low = REFERENCE_HZ[lower].toFloat()
    val high = REFERENCE_HZ[upper].toFloat()
    // Доля пути между опорными частотами — в логарифме, а не в герцах.
    val share = (ln(hz.toFloat()) - ln(low)) / (ln(high) - ln(low))
    return gainsDb[lower] + (gainsDb[upper] - gainsDb[lower]) * share
}

/**
 * Какая заготовка сейчас набрана на полосах — или `null`, если ни одна.
 *
 * Нужна после перезапуска: полосы восстанавливаются из настроек числами, а имя
 * под ними должно подсветиться само. Сравнение с допуском в полдецибела:
 * полосы округляются к шагу устройства, и точного совпадения не бывает, — но
 * допуск пошире начинает узнавать заготовку в том, что телефон поставил сам.
 */
fun presetMatching(levelsMb: List<Int>, frequencies: List<Int>, minMb: Int, maxMb: Int): String? =
    ECHO_PRESETS.firstOrNull { preset ->
        val expected = preset.levelsFor(frequencies, minMb, maxMb)
        expected.size == levelsMb.size &&
            expected.zip(levelsMb).all { (want, has) -> kotlin.math.abs(want - has) <= 50 }
    }?.name
