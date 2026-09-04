package app.askya.echo

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Мастерская Echo: что можно сделать с самим файлом, а не со списком.
 *
 * Переименовать, перенести в другую папку, отрезать кусок, склеить несколько
 * подряд. Всё это — работа с музыкой на телефоне, и делают её обычно на
 * компьютере: скачал десять записей одним куском, режешь, подписываешь,
 * раскладываешь. Askya умеет то же самое там, где музыка и лежит, — и это
 * единственное место в приложении, где файл меняется, а не читается.
 *
 * ## Почему резать и склеивать приходится через перекодирование
 *
 * Кусок mp3 нельзя просто выпилить из середины и положить в новый файл:
 * `MediaMuxer` — единственный системный сборщик контейнеров — mp3 в свои
 * контейнеры не принимает. Поэтому звук распаковывается до потока чисел
 * (`MediaCodec`-декодер), режется и склеивается уже на нём и запаковывается
 * заново в AAC внутри `.m4a`. Это стоит секунд на песню и одного пересжатия
 * качества — того самого, из-за которого исходник **никогда не трогается**:
 * и обрезка, и склейка кладут рядом новый файл, а прежний остаётся на месте.
 *
 * Формат один и тот же на любой вход: `.m4a` (AAC) играет всё, начиная с
 * древних телефонов, и это то немногое, что `MediaMuxer` умеет собирать.
 *
 * ## Куда кладётся сделанное
 *
 * В `Music/Askya` — свою папку, а не в ту, откуда взят исходник. Нарезки и
 * склейки в одной куче с оригиналами превращают папку скачанного в свалку, где
 * не разобрать, что откуда; а увидев «Askya» в списке папок, человек сразу
 * знает, чьё это и что там лежит.
 *
 * ## Чего здесь нет
 *
 * Ни громкости, ни нормализации, ни затуханий на стыке. Всё это — обработка
 * звука, то есть отдельное ремесло; мастерская же делает ровно то, что человек
 * сделал бы ножницами: отрезала и склеила.
 */
object EchoStudio {

    /** Папка, в которую ложится всё сделанное здесь. */
    const val HOME = "Askya"

