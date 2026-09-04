package app.askya.echo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор аккордов — половина Android и половина чистого счёта.
 *
 * Проверяется вторая: распаковка звука требует телефона, а всё, что делается
 * с готовым отпечатком, — обычные числа, и ошибка в них тем и опасна, что на
 * экране выглядит как «подбор такой».
 */
class EchoChordsTest {

    /** Отпечаток, в котором звучат ровно эти ноты и больше ничего. */
    private fun chroma(vararg notes: Int): FloatArray {
        val values = FloatArray(12)
        for (note in notes) values[note] = 1f
        return values
    }

    @Test
    fun `чистое трезвучие узнаётся своим именем`() {
        assertEquals(EchoChord(0, minor = false), EchoChords.chordOf(chroma(0, 4, 7)))
        assertEquals(EchoChord(9, minor = true), EchoChords.chordOf(chroma(9, 0, 4)))
        assertEquals(EchoChord(5, minor = false), EchoChords.chordOf(chroma(5, 9, 0)))
        assertEquals(EchoChord(2, minor = true), EchoChords.chordOf(chroma(2, 5, 9)))
    }

    @Test
    fun `мажор и минор не путаются между собой`() {
        val major = EchoChords.chordOf(chroma(9, 1, 4))
        assertEquals(EchoChord(9, minor = false), major)
    }

    @Test
    fun `тишина и ровный шум остаются безымянными`() {
        assertNull(EchoChords.chordOf(FloatArray(12)))
        // Все двенадцать нот поровну — это не аккорд, а шум: согласия с любым
        // трезвучием тут ровно ноль.
        assertNull(EchoChords.chordOf(FloatArray(12) { 1f }))
    }

    @Test
    fun `имя аккорда пишется как в песеннике`() {
        assertEquals("Am", EchoChord(9, minor = true).name)
        assertEquals("C", EchoChord(0, minor = false).name)
        assertEquals("Bb", EchoChord(10, minor = false).name)
        assertEquals("F#m", EchoChord(6, minor = true).name)
    }

    @Test
    fun `одинокий чужой кусок слушается соседей`() {
        val am = EchoChord(9, minor = true)
        val f = EchoChord(5, minor = false)
        val heard = listOf(am, am, f, am, am)
        assertEquals(listOf(am, am, am, am, am), EchoChords.settle(heard))
    }

    @Test
    fun `настоящая смена аккорда голосованием не стирается`() {
        val am = EchoChord(9, minor = true)
        val f = EchoChord(5, minor = false)
        val heard = listOf(am, am, f, f, am)
        assertEquals(heard, EchoChords.settle(heard))
    }

    @Test
    fun `подряд идущие куски сливаются в один отрезок`() {
        val am = EchoChord(9, minor = true)
        val spans = EchoChords.spansOf(List(6) { am })
        assertEquals(1, spans.size)
        assertEquals(0L, spans.first().fromMs)
        assertEquals(6 * EchoChords.FRAME_MS, spans.first().toMs)
    }

    @Test
    fun `короткий отрезок прирастает к предыдущему, а не выпадает дыркой`() {
        val am = EchoChord(9, minor = true)
        val f = EchoChord(5, minor = false)
        // Один кусок — треть секунды, много короче порога.
        val spans = EchoChords.spansOf(List(4) { am } + f + List(4) { am })

        assertEquals(1, spans.size)
        assertEquals(am, spans.first().chord)
        assertEquals(9 * EchoChords.FRAME_MS, spans.first().toMs)
    }

    @Test
    fun `короткий первый отрезок прирастает к следующему, а не пропадает`() {
        val am = EchoChord(9, minor = true)
        val f = EchoChord(5, minor = false)
        // Первому отдавать себя назад некому: песня начинается с него, и
        // песенник, начинающийся с четвёртой секунды, врал бы про начало.
        val spans = EchoChords.spansOf(listOf(am) + List(8) { f })

        assertEquals(1, spans.size)
        assertEquals(f, spans.first().chord)
        assertEquals(0L, spans.first().fromMs)
    }

    @Test
    fun `безымянные куски не становятся отрезками`() {
        val am = EchoChord(9, minor = true)
        val spans = EchoChords.spansOf(listOf(null, null) + List(5) { am } + listOf(null, null))
        assertEquals(1, spans.size)
        assertEquals(2 * EchoChords.FRAME_MS, spans.first().fromMs)
        assertEquals(7 * EchoChords.FRAME_MS, spans.first().toMs)
    }

    @Test
    fun `тональность читается по среднему отпечатку`() {
        // До-мажорная гамма без чужих нот: до, ре, ми, фа, соль, ля, си.
        assertEquals("C", EchoChords.keyOf(chroma(0, 2, 4, 5, 7, 9, 11)))
        // Та же гамма, сдвинутая на квинту: фа-диез вместо фа — соль-мажор.
        assertEquals("G", EchoChords.keyOf(chroma(7, 9, 11, 0, 2, 4, 6)))
    }

