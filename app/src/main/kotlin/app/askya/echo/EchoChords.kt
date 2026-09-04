package app.askya.echo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Аккорд: основание и лад.
 *
 * Основание — число от нуля до одиннадцати, где ноль — до: полутонами, а не
 * буквами, потому что весь разбор — это поворот одного и того же вектора на
 * двенадцать положений, и буква тут была бы лишним переводом туда и обратно.
 *
 * Ладов два, мажор и минор, и это не упрощение ради простоты. Септаккорды,
 * уменьшённые и с задержанием отличаются от трезвучия одной нотой, и на слух
 * машины эта одна нота теряется в обертонах соседних: разбор, который
 * предлагает выбрать между Am и Am7, ошибается чаще, чем помогает. Две
 * дюжины ответов — это то, что можно назвать уверенно и сыграть на чём угодно.
 */
data class EchoChord(val root: Int, val minor: Boolean) {

    /** Как его пишут в песеннике: `Am`, `F`, `C#m`. */
    val name: String get() = NOTE_NAMES[root] + if (minor) "m" else ""

    /** Ноты, из которых он сложен, — основание, терция, квинта. */
    val notes: List<Int> get() = listOf(root, (root + if (minor) 3 else 4) % 12, (root + 7) % 12)
}

/** Сколько времени звучит один аккорд. */
data class EchoChordSpan(val fromMs: Long, val toMs: Long, val chord: EchoChord)

/**
 * Разобранная запись.
 *
 * [chords] — те же аккорды, что и в [spans], но каждый по разу и в порядке
 * того, сколько он звучит: аппликатуры показываются по этому списку, и первым
 * человек должен увидеть тот, который придётся брать чаще всего.
 */
data class EchoChordScore(
    val key: String,
    val spans: List<EchoChordSpan>,
    val chords: List<EchoChord>,
    /** Дослушана ли запись до конца или разбор упёрся в свой предел. */
    val whole: Boolean,
)

/**
 * Разбор аккордов: что играть, если хочется сыграть это самому.
 *
 * ## Зачем это в плеере
 *
 * Песню, которая понравилась, хотят повторить руками — на гитаре, на укулеле,
 * на клавишах. Обычный путь за этим — чужой сайт с подбором, где та же песня
 * лежит в пяти вариантах, половина из них неверна, и все с рекламой. Askya в
 * сеть за этим не ходит и не может: файл уже лежит на телефоне, и всё, что
 * нужно, чтобы узнать аккорд, — в нём самом.
 *
 * ## Как это устроено
 *
 * Звук распаковывается ([EchoStudio] распаковывает его так же, ради нарезки),
 * сводится в один канал и прореживается до 11025 герц: аккорды живут ниже
 * тысячи герц, и держать ради них сорок четыре тысячи отсчётов в секунду
 * значит считать вчетверо дольше ради того же ответа. Прореживание идёт
 * усреднением, а не выбрасыванием отсчётов: выброшенное возвращается свистом
 * на чужих частотах и портит именно то, что здесь считают.
 *
 * Дальше запись режется на куски по 4096 отсчётов — это треть секунды, — и в
 * каждом меряется сила сорока восьми нот от до второй октавы до си пятой.
 * Меряется по Гёрцелю, а не разложением Фурье: нужных частот сорок восемь, а
 * полное разложение дало бы две тысячи, из которых сорок восемь и пришлось бы
 * выбирать. Свой Гёрцель — это двадцать строк, а своё БПФ — двести.
 *
 * Сорок восемь сил складываются в двенадцать по названию ноты: до второй
 * октавы и до четвёртой — это одно и то же до, и аккорд не различает, каким из
 * них его взяли. Двенадцать чисел — это и есть хроматический отпечаток куска,
 * и аккорд выбирается тем из двадцати четырёх, чей отпечаток на него похож.
 *
 * ## Почему ответ не всегда верен
 *
 * Отпечаток слышит всё разом: голос, барабаны, обертоны баса. На плотной
 * записи трезвучие тонет, и разбор путает родственные аккорды — Am с C, F с
 * Dm: у них по две общих ноты из трёх. Поэтому ответ подписан как догадка, а
 * не как истина, и рядом с ним стоит время: место в песне, куда можно перейти
 * и послушать самому.
 *
 * ## Почему кусок не называется сам по себе
 *
 * Разбор, спрашивавший каждый кусок отдельно, выдавал на песню в три аккорда
 * два десятка имён: треть секунды — это меньше, чем длится один удар по
 * струнам, и в неё попадает то бас с проходящей нотой, то голос без гитары.
 * Каждый такой кусок честно называл ближайшее к себе трезвучие, и песенник
 * складывался из букв, которых в песне не было.
 *
 * Поэтому куски называются не поодиночке, а все разом, и в счёте участвуют
 * три вещи помимо самого звука:
 *
 * — **соседи по времени.** Отпечаток куска усредняется с соседними
 *   ([smooth]): аккорд длиннее секунды, и мерить его третью секунды — то же,
 *   что мерить рулеткой в трясущейся руке;
 * — **цена смены.** Остаться на прежнем аккорде ничего не стоит, сменить —
 *   стоит ([SWITCH]). Новый должен не просто выиграть у прежнего, а выиграть
 *   заметно. Это и есть разница между «здесь другой аккорд» и «здесь на
 *   мгновение стало похоже на другой»;
 * — **тональность.** Она считается по всей записи разом и до разбора, и
 *   аккорды своей тональности получают небольшую фору ([FAMILY]). Не запрет:
 *   чужой аккорд возьмёт своё, если он там и правда есть, — а именно фора,
 *   потому что на границе между Am и C в песне без диезов вернее Am.
 *
 * Считается это сразу по всей песне, а не по ходу ([decode]): выбор в
 * середине зависит и от того, что было, и от того, что будет, а проход слева
 * направо застревал бы на первой же случайности.
 *
 * Оставшееся дрожание снимается голосованием соседей ([settle]): кусок, у
 * которого слева и справа стоит одно и то же, а сам он говорит иное, — это
 * ошибка одного куска, а не аккорд длиной в треть секунды.
 */
