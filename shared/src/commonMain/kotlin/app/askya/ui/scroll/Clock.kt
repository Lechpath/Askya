package app.askya.ui.scroll

/**
 * Время звука словами: «0:07», «1:24», «12:03».
 *
 * Часов не бывает: сорок минут — потолок записи (см.
 * `VoiceRecorder` телефона), и разряд, который никогда не
 * заполняется, только сдвигает столбец.
 */
fun formatClock(milliseconds: Long): String {
    val total = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