    @Test
    fun `разбор собирается в песенник и считает время каждого аккорда`() {
        val am = chroma(9, 0, 4)
        val f = chroma(5, 9, 0)
        // Ля-минор звучит вдвое дольше, значит и в списке хватов он первый.
        val frames = List(8) { am } + List(8) { f } + List(8) { am }

        val score = EchoChords.score(frames, whole = true)
        assertNotNull(score)
        assertEquals(listOf("Am", "F", "Am"), score.spans.map { it.chord.name })
        assertEquals(listOf("Am", "F"), score.chords.map { it.name })
        assertTrue(score.whole)
    }

    @Test
    fun `дрожание посреди аккорда не становится вторым аккордом`() {
        val am = chroma(9, 0, 4)
        val f = chroma(5, 9, 0)
        val c = chroma(0, 4, 7)

        // Ля-минор на десять секунд, и посреди него по одному куску чужого —
        // ровно то, чем оборачиваются бас с проходящей нотой и голос без
        // гитары. В песеннике этого быть не должно.
        val frames = List(9) { am } + f + List(9) { am } + c + List(9) { am }

        val score = EchoChords.score(frames, whole = true)
        assertNotNull(score)
        assertEquals(listOf("Am"), score.spans.map { it.chord.name })
        assertEquals(listOf("Am"), score.chords.map { it.name })
    }

    @Test
    fun `настоящая смена аккорда доходит до песенника целиком`() {
        val am = chroma(9, 0, 4)
        val dm = chroma(2, 5, 9)

        // Каждый держится по четыре секунды — столько, сколько держат аккорд
        // под гитару. Цена смены такую смену пропускает.
        val frames = List(11) { am } + List(11) { dm } + List(11) { am }

        val score = EchoChords.score(frames, whole = true)
        assertNotNull(score)
        assertEquals(listOf("Am", "Dm", "Am"), score.spans.map { it.chord.name })
    }

    @Test
    fun `усреднение не сдвигает отпечаток, когда вокруг то же самое`() {
        val am = chroma(9, 0, 4)
        val smoothed = EchoChords.smooth(List(5) { am })

        assertEquals(5, smoothed.size)
        smoothed.forEach { frame ->
            assertEquals(EchoChord(9, minor = true), EchoChords.chordOf(frame))
        }
    }

    @Test
    fun `тишина посреди песни не называется аккордом`() {
        val am = chroma(9, 0, 4)
        val nothing = FloatArray(12)
        val frames = List(8) { am } + List(8) { nothing } + List(8) { am }

        val heard = EchoChords.decode(
            EchoChords.smooth(frames),
            EchoChord(9, minor = true),
        )
        // Середина безымянна, края названы: разбор не обязан заполнять паузу.
        assertNull(heard[12])
        assertEquals(EchoChord(9, minor = true), heard.first())
        assertEquals(EchoChord(9, minor = true), heard.last())
    }

    @Test
    fun `у каждого из двадцати четырёх аккордов есть хват на обоих грифах`() {
        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val chord = EchoChord(root, minor)

                val guitar = fingering(chord, EchoInstrument.GUITAR)
                assertEquals(6, guitar.size, "гитара, ${chord.name}")
                assertTrue(guitar.any { it >= 0 }, "гитара, ${chord.name}: все струны глухие")

                val ukulele = fingering(chord, EchoInstrument.UKULELE)
                assertEquals(4, ukulele.size, "укулеле, ${chord.name}")

                assertEquals(3, keysOf(chord).size, "клавиши, ${chord.name}")
            }
        }
    }

    @Test
    fun `хват берёт те же ноты, что и сам аккорд`() {
        // Гитара строем E A D G B E: открытые струны полутонами.
        val open = listOf(4, 9, 2, 7, 11, 4)

        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val chord = EchoChord(root, minor)
                val sounded = fingering(chord, EchoInstrument.GUITAR)
                    .mapIndexedNotNull { string, fret ->
                        if (fret < 0) null else (open[string] + fret) % 12
                    }
                    .toSet()

                assertEquals(
                    chord.notes.toSet(),
                    sounded,
                    "гитара, ${chord.name}: звучит не то трезвучие",
                )
            }
        }
    }

    @Test
    fun `хват укулеле берёт те же ноты, что и сам аккорд`() {
        // Строй G C E A.
        val open = listOf(7, 0, 4, 9)

        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val chord = EchoChord(root, minor)
                val sounded = fingering(chord, EchoInstrument.UKULELE)
                    .mapIndexedNotNull { string, fret ->
                        if (fret < 0) null else (open[string] + fret) % 12
                    }
                    .toSet()

                assertEquals(
                    chord.notes.toSet(),
                    sounded,
                    "укулеле, ${chord.name}: звучит не то трезвучие",
                )
            }
        }
    }
}