object EchoChords {

    /**
     * Разобрать запись.
     *
     * `null` — файл не прочитался или в нём не нашлось ничего похожего на
     * музыку: тишина, речь, шум. Пустой разбор показывать нечестнее, чем
     * сказать «не вышло».
     *
     * [onStep] зовётся долей прочитанного — от нуля до единицы. Разбор идёт
     * секундами, и молчащий экран на это время читался бы как зависание.
     */
    suspend fun read(
        context: Context,
        track: Track,
        onStep: (Float) -> Unit = {},
    ): EchoChordScore? = withContext(Dispatchers.Default) {
        val frames = mutableListOf<FloatArray>()
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return@withContext null

        val whole = listen(
            context = context,
            uri = uri,
            durationMs = track.durationMs,
            onFrame = { frames += it },
            onStep = onStep,
        ) ?: return@withContext null

        if (frames.size < ENOUGH) return@withContext null
        score(frames, whole)
    }

    /**
     * Из отпечатков — в разбор.
     *
     * Отделено от чтения файла нарочно: здесь нет ни одного обращения к
     * системе, и потому вся эта половина проверяется обычными тестами.
     */
    internal fun score(frames: List<FloatArray>, whole: Boolean): EchoChordScore? {
        if (frames.isEmpty()) return null

        // Тональность считается по всей записи и до разбора: она нужна самому
        // разбору как фора своим аккордам, а не только подписью сверху.
        val key = keyChordOf(average(frames))
        val heard = decode(smooth(frames), key)
        val steady = settle(heard)
        val spans = spansOf(steady)
        if (spans.isEmpty()) return null

        // Порядок аппликатур — по звучащему времени, а не по первому
        // появлению: чаще всего берут тот аккорд, который дольше всего звучит,
        // и он должен стоять первым.
        val held = spans.groupBy { it.chord }
            .mapValues { (_, list) -> list.sumOf { it.toMs - it.fromMs } }
        val chords = held.entries.sortedByDescending { it.value }.map { it.key }

        return EchoChordScore(
            key = key.name,
            spans = spans,
            chords = chords,
            whole = whole,
        )
    }

