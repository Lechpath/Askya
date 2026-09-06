package app.askya.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** Чем кончилась правка. */
sealed interface EditResult {

    /** Готовый файл: ссылка и то, как он назван в папке. */
    data class Done(val uri: String, val name: String) : EditResult

    /** Не вышло. [reason] — человеку, а не в лог. */
    data class Failed(val reason: String) : EditResult
}

/**
 * Правка видео: вырезать кусок, повернуть, убрать звук, вынуть звук, снять
 * кадр.
 *
 * ## Почему это не перекодирование
 *
 * Кусок вырезается **пересборкой контейнера, а не пережатием**: кадры
 * переписываются в новый файл как есть, байт в байт. Отсюда всё, что этот
 * редактор умеет и чего не умеет.
 *
 * Умеет — мгновенно и без потери качества: часовой фильм режется за секунды,
 * и картинка в куске ровно та же, что была. Пережатие тех же секунд на
 * телефоне заняло бы десятки минут и ухудшило бы картинку — а ради обрезки
 * этого никто не просил.
 *
 * Не умеет — того, для чего кадры пришлось бы рисовать заново: наложить
 * надпись, склеить два фильма с разными кодеками, изменить размер кадра,
 * запечь скорость в файл. Скорость есть у плеера, и там ей место.
 *
 * ## Чем режется
 *
 * `MediaExtractor` и `MediaMuxer` — системные, не libVLC. Плеер и редактор
 * здесь разного происхождения намеренно: VLC умеет открыть почти всё, но
 * пересобрать файл его Android-обёртка не даёт вовсе, а системная пара это
 * умеет. Плата — редактор знает меньше форматов, чем плеер: разбирается mp4,
 * mkv, webm, 3gp, а собирается только mp4. То, что открылось в плеере, но не
 * режется, честно говорит об этом, а не молчит.
 *
 * ## Про место разреза
 *
 * Начало куска сдвигается назад до ближайшего опорного кадра. Иначе никак:
 * кадры между опорными хранятся как разница с предыдущими, и кусок,
 * начинающийся с такого кадра, — это несколько секунд цветной каши.
 * Расхождение обычно меньше двух секунд; точнее умеет только пережатие.
 */
object VideoEdits {

