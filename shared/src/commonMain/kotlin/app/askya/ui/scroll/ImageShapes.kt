package app.askya.ui.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.askya.platform.LocalPlatformContext
import app.askya.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Каким боком лежит картинка: ширина, поделённая на высоту.
 *
 * Нужно лентой Scroll: миниатюра там повторяет формат снимка — у лежачего она
 * лежачая, у стоячего стоячая, — и высота каждой считается до того, как
 * миниатюры разложены по столбцам. Спрашивать формат у прочитанной картинки
 * нельзя: столбцы тогда пересобирались бы по мере того, как снимки дочитываются,
 * и лента прыгала бы под пальцем.
 *
 * Читаются только размеры, без единой точки: `inJustDecodeBounds` открывает
 * заголовок файла и на этом останавливается. Полный снимок с телефона — это
 * десятки мегабайт в памяти, а здесь нужны два числа.
 *
 * Разворот из EXIF учитывается — иначе снимок с камеры, лежащий боком, дал бы
 * лежачую миниатюру под стоячую картинку. Тем же перечнем метки, что и в
 * `Bitmap.upright`, только повёрнутыми оказываются не точки, а числа.
 */

/**
 * Уже прочитанные форматы — на весь запуск приложения.
 *
 * Формат картинки не меняется, пока её не переписали, а лента Scroll и раздел
 * «Галерея» спрашивают одни и те же снимки по многу раз за сеанс. Правка
 * картинки заводит новый файл и новую ссылку (см. `NoteRepository.replaceImage`),
 * поэтому устареть записанному здесь нечем.
 */
private val known = ConcurrentHashMap<String, Float>()

/**
 * Форматы перечисленных картинок. Чего ещё не читали — дочитывается в фоне и
 * возвращается одним разом: по одной картинке столбцы ленты переезжали бы
 * столько раз, сколько в ней снимков.
 */
@Composable
fun rememberImageAspects(uris: List<String>): Map<String, Float> {
    val context = LocalPlatformContext.current
    var shapes by remember { mutableStateOf<Map<String, Float>>(known.toMap()) }

    // Ключом — сами ссылки, а не список: у списка, собранного заново на каждой
    // перерисовке, тождество своё, и чтение заводилось бы без конца.
    val asked = uris.joinToString("\n")

    LaunchedEffect(asked) {
        val missing = uris.filterNot { known.containsKey(it) }
        if (missing.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                missing.forEach { uri -> aspectOf(context, uri)?.let { known[uri] = it } }
            }
        }
        shapes = known.toMap()
    }

    return shapes
}

/** Формат одного файла. `null` — не прочиталось: файл могли убрать. */
internal expect fun aspectOf(context: PlatformContext, uri: String): Float?