    /**
     * Переименовать файл: и подпись внутри библиотеки, и само имя на диске.
     *
     * Обе сразу, потому что порознь получается ложь: подпись в плеере одна,
     * имя в проводнике другое, и человек, который искал файл по названию, его
     * не находит. Расширение остаётся прежним — оно про формат, а не про имя.
     *
     * Право на запись чужого файла спрашивается снаружи (см. `rememberFileWriter`
     * в разметке): системное окно показывает человеку, что именно меняется, и
     * согласие даётся телефону, а не нам.
     */
    suspend fun rename(context: Context, track: Track, title: String): Boolean =
        withContext(Dispatchers.IO) {
            val name = title.trim()
            if (name.isEmpty()) return@withContext false

            val extension = nameOf(context, track).substringAfterLast('.', "")
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, name)
                put(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    if (extension.isEmpty()) name else "$name.$extension",
                )
            }

            // Отказ системы наружу не глушится: право на правку чужого файла
            // спрашивают у человека, а `false` вместо вопроса выглядел бы как
            // «не получилось», и второй попытки он бы не сделал.
            context.contentResolver.update(Uri.parse(track.uri), values, null, null) > 0
        }

    /**
     * Перенести файл в другую папку внутри `Music`.
     *
     * С Android 10 это одна строчка: у файла есть поле «где он лежит», и
     * система сама двигает его по диску. На системах постарше папки — это
     * настоящие каталоги, и файл приходится переносить руками, а потом
     * рассказывать об этом библиотеке, иначе он останется в ней по старому
     * адресу и перестанет играть.
     */
    suspend fun move(context: Context, track: Track, folder: String): Boolean =
        withContext(Dispatchers.IO) {
            val place = folder.trim().trim('/')
            if (place.isEmpty()) return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$place")
                }
                // Как и переименование, отказ отдаётся наверх — см. [rename].
                return@withContext context.contentResolver
                    .update(Uri.parse(track.uri), values, null, null) > 0
            }

            runCatching {
                val from = pathOf(context, track) ?: return@runCatching false
                val source = File(from)
                val home = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    place,
                )
                home.mkdirs()
                val target = File(home, source.name)
                if (!source.renameTo(target)) return@runCatching false

                @Suppress("DEPRECATION")
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DATA, target.absolutePath)
                }
                context.contentResolver.update(Uri.parse(track.uri), values, null, null)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
                true
            }.getOrDefault(false)
        }

    /**
     * Отрезать кусок: с [fromMs] по [toMs] — в новый файл под именем [title].
     *
     * Исходник остаётся нетронутым: обрезка — это то, что легко сделать не так,
     * и второй попытки не было бы, стирай она оригинал.
     */
    suspend fun trim(
        context: Context,
        track: Track,
        fromMs: Long,
        toMs: Long,
        title: String,
    ): Boolean = weld(
        context = context,
        pieces = listOf(Piece(Uri.parse(track.uri), fromMs * 1_000, toMs * 1_000)),
        title = title,
    )

    /**
     * Склеить несколько записей в одну, в том порядке, в каком их сложили.
     *
     * Порядок берётся списком, а не сортировкой: склеивают обычно то, что
     * порядком и осмысленно, — части одной записи, стороны пластинки, — и
     * алфавит тут не помощник.
     */
    suspend fun join(context: Context, tracks: List<Track>, title: String): Boolean = weld(
        context = context,
        pieces = tracks.map { Piece(Uri.parse(it.uri), 0, it.durationMs * 1_000) },
        title = title,
    )

    /** Кусок звука: откуда взять и какой отрезок, в микросекундах. */
    private data class Piece(val uri: Uri, val fromUs: Long, val toUs: Long)

    /**
     * Общая работа обрезки и склейки: распаковать, сшить, запаковать.
     *
     * Обе задачи — одна и та же, и разница между ними ровно в числе кусков:
     * обрезка — это склейка одного куска. Держать под них два похожих
     * перекодировщика значило бы завести две одинаково хрупких вещи вместо
     * одной.
     *
     * Первый кусок задаёт частоту и число каналов всей записи: у склеиваемых
     * файлов они бывают разными, и приводить их всё равно к чему-то одному
     * придётся — так пусть это будет то, с чего запись начинается.
     */
    private suspend fun weld(
        context: Context,
        pieces: List<Piece>,
        title: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val name = title.trim().ifEmpty { return@withContext false }
        if (pieces.isEmpty()) return@withContext false

        val shape = shapeOf(context, pieces.first().uri) ?: return@withContext false
        val target = create(context, name) ?: return@withContext false

        var done = false
        try {
            val muxer = target.muxer(context)
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                shape.rate,
                shape.channels,
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val welder = Welder(muxer, encoder, shape)
            try {
                for (piece in pieces) unpack(context, piece, shape, welder)
                welder.finish()
                done = true
            } finally {
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                runCatching { if (welder.opened) muxer.stop() }
                runCatching { muxer.release() }
            }
        } catch (failure: Exception) {
            done = false
        } finally {
            target.close(context, done)
        }

        done
    }

    /**
     * Частота и число каналов записи — то, во что будут приводиться все куски.
     *
     * Больше двух каналов сводится к двум: `MediaCodec` на телефонах кодирует в
     * AAC уверенно только моно и стерео, а пятиканальная дорожка в музыке с
     * телефона — редкость, ради которой не стоит рисковать всей работой.
     */
    private fun shapeOf(context: Context, uri: Uri): Shape? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val at = audioTrackOf(extractor) ?: return null
            val format = extractor.getTrackFormat(at)
            Shape(
                rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2),
            )
        } catch (failure: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Распаковать один кусок и отдать его сшивальщику числами.
     *
     * Три состояния вместо одного «кончилось», и это не педантизм: «в файле
     * больше нечего брать» и «декодер отдал всё, что держал» — разные события,
     * между которыми у декодера внутри лежит ещё несколько кадров. Слив их
     * воедино, склейка теряла бы полсекунды на каждом стыке.
     */
    private fun unpack(context: Context, piece: Piece, shape: Shape, welder: Welder) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(context, piece.uri, null)
            val at = audioTrackOf(extractor) ?: return
            extractor.selectTrack(at)
            val format = extractor.getTrackFormat(at)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return

            // Начало отрезка ищется по опорному кадру не позже нужного места:
            // распакованное до него всё равно отбрасывается, а вот начатый с
            // середины кадра звук даёт треск.
            if (piece.fromUs > 0) extractor.seekTo(piece.fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            // Из файла брать больше нечего.
            var read = false
            // И декодеру об этом уже сказано.
            var told = false
            var done = false
            // Сколько раз подряд декодер промолчал. Страховка от зависшего
            // кодека: без неё сломанный файл держал бы поток вечно.
            var silent = 0

            while (!done) {
                if (!told) {
                    val index = decoder.dequeueInputBuffer(WAIT_US)
                    if (index >= 0) {
                        val buffer = if (read) null else decoder.getInputBuffer(index)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            told = true
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(index, 0, size, time, 0)
                            // Дальше конца отрезка читать нечего: кормить
                            // декодер остатком песни ради того, чтобы потом
                            // его выбросить, — это минуты на длинных записях.
                            if (time > piece.toUs || !extractor.advance()) read = true
                        }
                    }
                }

                val out = decoder.dequeueOutputBuffer(info, WAIT_US)
                when {
                    out >= 0 -> {
                        silent = 0
                        val buffer = decoder.getOutputBuffer(out)
                        val here = info.presentationTimeUs
                        if (buffer != null && info.size > 0 && here >= piece.fromUs && here <= piece.toUs) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val heard = decoder.outputFormat
                            welder.take(
                                pcm = buffer,
                                from = Shape(
                                    rate = heard.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                    channels = heard.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                ),
                                into = shape,
                            )
                        }
                        decoder.releaseOutputBuffer(out, false)
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (ended || here > piece.toUs) done = true
                    }

                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> silent = 0

                    else -> {
                        silent++
                        if (told && silent > PATIENCE) done = true
                    }
                }
            }
        } catch (failure: Exception) {
            // Один кусок не прочитался — остальные всё равно склеиваются:
            // молча потерять песню плохо, но потерять и все прочие хуже.
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Номер звуковой дорожки в файле; `null` — звука в нём нет. */
    private fun audioTrackOf(extractor: MediaExtractor): Int? {
        for (at in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(at).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return at
        }
        return null
    }

    /** Во что приводится звук: частота и число каналов. */
    private data class Shape(val rate: Int, val channels: Int)

    /**
     * Сшивальщик: принимает распакованный звук и складывает его в файл.
     *
     * Работает потоком, а не через «сперва всё распакуем»: пять минут стерео в
     * числах — это полсотни мегабайт, а склеивают обычно не одну запись. В
     * памяти держится один буфер за раз.
     */
    private class Welder(
        private val muxer: MediaMuxer,
        private val encoder: MediaCodec,
        private val shape: Shape,
    ) {

        /** Открыт ли уже контейнер: закрывать неоткрытый нельзя. */
        var opened = false
            private set

        private var at = -1
        private var clock = 0L
        private val info = MediaCodec.BufferInfo()

        /** Кусок распакованного звука: привести к нужному виду и отдать дальше. */
        fun take(pcm: ByteBuffer, from: Shape, into: Shape) {
            val samples = pcm.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val heard = ShortArray(samples.remaining())
            samples.get(heard)
            feed(convert(heard, from, into))
        }

        /** Досказать кодировщику, что звук кончился, и дописать остаток. */
        fun finish() {
            val index = encoder.dequeueInputBuffer(WAIT_US * 10)
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, clock, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(last = true)
        }

        private fun feed(samples: ShortArray) {
            if (samples.isEmpty()) return
            var offset = 0

            while (offset < samples.size) {
                val index = encoder.dequeueInputBuffer(WAIT_US)
                if (index < 0) {
                    drain(last = false)
                    continue
                }

                val buffer = encoder.getInputBuffer(index) ?: return
                buffer.clear()
                val room = buffer.remaining() / 2
                val chunk = minOf(room, samples.size - offset)
                buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, offset, chunk)
                encoder.queueInputBuffer(index, 0, chunk * 2, clock, 0)

                // Время считается по числу отданных отсчётов, а не берётся у
                // исходника: у склейки исходников несколько, и их часы идут
                // каждый со своего нуля.
                clock += 1_000_000L * (chunk / shape.channels) / shape.rate
                offset += chunk
                drain(last = false)
            }
        }

        private fun drain(last: Boolean) {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, if (last) WAIT_US * 10 else 0)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!last) return

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!opened) {
                            at = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            opened = true
                        }
                    }

                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)
                        val service = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buffer != null && info.size > 0 && !service && opened) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(at, buffer, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    /**
     * Привести распакованный звук к общему виду: столько же каналов, та же
     * частота.
     *
     * Каналы сводятся полусуммой, а расходятся повтором — так делают все, и
     * ничего умнее для двух каналов не придумано. Частота пересчитывается по
     * прямой между соседними отсчётами: это самый простой способ, и на
     * переходе 44.1 → 48 кГц его слышно ровно настолько, насколько слышно
     * разницу между этими частотами вообще, то есть никак.
     *
     * Чаще всего приводить нечего: у склеенных подряд файлов с одного альбома
     * и частота, и каналы совпадают, и звук проходит насквозь.
     */
    private fun convert(samples: ShortArray, from: Shape, into: Shape): ShortArray {
        val mixed = when {
            from.channels == into.channels -> samples
            from.channels == 2 && into.channels == 1 -> ShortArray(samples.size / 2) { at ->
                ((samples[at * 2].toInt() + samples[at * 2 + 1].toInt()) / 2).toShort()
            }
            from.channels == 1 && into.channels == 2 -> ShortArray(samples.size * 2) { at ->
                samples[at / 2]
            }
            // Больше двух каналов: берутся первые два (или первый), остальное
            // отбрасывается. Сводить пять каналов по-настоящему — это другая
            // работа, и делать её наполовину хуже, чем не делать.
            else -> ShortArray(samples.size / from.channels * into.channels) { at ->
                val frame = at / into.channels
                val channel = at % into.channels
                samples.getOrElse(frame * from.channels + channel) { 0 }
            }
        }

        if (from.rate == into.rate) return mixed

        val frames = mixed.size / into.channels
        val wanted = (frames.toLong() * into.rate / from.rate).toInt()
        if (wanted <= 0) return ShortArray(0)

        val stretched = ShortArray(wanted * into.channels)
        for (frame in 0 until wanted) {
            val place = frame.toDouble() * from.rate / into.rate
            val left = place.toInt()
            val right = (left + 1).coerceAtMost(frames - 1)
            val share = place - left
            for (channel in 0 until into.channels) {
                val a = mixed.getOrElse(left * into.channels + channel) { 0 }.toDouble()
                val b = mixed.getOrElse(right * into.channels + channel) { 0 }.toDouble()
                stretched[frame * into.channels + channel] = (a + (b - a) * share).toInt().toShort()
            }
        }
        return stretched
    }

    /**
     * Куда писать: новая запись в библиотеке и открытый на запись файл за ней.
     *
     * С Android 10 файл заводится «незавершённым» (`IS_PENDING`): пока он не
     * дописан, его не видит ни один плеер, включая наш собственный, — иначе в
     * списке появлялась бы полупустая песня, которую можно включить.
     */
    private fun create(context: Context, name: String): Sheet? {
        val file = "$name.m4a"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file)
                put(MediaStore.Audio.Media.TITLE, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$HOME")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = runCatching {
                context.contentResolver.insert(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values,
                )
            }.getOrNull() ?: return null
            return Sheet(uri = uri, path = null)
        }

        val home = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            HOME,
        )
        if (!home.exists() && !home.mkdirs()) return null
        return Sheet(uri = null, path = File(home, file).absolutePath)
    }

    /** Место, в которое пишется сделанное: запись в библиотеке или путь. */
    private class Sheet(val uri: Uri?, val path: String?) {

        private var pipe: android.os.ParcelFileDescriptor? = null

        /**
         * Сборщик контейнера, пишущий в это место.
         *
         * До Android 10 у файла есть путь, и `MediaMuxer` берёт его сам. Выше
         * пути нет — есть запись в библиотеке, которую надо открыть на запись
         * и держать открытой всё время работы: закройся дескриптор раньше
         * времени, и файл останется обрубком.
         */
        fun muxer(context: Context): MediaMuxer = if (path != null) {
            MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            val opened = context.contentResolver.openFileDescriptor(uri!!, "rw")
                ?: error("файл не открылся на запись")
            pipe = opened
            MediaMuxer(opened.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        /**
         * Закрыть за собой: показать готовый файл библиотеке — или убрать
         * следы, если ничего не вышло. Недописанный файл, оставленный в
         * библиотеке, — это строка, которая молча не играет.
         */
        fun close(context: Context, done: Boolean) {
            runCatching { pipe?.close() }
            pipe = null

            val written = uri
            if (written != null) {
                if (done) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    runCatching { context.contentResolver.update(written, values, null, null) }
                } else {
                    runCatching { context.contentResolver.delete(written, null, null) }
                }
                return
            }

            val file = path ?: return
            if (done) {
                MediaScannerConnection.scanFile(context, arrayOf(file), null, null)
            } else {
                runCatching { File(file).delete() }
            }
        }
    }

    /** Имя файла на диске — из него берётся расширение при переименовании. */
    private fun nameOf(context: Context, track: Track): String = runCatching {
        context.contentResolver.query(
            Uri.parse(track.uri),
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    /** Путь к файлу — нужен только на системах до Android 10. */
    @Suppress("DEPRECATION")
    private fun pathOf(context: Context, track: Track): String? = runCatching {
        context.contentResolver.query(
            Uri.parse(track.uri),
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** Сколько ждать буфера кодировщика: доли миллисекунды, чтобы не крутиться зря. */
    private const val WAIT_US = 10_000L

    /** Плотность записи. 192 кбит — то, на чём AAC перестаёт быть слышен. */
    private const val BITRATE = 192_000

    /** Наибольший кусок, который кодировщик берёт за раз. */
    private const val CHUNK = 64 * 1024

    /**
     * Сколько пустых ответов декодера подряд считать поломкой.
     *
     * Каждый ответ ждёт [WAIT_US], то есть терпения хватает на несколько
     * секунд молчания. Исправный кодек молчит доли секунды; сломанный файл
     * молчит всегда, и без этого счёта работа не кончилась бы никогда.
     */
    private const val PATIENCE = 400
}
