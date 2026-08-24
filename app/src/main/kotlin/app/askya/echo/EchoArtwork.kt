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
        context.contentResolver.openInputStream(EchoLibrary.coverUri(albumId))?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options())
        }
    }.getOrNull()
}

private fun fromTag(context: Context, uri: String): Bitmap? = runCatching {
    val reader = MediaMetadataRetriever()
    try {
        reader.setDataSource(context, Uri.parse(uri))
        reader.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options())
        }
    } finally {
        reader.release()
    }
}.getOrNull()

/** Половинное уменьшение: обложке на экране больше 600 точек не нужно. */
private fun options() = BitmapFactory.Options().apply { inSampleSize = 2 }
