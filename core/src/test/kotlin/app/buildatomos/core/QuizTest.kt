package app.buildatomos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuizTest {
    private fun asset(name: String) = (File("../app/src/main/assets/$name").takeIf { it.exists() } ?: File("app/src/main/assets/$name")).readText()
    private val info = AtomInfo.readAll(asset("atoms-info.json"))
    @Suppress("UNCHECKED_CAST")
    private val names = (Json.parse(asset("atoms.json")) as List<Map<String, Any?>>).sortedBy { it["z"] as Double }.map { it["n"] as String }
    private val quiz = Quiz(names, info)
    private fun at(z: Int) = info.first { it.z == z }

    @Test fun everyDayDealsThree() {
        for (day in 0..2000) {
            val qs = quiz.today(day)
            assertEquals(3, qs.size, "day $day")
            assertEquals(3, qs.map { it.type.topic }.toSet().size, "day $day: three topics")
            assertTrue(qs.count { it.type.four } <= 1, "day $day: one four-card question at most")
            val zs = qs.flatMap { it.atoms }
            assertEquals(zs.size, zs.toSet().size, "day $day: an atom twice")
            for (q in qs) {
                assertTrue(q.options.size == 2 || q.options.size == 4, "day $day: ${q.text}")
                assertTrue(q.answer in q.options.indices, "day $day: ${q.text}")
                assertEquals(q.options.size, q.after.size)
                assertTrue(q.text.isNotBlank())
            }
            assertEquals(qs, quiz.today(day), "day $day deals the same twice")
        }
    }

    /** The answers are true to the data. */
    @Test fun answersAreRight() {
        val qs = (0..2000).flatMap { quiz.today(it) }
        fun cards(q: QuizQuestion) = q.options.map { at((it as QuizOption.Card).z) }
        fun checkCompare(type: QuizType, higher: String, value: (AtomInfo) -> Double) {
            val some = qs.filter { it.type == type }
            assertTrue(some.isNotEmpty(), "no $type")
            for (q in some) {
                val (a, b) = cards(q).map(value)
                val right = if (q.text == higher) (if (a > b) 0 else 1) else (if (a < b) 0 else 1)
                assertEquals(right, q.answer, q.text)
            }
        }
        checkCompare(QuizType.MELT, "Which has the higher melting point?") { it.melt!! }
        checkCompare(QuizType.BOIL, "Which has the higher boiling point?") { it.boil!! }
        checkCompare(QuizType.DENSE, "Which is denser?") { it.density!! }
        checkCompare(QuizType.CRUST, "Which is more common in Earth's crust?") { it.crust!! }
        checkCompare(QuizType.SEA, "Which is more common in seawater?") { it.sea!! }
        checkCompare(QuizType.FIRST, "Which was discovered more recently?") { it.year!!.toDouble() }
        for (q in qs) {
            val right = q.options[q.answer]
            when (q.type) {
                QuizType.RADIOACTIVE -> assertEquals(0, at((right as QuizOption.Card).z).stableIsotopes)
                QuizType.NOBLE -> assertEquals("Noble gases", at((right as QuizOption.Card).z).series)
                QuizType.LIQUID4 -> assertEquals("Liquid", at((right as QuizOption.Card).z).phase)
                QuizType.LIFE4 -> assertTrue((right as QuizOption.Card).z in Quiz.LIFE)
                QuizType.PLACE -> assertTrue((right as QuizOption.Card).z in Quiz.PLACE_NAMES)
                QuizType.FIRST4 -> assertEquals(cards(q).minOf { it.year!! }, at((right as QuizOption.Card).z).year)
                QuizType.PHASE -> assertEquals(at(q.about!!).phase, (right as QuizOption.Word).text)
                QuizType.METAL -> assertEquals(at(q.about!!).series in AtomInfo.METALS, (right as QuizOption.Word).text == "Yes")
                QuizType.YEAR -> assertEquals(at(q.about!!).year.toString(), (right as QuizOption.Word).text)
                else -> {}
            }
        }
        val iron = assertNotNull(qs.firstOrNull { q -> q.type == QuizType.MELT && q.atoms.contains(26) })
        assertEquals("1,538 °C", iron.after[iron.atoms.indexOf(26)])
    }

    @Test fun phoneWords() {
        assertEquals("−39 °C", Quiz.celsius(234.32))
        assertEquals("1,538 °C", Quiz.celsius(1811.0))
        assertEquals("7.87 g/cm³", Quiz.density(7.874))
        assertEquals("0.53 g/cm³", Quiz.density(0.534))
        assertEquals("46%", Quiz.share(461000.0))
        assertEquals("1 part per million", Quiz.share(1.0))
        assertEquals("230 parts per million", Quiz.share(234.0))
        assertEquals("0.23%", Quiz.share(2340.0))
        assertEquals("4 parts per billion", Quiz.share(0.004))
        assertEquals("A, B and C", Quiz.people("A, B, C"))
    }

    /**
     * The same questions as the phone deals, word for word: quiz-phone.json is the phone's own
     * quizOf for 100 days from 20700 (2026-09-04), made with tools/gen-quiz.mts. Point QUIZ_PHONE
     * at a longer run of it to check more days (0..30000 matched when this was written).
     */
    @Test fun dealsAsThePhoneDoes() {
        val f = System.getenv("QUIZ_PHONE")?.let(::File) ?: File("src/test/resources/quiz-phone.json")
        @Suppress("UNCHECKED_CAST")
        val days = Json.parse(f.readText()) as Map<String, List<Map<String, Any?>>>
        for ((day, phone) in days) {
            assertEquals(phone, quiz.today(day.toInt()).map(::phoneShape), "day $day")
        }
    }

    /** A question as the phone's JSON has it. */
    private fun phoneShape(q: QuizQuestion): Map<String, Any?> = buildMap {
        put("type", q.type.id)
        put("text", q.text)
        put("options", q.options.map { o ->
            when (o) {
                is QuizOption.Card -> mapOf("z" to o.z.toDouble())
                is QuizOption.Word -> mapOf("text" to o.text)
            }
        })
        put("answer", q.answer.toDouble())
        put("after", q.after)
        q.about?.let { put("about", it.toDouble()) }
        q.note?.let { put("note", it) }
    }

    @Test fun aDayAndAStreak() {
        var s = QuizDay(100)
        assertEquals("Start a streak", s.streakLabel)
        s = s.answer(true).answer(false)
        assertEquals(0, s.streak)
        s = s.answer(true)
        assertTrue(s.done())
        assertEquals(2, s.right)
        assertEquals(1, s.streak)
        assertEquals(s, s.answer(true), "a finished day takes no more answers")
        s = s.on(101)
        assertEquals(emptyList(), s.answers)
        assertEquals("Streak: 1 day", s.streakLabel)
        s = s.answer(true).answer(true).answer(true)
        assertEquals(2, s.liveStreak)
        assertEquals(s, s.finish(), "counted once a day")
        s = s.on(103)
        assertEquals(0, s.liveStreak, "a day missed")
        s = s.answer(false).answer(false).answer(false)
        assertEquals(1, s.streak)
    }
}