    /**
     * Усреднить отпечаток с соседними по времени.
     *
     * Окно в три куска — это чуть больше секунды, и это самая короткая мерка,
     * которой имеет смысл мерить аккорд: короче него не бывает и того, что в
     * песеннике пишут отдельной буквой. Шире брать нельзя — настоящая смена
     * аккорда размазалась бы на секунду в обе стороны, и разбор врал бы ровно
     * там, где он и нужен точным: на границе.
     *
     * Края берут, сколько есть: первый кусок усредняется с одним соседом, а не
     * дополняется тишиной. Дополненный тишиной, он вышел бы вдвое тише
     * остальных и потерял бы аккорд там, где песня как раз начинается.
     */
    internal fun smooth(frames: List<FloatArray>): List<FloatArray> {
        if (frames.size < 3) return frames
        return List(frames.size) { at ->
            val from = max(0, at - SMOOTH)
            val to = min(frames.size - 1, at + SMOOTH)
            val mixed = FloatArray(12)
            for (which in from..to) {
                val frame = frames[which]
                for (note in 0 until 12) mixed[note] += frame[note]
            }
            val count = (to - from + 1).toFloat()
            for (note in 0 until 12) mixed[note] /= count
            mixed
        }
    }

    /**
     * Назвать все куски разом.
     *
     * Счёт по двадцати пяти состояниям — двадцать четыре аккорда и «никакого»:
     * для каждого куска считается лучшая цена прийти в него каждым из них, а в
     * конце лучший путь читается назад. Тот же счёт исправляет опечатки и
     * разбирает речь, и нужен он здесь ровно за тем же — чтобы ответ был
     * связной цепочкой, а не двумя тысячами независимых догадок.
     *
     * Цена куска складывается из трёх слагаемых: согласие отпечатка с
     * трезвучием, фора своим по тональности ([FAMILY]) и штраф за смену
     * ([SWITCH]). «Никакого» стоит ровно [MATCH_FLOOR]: кусок остаётся
     * безымянным, когда ни одно трезвучие не согласилось с ним сильнее этого.
     *
     * Перебирать все переходы не нужно: цена перехода одна и та же для всех
     * пар, кроме «остался на месте». Значит, в состояние приходят либо из него
     * самого, либо из лучшего на прошлом шаге, и весь шаг — это два сравнения
     * на состояние вместо двадцати пяти.
     */
    internal fun decode(frames: List<FloatArray>, key: EchoChord): List<EchoChord?> {
        if (frames.isEmpty()) return emptyList()

        val family = family(key)
        // Двадцать пятое состояние — «никакого аккорда»; оно же последнее.
        val silence = CHORDS.size

        fun sing(chroma: FloatArray, state: Int): Float =
            if (state == silence) {
                MATCH_FLOOR
            } else {
                val chord = CHORDS[state]
                agreement(chroma, chord) + if (chord in family) FAMILY else 0f
            }

        val here = FloatArray(silence + 1)
        val next = FloatArray(silence + 1)
        // Откуда пришли: `true` — остались на месте, `false` — из лучшего.
        val stayed = Array(frames.size) { BooleanArray(silence + 1) }
        val bests = IntArray(frames.size)

        for (state in 0..silence) here[state] = sing(frames.first(), state)
        bests[0] = (0..silence).maxBy { here[it] }

        for (at in 1 until frames.size) {
            val chroma = frames[at]
            val best = here[bests[at - 1]]
            for (state in 0..silence) {
                val stay = here[state]
                val came = best - SWITCH
                stayed[at][state] = stay >= came
                next[state] = max(stay, came) + sing(chroma, state)
            }
            for (state in 0..silence) here[state] = next[state]
            bests[at] = (0..silence).maxBy { here[it] }
        }

        val path = arrayOfNulls<EchoChord>(frames.size)
        var state = bests.last()
        for (at in frames.indices.reversed()) {
            path[at] = CHORDS.getOrNull(state)
            if (at > 0 && !stayed[at][state]) state = bests[at - 1]
        }
        return path.toList()
    }