    /**
     * Вырезать кусок и записать его отдельным файлом.
     *
     * [fromMs]…[toMs] — границы куска; [toMs] = 0 означает «до конца».
     * [keepAudio] = false кладёт в файл одно изображение; [rotateBy] —
     * доворот на 90, 180 или 270 градусов поверх того, как файл повёрнут
     * сейчас.
     *
     * Поворот не трогает кадры: в mp4 угол — это пометка в заголовке, по
     * которой плеер разворачивает картинку сам. Поэтому поворот тоже
     * мгновенный и тоже без потери качества.
     */
    suspend fun cut(
        context: Context,
        store: VideoStore,
        source: String,
        title: String,
        fromMs: Long = 0,
        toMs: Long = 0,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotateBy: Int = 0,
    ): EditResult = withContext(Dispatchers.IO) {
        val audioOnly = keepAudio && !keepVideo
        val mime = if (audioOnly) "audio/mp4" else "video/mp4"
        val pending = store.create(title, mime)
            ?: return@withContext EditResult.Failed("Не удалось завести файл в папке Askya")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false

        try {
            extractor.setDataSource(context, Uri.parse(source), null)
            muxer = MediaMuxer(pending.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Дорожки исходника, разложенные по номерам в новом файле.
            val tracks = HashMap<Int, Int>()
            var buffer = 256 * 1024

            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val kind = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val wanted = when {
                    kind.startsWith("video/") -> keepVideo
                    kind.startsWith("audio/") -> keepAudio
                    // Субтитры и прочие дорожки в mp4 не кладутся: система
                    // умеет их писать не для всякого формата, а падать на
                    // обрезке из-за дорожки, которой человек не просил, нельзя.
                    else -> false
                }
                if (!wanted) continue

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    buffer = maxOf(buffer, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                extractor.selectTrack(index)
                tracks[index] = muxer.addTrack(format)
            }

            // Запас поверх заявленного: `max-input-size` разборщик заполняет не
            // всегда и не всегда честно, а кадр, не влезший в буфер, — это
            // исключение посреди часового фильма, а не пропущенный кадр.
            buffer = maxOf(buffer, MIN_BUFFER)

            if (tracks.isEmpty()) {
                pending.cancel()
                return@withContext EditResult.Failed(
                    if (audioOnly) "В этом файле нет звуковой дорожки"
                    else "В этом файле нечего вырезать",
                )
            }

            // Угол — только у изображения. У файла с одной звуковой дорожкой
            // поворачивать нечего, а поворот, записанный в звук, часть
            // разборщиков считает испорченным заголовком.
            if (!audioOnly) muxer.setOrientationHint(orientationOf(context, source, rotateBy))
            muxer.start()
            started = true

            val fromUs = fromMs * 1000
            val toUs = if (toMs > 0) toMs * 1000 else Long.MAX_VALUE
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var bytes = ByteBuffer.allocate(buffer)
            val info = MediaCodec.BufferInfo()
            // Кусок должен начинаться с нуля, а не с того места, где он стоял
            // в исходнике: иначе плеер честно покажет пустоту до первой минуты.
            //
            // Начало общее для всех дорожек, а не своё у каждой: разведи их по
            // отдельным нулям — и звук уедет от изображения ровно на разницу
            // между их первыми кадрами.
            var origin = -1L
            var written = 0

            // Дорожки, дошедшие до конца куска. Прежде здесь стоял общий
            // `break`, и он обрывал обрезку целиком: звук в файле лежит
            // впереди изображения, до своего конца доходит первым — и
            // вырезанный кусок кончался на середине картинки.
            val ended = HashSet<Int>()

            while (true) {
                val index = extractor.sampleTrackIndex
                if (index < 0) break

                val target = tracks[index]
                if (target == null) {
                    if (!extractor.advance()) break
                    continue
                }

                val time = extractor.sampleTime
                if (time > toUs) {
                    ended += index
                    if (ended.size >= tracks.size) break
                    if (!extractor.advance()) break
                    continue
                }

                info.offset = 0
                info.size = runCatching { extractor.readSampleData(bytes, 0) }.getOrElse {
                    // Кадр не влез: буфер удваивается, и тот же кадр читается
                    // заново. Расти он может дважды — дальше дело не в размере.
                    if (bytes.capacity() >= MAX_BUFFER) throw it
                    bytes = ByteBuffer.allocate(bytes.capacity() * 2)
                    extractor.readSampleData(bytes, 0)
                }
                if (info.size < 0) break

                if (time >= 0) {
                    if (origin < 0) origin = time
                    info.presentationTimeUs = maxOf(0, time - origin)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(target, bytes, info)
                    written++
                }
                if (!extractor.advance()) break
            }

            if (written == 0) {
                pending.cancel()
                return@withContext EditResult.Failed("В выбранных границах ничего нет")
            }

            muxer.stop()
            started = false
            muxer.release()
            muxer = null

            val uri = pending.done()

            // Пустой файл — не «готово». Так кончается обрезка, когда место на
            // телефоне вышло посреди записи или когда система завела запись, но
            // писать в неё не дала: прежде о таком говорилось «Готово», а в
            // папке не появлялось ничего, и искать было нечего.
            if (pending.weight <= 0) {
                store.remove(uri)
                return@withContext EditResult.Failed(
                    "Файл получился пустым — скорее всего, кончилось место на телефоне",
                )
            }

            EditResult.Done(uri, pending.displayName)
        } catch (failure: Throwable) {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            pending.cancel()
            EditResult.Failed(reasonOf(failure))
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Звук фильма отдельным файлом — в ту же папку Askya. */
    suspend fun extractAudio(
        context: Context,
        store: VideoStore,
        source: String,
        title: String,
    ): EditResult = cut(
        context = context,
        store = store,
        source = source,
        title = "$title-звук",
        keepAudio = true,
        keepVideo = false,
    )

    /**
     * Кадр в этом месте.
     *
     * Вынимается системным `MediaMetadataRetriever`, а не плеером: снимок
     * кадра Android-обёртка libVLC наружу не отдаёт вовсе. Значит, кадр
     * снимается не со всякого файла, который плеер открывает, — `null`
     * означает ровно это, и говорить об этом надо человеку, а не в лог.
     *
     * Кадр берётся ближайший к месту, а не точный: точный требует разбора
     * всех кадров от опорного, и на четырёхкратном разрешении это секунды
     * ожидания посреди фильма.
     */
    suspend fun frame(context: Context, source: String, atMs: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, Uri.parse(source))
                retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.also { runCatching { retriever.release() } }.getOrNull()
        }

    /**
     * Сколько длится файл — спрошенное у самого файла, а не у списка.
     *
     * Нужно ножницам. Длину ролика раздел берёт из `MediaStore`, а тот знает
     * её не всегда: у скачанного потока, у `.ts` и у только что записанного
     * файла в списке стоит ноль. Ножницы, поверившие нулю, показывали пустую
     * полосу и не давали нажать «Обрезать» — вырезать из ролика длиной ноль
     * нечего. Поэтому длина, если её не знают, спрашивается у файла.
     *
     * Ноль в ответе означает, что её не знает и он: такой файл резать не по
     * чему, и об этом честнее сказать словом.
     */
    suspend fun duration(context: Context, source: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, Uri.parse(source))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        }.also { runCatching { retriever.release() } }.getOrDefault(0L)
    }

    /**
     * Угол готового файла: свой угол исходника плюс доворот.
     *
     * Свой спрашивается у системы, а не считается нулём: снятое телефоном
     * видео почти всегда лежит боком, и повёрнутым его делает ровно эта
     * пометка. Потеряв её, вертикальный ролик лёг бы набок.
     */
    private fun orientationOf(context: Context, source: String, rotateBy: Int): Int {
        val retriever = MediaMetadataRetriever()
        val own = runCatching {
            retriever.setDataSource(context, Uri.parse(source))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        }.also { runCatching { retriever.release() } }.getOrDefault(0)
        return ((own + rotateBy) % 360 + 360) % 360
    }

    /**
     * Почему не вышло — словами.
     *
     * Системные исключения тут говорят одно и то же на своём языке:
     * «IllegalArgumentException» из `addTrack` означает, что кодек исходника
     * в mp4 не кладётся, а исключение из `setDataSource` — что файл системным
     * разбором не открывается вовсе.
     */
    private fun reasonOf(failure: Throwable): String = when (failure) {
        is IllegalArgumentException ->
            "Такую дорожку нельзя положить в mp4 — файл открывается, но не режется"

        is java.io.IOException ->
            "Этот формат системный разбор не открывает: плеер его играет, а резать не может"

        else -> "Не получилось: ${failure.javaClass.simpleName}"
    }

    /** Меньше мегабайта кадр 4K не бывает; с этого и начинается буфер. */
    private const val MIN_BUFFER = 1024 * 1024

    /** Дальше двадцати мегабайт на кадр дело не в буфере, а в файле. */
    private const val MAX_BUFFER = 20 * 1024 * 1024
}
