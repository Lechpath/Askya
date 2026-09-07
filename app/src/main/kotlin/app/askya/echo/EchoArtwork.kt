package app.askya.echo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Обложка дорожки — картинкой, без Compose.
 *
 * Живёт рядом с плеером, а не в его экране: обложку показывает не только сам
 * плеер, но и уведомление в шторке, а оно рисуется системой и о композиции
 * ничего не знает.
 *
 * Сначала спрашивается у MediaStore (`albumart`) — там она уже разобрана и
 * лежит готовой картинкой. Не нашлось — читается из самого файла
 * `MediaMetadataRetriever`: у скачанных отдельными песнями обложка часто вшита
 * в тег, а альбома у них нет вовсе.
 *
 * `null` значит «обложки нет»: ошибки здесь ожидаемы — тег может быть пустым,
 * файл битым, а доступ отозванным, — и все они означают одно и то же.
 *
 * Последняя разобранная обложка помнится. Играющую дорожку спрашивают разом
 * несколько мест — плеер, полноэкранная карточка, вспышки вокруг обложки,
 * уведомление, — и без этого один и тот же jpeg разбирался бы по четыре раза
 * на каждой смене песни. Помнится ровно одна: обложек столько же, сколько
 * песен, а нужна из них та, что звучит.
 */
suspend fun loadArtwork(context: Context, albumId: Long, uri: String?): Bitmap? =
    withContext(Dispatchers.IO) {
        val key = "$albumId|${uri.orEmpty()}"
        synchronized(lock) { if (key == lastKey) return@withContext last }

        val bitmap = fromAlbumArt(context, albumId) ?: uri?.let { fromTag(context, it) }
        synchronized(lock) {
            lastKey = key
            last = bitmap
        }
        bitmap
    }

/** Последняя разобранная обложка и то, чья она. */
private val lock = Any()
private var lastKey: String? = null
private var last: Bitmap? = null

private fun fromAlbumArt(context: Context, albumId: Long): Bitmap? {
    if (albumId <= 0) return null
    return runCatching {
        val cover = EchoLibrary.coverUri(albumId)
        // Дважды открытый поток, а не один: размер читается первым проходом,
        // и отмотать поток документа назад нельзя — `markSupported` у него
        // ничего не обещает.
        val options = options(stream = { context.contentResolver.openInputStream(cover) })
        context.contentResolver.openInputStream(cover)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()
}

private fun fromTag(context: Context, uri: String): Bitmap? = runCatching {
    val reader = MediaMetadataRetriever()
    try {
        reader.setDataSource(context, Uri.parse(uri))
        reader.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options(bytes = bytes))
        }
    } finally {
        reader.release()
    }
}.getOrNull()

/**
 * Уменьшение по размеру самой картинки, а не вслепую вдвое.
 *
 * Обложки бывают какие угодно: у песни из магазина она 600 точек, у рипа с
 * диска — 3000. Половинное уменьшение первую оставляло приемлемой, а вторую
 * превращало в полтора мегапикселя — девять мегабайт в памяти на каждую
 * играющую песню, и эти же девять мегабайт уходили в уведомление и в
 * `MediaMetadata`. Система такие картинки ужимает сама, но не всегда молча: у
 * уведомления есть потолок на то, сколько оно весит, и переросшее его просто
 * не появляется в шторке.
 *
 * [LIMIT] точек по большей стороне хватает и карточке плеера на весь экран, и
 * обложке в центре управления. Читается размер сперва без самой картинки
 * (`inJustDecodeBounds`), потом подбирается степень двойки — единственный
 * множитель, который `BitmapFactory` умеет применять при чтении, не разбирая
 * файл целиком.
 */
private fun options(bytes: ByteArray? = null, stream: () -> java.io.InputStream? = { null }): BitmapFactory.Options {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (bytes != null) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    } else {
        stream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }

    var sample = 1
    val side = maxOf(bounds.outWidth, bounds.outHeight)
    while (side > 0 && side / sample > LIMIT) sample *= 2

    return BitmapFactory.Options().apply { inSampleSize = sample }
}

/** Больше этого обложке негде пригодиться: карточка плеера и есть экран. */
private const val LIMIT = 1024