    /**
     * Аккорды тональности — те, которыми в ней и играют.
     *
     * Шесть трезвучий вместо семи: седьмая ступень уменьшённая, а уменьшённых
     * в разборе нет вовсе. У минора их семь — гармоническая пятая ступень
     * (мажорная) стоит рядом с натуральной, потому что в песнях берут обе, и
     * фора, доставшаяся одной из них, врала бы про половину припевов.
     */
    private fun family(key: EchoChord): Set<EchoChord> {
        fun step(semitones: Int, minor: Boolean) = EchoChord((key.root + semitones) % 12, minor)
        return if (!key.minor) {
            setOf(
                step(0, false), step(2, true), step(4, true),
                step(5, false), step(7, false), step(9, true),
            )
        } else {
            setOf(
                step(0, true), step(3, false), step(5, true),
                step(7, true), step(7, false), step(8, false), step(10, false),
            )
        }
    }

    /**
     * Какой аккорд ближе всего к этому отпечатку.
     *
     * Сравнение — согласие двух наборов, посчитанное от их же средних: у
     * отпечатка есть общий уровень, который есть у всех двенадцати нот
     * поровну, и он одинаково хорошо «подходит» к любому аккорду. Вычитание
     * среднего его и убирает — остаётся только то, чем этот кусок отличается
     * от ровного шума.
     *
     * `null` — согласие слабее порога: тишина, шум, речь или та плотная каша,
     * где трезвучия просто нет. Назвать такой кусок аккордом значило бы
     * поставить в песенник наугад взятую букву.
     */
    internal fun chordOf(chroma: FloatArray): EchoChord? {
        val loudest = chroma.max()
        if (loudest <= 0f) return null

        var best: EchoChord? = null
        var bestFit = MATCH_FLOOR

        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val fit = agreement(chroma, EchoChord(root, minor))
                if (fit > bestFit) {
                    bestFit = fit
                    best = EchoChord(root, minor)
                }
            }
        }
        return best
    }

    /** Насколько отпечаток похож на трезвучие: от −1 до 1. */
    private fun agreement(chroma: FloatArray, chord: EchoChord): Float {
        val notes = chord.notes
        // Среднее трезвучия: три ноты из двенадцати.
        val templateMean = 3f / 12f
        var mean = 0f
        for (value in chroma) mean += value
        mean /= 12f

        var top = 0f
        var left = 0f
        var right = 0f
        for (note in 0 until 12) {
            val here = chroma[note] - mean
            val there = (if (note in notes) 1f else 0f) - templateMean
            top += here * there
            left += here * here
            right += there * there
        }
        val below = sqrt(left * right)
        return if (below <= 0f) 0f else top / below
    }

    /**
     * Убрать дрожание: кусок, окружённый согласными соседями, слушается их.
     *
     * Не медиана по трём и не сглаживание чисел: аккорд — это имя, а не
     * величина, и среднего между Am и F не бывает. Голосование соседей —
     * единственное, что здесь имеет смысл.
     */
    internal fun settle(heard: List<EchoChord?>): List<EchoChord?> {
        if (heard.size < 3) return heard
        val settled = heard.toMutableList()
        for (at in 1 until heard.size - 1) {
            val before = heard[at - 1]
            val after = heard[at + 1]
            if (before != null && before == after && heard[at] != before) settled[at] = before
        }
        return settled
    }

    /**
     * Куски подряд — в отрезки времени.
     *
     * Короткие отрезки не выбрасываются, а прирастают к соседнему: аккорд,
     * простоявший полсекунды, — это либо ошибка разбора, либо проходящая
     * нота, и в песеннике ему места нет, а вот дырке на его месте — тем более.
     *
     * Прирастают к предыдущему — кроме самого первого, которому предыдущего
     * нет: он прирастает к следующему. Выбрасывать его нельзя, потому что это
     * начало песни, и песенник, начинающийся с четвёртой секунды, врёт про то,
     * с чего песню начинают. Так же и безымянные куски — вступление, пауза,
     * проигрыш барабанами — не оставляют дырок: следующий названный отрезок
     * забирает их себе.
     */
    internal fun spansOf(steady: List<EchoChord?>): List<EchoChordSpan> {
        val spans = mutableListOf<EchoChordSpan>()

        var at = 0
        while (at < steady.size) {
            val chord = steady[at]
            var to = at
            while (to + 1 < steady.size && steady[to + 1] == chord) to++
            if (chord != null) {
                spans += EchoChordSpan(
                    fromMs = at.toLong() * FRAME_MS,
                    toMs = (to + 1).toLong() * FRAME_MS,
                    chord = chord,
                )
            }
            at = to + 1
        }

        val kept = mutableListOf<EchoChordSpan>()
        for (span in spans) {
            val last = kept.lastOrNull()
            val short = span.toMs - span.fromMs < SHORTEST_MS
            when {
                // Тот же, что перед ним, — просто продолжение, даже через
                // безымянную дырку между ними.
                last != null && last.chord == span.chord -> {
                    kept[kept.lastIndex] = last.copy(toMs = span.toMs)
                }

                short && last != null -> kept[kept.lastIndex] = last.copy(toMs = span.toMs)
                else -> kept += span
            }
        }

        // Первый — назад отдать некому, и он отдаёт себя вперёд.
        if (kept.size > 1 && kept.first().toMs - kept.first().fromMs < SHORTEST_MS) {
            val second = kept[1]
            kept[1] = second.copy(fromMs = kept.first().fromMs)
            kept.removeAt(0)
        }
        return kept
    }

    /** Средний отпечаток всей записи — по нему ищется тональность. */
    internal fun average(frames: List<FloatArray>): FloatArray {
        val sum = FloatArray(12)
        for (frame in frames) {
            for (note in 0 until 12) sum[note] += frame[note]
        }
        val top = sum.max()
        if (top > 0f) {
            for (note in 0 until 12) sum[note] /= top
        }
        return sum
    }

    /**
     * Тональность — по профилям Крумхансл.
     *
     * Это не выдумка Askya, а известная мерка: в каждой тональности ноты
     * звучат разное время, и доли эти на всей европейской музыке одни и те же.
     * Средний отпечаток записи сравнивается с двадцатью четырьмя повёрнутыми
     * профилями, и лучший даёт имя — «Am», «C».
     *
     * Тональность сказана не ради учёности: по ней видно, какие аккорды в
     * песне ждать, и она первой объясняет, почему подбор предложил именно эти.
     */
    internal fun keyOf(chroma: FloatArray): String = keyChordOf(chroma).name

    /**
     * Она же тоникой — тем аккордом, которым тональность и называют.
     *
     * Отдельно от [keyOf] потому, что разбору нужна не подпись, а сам аккорд:
     * по нему собирается семья ступеней, получающая фору (см. [decode]).
     */
    internal fun keyChordOf(chroma: FloatArray): EchoChord {
        var best = EchoChord(0, minor = false)
        var bestFit = -2f
        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val profile = if (minor) MINOR_KEY else MAJOR_KEY
                val fit = correlate(chroma, profile, root)
                if (fit > bestFit) {
                    bestFit = fit
                    best = EchoChord(root, minor)
                }
            }
        }
        return best
    }

    private fun correlate(chroma: FloatArray, profile: FloatArray, shift: Int): Float {
        var mean = 0f
        var profileMean = 0f
        for (note in 0 until 12) {
            mean += chroma[note]
            profileMean += profile[note]
        }
        mean /= 12f
        profileMean /= 12f

        var top = 0f
        var left = 0f
        var right = 0f
        for (note in 0 until 12) {
            val here = chroma[note] - mean
            val there = profile[(note - shift + 12) % 12] - profileMean
            top += here * there
            left += here * here
            right += there * there
        }
        val below = sqrt(left * right)
        return if (below <= 0f) 0f else top / below
    }

    /**
     * Прочитать файл и отдать отпечаток каждого куска.
     *
     * `null` — файл не открылся или звука в нём нет. `false` — прочитано не
     * всё: у разбора есть предел ([LIMIT_MS]), потому что часовая запись
     * считалась бы минутами, а аккорды в ней всё равно смотрят по первым
     * минутам.
     */
    private fun listen(
        context: Context,
        uri: Uri,
        durationMs: Long,
        onFrame: (FloatArray) -> Unit,
        onStep: (Float) -> Unit,
    ): Boolean? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        val piece = FloatArray(FRAME)
        var filled = 0
        var carry = 0.0
        var sum = 0.0
        var count = 0
        // Последний отданный отсчёт: он нужен, когда новых отсчётов на один
        // выходной приходится меньше одного, — так бывает у записей с частотой
        // ниже той, к которой всё приводится.
        var last = 0.0
        var whole = true

        try {
            extractor.setDataSource(context, uri, null)
            val at = audioTrackOf(extractor) ?: return null
            extractor.selectTrack(at)
            val format = extractor.getTrackFormat(at)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            // Из файла брать больше нечего — и декодеру об этом уже сказано.
            // Два состояния, а не одно: конец файла и конец потока для
            // декодера — разные события, и между ними у него внутри лежит ещё
            // несколько кадров. Один и тот же буфер нельзя отдать дважды, и
            // «кончилось» приходится досказывать следующим свободным.
            var read = false
            var told = false
            var done = false
            var silent = 0
            var heard = 0L

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
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            if (!extractor.advance()) read = true
                        }
                    }
                }

                val out = decoder.dequeueOutputBuffer(info, WAIT_US)
                when {
                    out >= 0 -> {
                        silent = 0
                        val buffer = decoder.getOutputBuffer(out)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shape = decoder.outputFormat
                            val rate = shape.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val channels = shape
                                .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                .coerceAtLeast(1)
                            val step = rate.toDouble() / RATE

                            val samples = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var left = samples.remaining()
                            while (left >= channels) {
                                var mixed = 0
                                for (channel in 0 until channels) mixed += samples.get().toInt()
                                left -= channels

                                // Прореживание усреднением: все исходные
                                // отсчёты, попавшие в один новый, складываются
                                // и делятся. Это и есть тот самый грубый
                                // фильтр, без которого высокие частоты
                                // возвращаются свистом на низких.
                                sum += mixed.toDouble() / channels
                                count++
                                carry += 1.0
                                while (carry >= step) {
                                    carry -= step
                                    val value = if (count > 0) sum / count else last
                                    last = value
                                    piece[filled++] = (value / Short.MAX_VALUE).toFloat()
                                    sum = 0.0
                                    count = 0
                                    if (filled == FRAME) {
                                        onFrame(chromaOf(piece))
                                        filled = 0
                                    }
                                }
                            }
                        }

                        heard = max(heard, info.presentationTimeUs / 1_000)
                        if (durationMs > 0) onStep(min(1f, heard.toFloat() / durationMs))
                        if (heard > LIMIT_MS) {
                            whole = false
                            done = true
                        }
                        decoder.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    }

                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> silent = 0

                    else -> {
                        silent++
                        if (told && silent > PATIENCE) done = true
                    }
                }
            }
        } catch (failure: Exception) {
            // Прочитанного до поломки может хватить: разбор половины песни —
            // это половина песенника, а не пустой лист.
            return null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }

        return whole
    }

    /**
     * Отпечаток куска: двенадцать чисел, по одному на название ноты.
     *
     * Силы сорока восьми нот меряются по Гёрцелю и складываются по названию:
     * до второй октавы и до четвёртой — одно и то же до.
     *
     * Сила берётся не как есть, а логарифмом: ухо слышит громкость именно так,
     * и без этого один громкий бас перевешивал бы всё трезвучие целиком.
     */
    private fun chromaOf(piece: FloatArray): FloatArray {
        val chroma = FloatArray(12)

        for (note in NOTES.indices) {
            val coefficient = NOTES[note]
            var first = 0.0
            var second = 0.0
            for (at in 0 until FRAME) {
                val now = piece[at] * WINDOW[at] + coefficient * first - second
                second = first
                first = now
            }
            val power = first * first + second * second - coefficient * first * second
            chroma[note % 12] += ln(1.0 + LOUDNESS * sqrt(abs(power))).toFloat()
        }

        val top = chroma.max()
        if (top > 0f) {
            for (note in 0 until 12) chroma[note] /= top
        }
        return chroma
    }

    /** Номер звуковой дорожки в файле; `null` — звука в нём нет. */
    private fun audioTrackOf(extractor: MediaExtractor): Int? {
        for (at in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(at).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return at
        }
        return null
    }

    /** До какой частоты прореживается звук: аккорды живут много ниже. */
    private const val RATE = 11_025

    /** Кусок, в котором меряется один отпечаток, — треть секунды. */
    private const val FRAME = 4096

    /** Он же во времени: по нему считается место аккорда в песне. */
    internal const val FRAME_MS = FRAME * 1000L / RATE

    /**
     * Короче этого аккорд не показывается — прирастает к предыдущему.
     *
     * Секунда с четвертью, а не семьсот миллисекунд: под гитару поют примерно
     * в сто ударов в минуту, и аккорд там держат такт — две с лишним секунды.
     * Всё, что вдвое короче этого, — не смена аккорда, а то, что успело
     * прозвучать между двумя сменами.
     */
    private const val SHORTEST_MS = 1_250L

    /**
     * Сколько соседей с каждой стороны попадает в усреднение отпечатка.
     *
     * Один — это окно в три куска, чуть больше секунды: см. [smooth].
     */
    private const val SMOOTH = 1

    /**
     * Чего стоит сменить аккорд.
     *
     * Согласие с трезвучием лежит между нулём и единицей, и на плотной записи
     * верный аккорд обычно набирает 0,5–0,8, а родственный ему — на десятую
     * меньше. Двадцать две сотых — это порог, который родственнику не взять
     * случайно, а настоящей смене хватает с запасом: там разница уходит за
     * треть.
     *
     * Ноль вернул бы прежний разбор — двадцать аккордов на песню в три.
     * Единица дала бы один аккорд на всю запись: сменить не окупалось бы
     * никогда.
     */
    private const val SWITCH = 0.22f

    /**
     * Фора аккордам своей тональности.
     *
     * Мала нарочно: это подсказка, а не правило. Песня с одним чужим аккордом
     * в припеве — обычное дело, и фора в шесть сотых его не перебьёт; а вот
     * выбор между Am и C, где согласие почти поровну, она решает в пользу
     * того, который в этой тональности и играют.
     */
    private const val FAMILY = 0.06f

    /** Меньше этого числа кусков — это не песня, а обрывок. */
    private const val ENOUGH = 8

    /** Дальше этого места запись не слушается: час считался бы минутами. */
    private const val LIMIT_MS = 12 * 60 * 1000L

    /** Слабее этого согласия кусок остаётся безымянным. */
    private const val MATCH_FLOOR = 0.35f

    /** Насколько сжимается громкость перед сложением. */
    private const val LOUDNESS = 60.0

    private const val WAIT_US = 10_000L
    private const val PATIENCE = 400

    /**
     * Коэффициенты Гёрцеля для сорока восьми нот от до второй октавы.
     *
     * Четыре октавы, а не всё слышимое: ниже до второй лежит бас, чьи обертоны
     * и так попадают сюда, а выше си пятой — свист тарелок, у которого высоты
     * нет вовсе.
     */
    private val NOTES = DoubleArray(48) { at ->
        val hertz = 65.406 * Math.pow(2.0, at / 12.0)
        2.0 * cos(2.0 * PI * hertz / RATE)
    }

    /** Окно Ханна: без него края куска дают ложные частоты. */
    private val WINDOW = FloatArray(FRAME) { at ->
        (0.5 - 0.5 * cos(2.0 * PI * at / (FRAME - 1))).toFloat()
    }

    /**
     * Все двадцать четыре — в том порядке, в каком их перебирает [decode].
     *
     * Списком, а не двумя вложенными обходами: разбор ходит по ним тысячи раз,
     * и заводить двадцать четыре одинаковых объекта на каждый кусок незачем.
     */
    private val CHORDS: List<EchoChord> = buildList {
        for (root in 0 until 12) {
            add(EchoChord(root, minor = false))
            add(EchoChord(root, minor = true))
        }
    }

    private val MAJOR_KEY = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
    )

    private val MINOR_KEY = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
    )
}

/**
 * Названия нот — те, которыми подписаны аккорды в песенниках.
 *
 * Диезы и бемоли вперемешку, и это не небрежность: `Bb` и `Eb` пишут бемолем
 * всегда, а `F#` и `C#` — диезом, и человек, читающий подбор, ищет глазами
 * именно эти написания. Единообразие («все диезы») было бы правильно
 * теоретически и непривычно на бумаге.
 */
internal val NOTE_NAMES = listOf(
    "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B",
)
