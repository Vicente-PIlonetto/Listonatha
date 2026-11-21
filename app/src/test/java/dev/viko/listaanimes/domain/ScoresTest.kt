package dev.viko.listaanimes.domain

import dev.viko.listaanimes.domain.score.Bands
import dev.viko.listaanimes.domain.score.LetterGrade
import dev.viko.listaanimes.domain.score.ScoreDisplay
import dev.viko.listaanimes.domain.score.ScoreSystem
import dev.viko.listaanimes.domain.score.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoresTest {
    private fun t(raw: Int, sys: ScoreSystem, special: Int? = null) = Scores.text(raw, sys, special)

    @Test fun zeroIsNoScoreInEverySystem() {
        ScoreSystem.entries.forEach { assertEquals(it.name, ScoreDisplay.None, Scores.display(0, it)) }
        ScoreSystem.entries.forEach { assertEquals(0, Scores.snap(0, it)) }
    }

    @Test fun point100() {
        assertEquals("89", t(89, ScoreSystem.POINT_100))
        assertEquals("1", t(1, ScoreSystem.POINT_100))
        assertEquals("100", t(100, ScoreSystem.POINT_100))
    }

    @Test fun point10Decimal() {
        assertEquals("7,5", t(75, ScoreSystem.POINT_10_DECIMAL))
        assertEquals("10,0", t(100, ScoreSystem.POINT_10_DECIMAL))
        assertEquals("0,1", t(1, ScoreSystem.POINT_10_DECIMAL))
    }

    @Test fun point10RoundsToNearestAndNeverShowsZero() {
        assertEquals("7", t(72, ScoreSystem.POINT_10))
        assertEquals("8", t(75, ScoreSystem.POINT_10))
        assertEquals("1", t(3, ScoreSystem.POINT_10))
        assertEquals("10", t(96, ScoreSystem.POINT_10))
        assertEquals(70, Scores.snap(72, ScoreSystem.POINT_10))
    }

    @Test fun starsAcceptHalf() {
        assertEquals("3,5", t(70, ScoreSystem.STARS_5))
        assertEquals(ScoreDisplay.Stars(7), Scores.display(70, ScoreSystem.STARS_5))
        assertEquals("5", t(100, ScoreSystem.STARS_5))
        assertEquals("0,5", t(4, ScoreSystem.STARS_5))
        assertEquals(70, Scores.snap(68, ScoreSystem.STARS_5))
    }

    @Test fun threeSmileysUseNearest() {
        assertEquals("Triste", t(35, ScoreSystem.SMILEY_3))
        assertEquals("Neutra", t(60, ScoreSystem.SMILEY_3))
        assertEquals("Feliz", t(85, ScoreSystem.SMILEY_3))
        assertEquals("Triste", t(10, ScoreSystem.SMILEY_3))
        assertEquals("Feliz", t(100, ScoreSystem.SMILEY_3))
        assertEquals("Neutra", t(72, ScoreSystem.SMILEY_3)) // 72: 12 de 60, 13 de 85
        assertEquals("Triste", t(47, ScoreSystem.SMILEY_3)) // empate 35/60 → menor
        assertEquals(85, Scores.snap(80, ScoreSystem.SMILEY_3))
    }

    @Test fun bandsCoverEveryValueOnce() {
        for (raw in 1..100) {
            val matches = Bands.all.count { raw in it.min..it.max }
            assertEquals("raw $raw", 1, matches)
        }
    }

    @Test fun point7AndSevenFacesFollowBaseBands() {
        val expected = mapOf(100 to 7, 95 to 7, 94 to 6, 85 to 6, 84 to 5, 70 to 5, 69 to 4, 55 to 4, 54 to 3, 40 to 3, 39 to 2, 20 to 2, 19 to 1, 1 to 1)
        expected.forEach { (raw, v) -> assertEquals("raw $raw", "$v", t(raw, ScoreSystem.POINT_7)) }
        assertEquals("Adorei", t(97, ScoreSystem.SMILEY_7))
        assertEquals("Gostei muito", t(89, ScoreSystem.SMILEY_7))
        assertEquals("Gostei", t(77, ScoreSystem.SMILEY_7))
        assertEquals("Ok", t(62, ScoreSystem.SMILEY_7))
        assertEquals("Não gostei", t(47, ScoreSystem.SMILEY_7))
        assertEquals("Ruim", t(29, ScoreSystem.SMILEY_7))
        assertEquals("Detestei", t(10, ScoreSystem.SMILEY_7))
    }

    @Test fun point7AndFacesStoreMiddleOfBand() {
        val mids = mapOf(7 to 97, 6 to 89, 5 to 77, 4 to 62, 3 to 47, 2 to 29, 1 to 10)
        mids.forEach { (v, mid) ->
            val opt = Scores.options(ScoreSystem.POINT_7).single { it.label == "$v" }
            assertEquals(mid, opt.raw)
            assertEquals(mid, Bands.byValue(v)!!.mid)
        }
        assertEquals(97, Scores.snap(100, ScoreSystem.SMILEY_7))
        assertEquals(10, Scores.snap(1, ScoreSystem.POINT_7))
    }

    @Test fun lettersTableValues() {
        val table = mapOf(
            "A−−" to 85, "A−" to 87, "A" to 89, "A+" to 92, "A++" to 94,
            "B−−" to 71, "B−" to 74, "B" to 77, "B+" to 80, "B++" to 83,
            "C−−" to 56, "C−" to 59, "C" to 62, "C+" to 65, "C++" to 68,
            "D−−" to 41, "D−" to 44, "D" to 47, "D+" to 50, "D++" to 53,
            "E−−" to 21, "E−" to 25, "E" to 29, "E+" to 33, "E++" to 37,
            "F−−" to 2, "F−" to 6, "F" to 10, "F+" to 14, "F++" to 18,
        )
        assertEquals(30, LetterGrade.all.size)
        table.forEach { (label, raw) -> assertEquals(label, raw, LetterGrade.all.single { it.label == label }.raw) }
    }

    @Test fun lettersNearestValue() {
        assertEquals("B−−", t(72, ScoreSystem.LETTERS))
        assertEquals("B", t(77, ScoreSystem.LETTERS))
        assertEquals("A", t(89, ScoreSystem.LETTERS))
        assertEquals("A++", t(94, ScoreSystem.LETTERS))
        assertEquals("F−−", t(1, ScoreSystem.LETTERS))
        assertEquals("B++", t(84, ScoreSystem.LETTERS)) // empate 83/85 → menor, igual à faixa base
        assertEquals("E++", t(39, ScoreSystem.LETTERS)) // empate 37/41 → menor
        assertEquals("D−−", t(40, ScoreSystem.LETTERS))
        assertEquals("C−−", t(55, ScoreSystem.LETTERS))
        assertEquals("B−−", t(70, ScoreSystem.LETTERS))
        assertEquals(71, Scores.snap(72, ScoreSystem.LETTERS))
    }

    @Test fun lettersAbove94WithoutStoredSpecialShowTierBase() {
        assertEquals("S", t(95, ScoreSystem.LETTERS))
        assertEquals("S", t(96, ScoreSystem.LETTERS))
        assertEquals("SS", t(97, ScoreSystem.LETTERS))
        assertEquals("SS", t(98, ScoreSystem.LETTERS))
        assertEquals("SSS", t(99, ScoreSystem.LETTERS))
        assertEquals("SSS", t(100, ScoreSystem.LETTERS))
        val d = Scores.display(96, ScoreSystem.LETTERS) as ScoreDisplay.Text
        assertTrue(d.special)
    }

    @Test fun lettersWithStoredSpecial() {
        assertEquals("S+++", t(96, ScoreSystem.LETTERS, special = 4))
        assertEquals("SSS+++", t(100, ScoreSystem.LETTERS, special = 12))
        // Especial que não bate com o valor é ignorada na exibição.
        assertEquals("SS", t(97, ScoreSystem.LETTERS, special = 12))
    }

    /** Ida e volta: escolher qualquer opção e mostrar de novo dá a mesma opção, em todos os sistemas. */
    @Test fun roundTripEveryOptionInEverySystem() {
        ScoreSystem.entries.forEach { sys ->
            Scores.options(sys).forEach { opt ->
                assertEquals("${sys.name} ${opt.label}", opt.label, t(opt.raw, sys, opt.special?.position))
                assertEquals("${sys.name} snap ${opt.label}", opt.raw, Scores.snap(opt.raw, sys))
            }
        }
    }

    /** Converter de um sistema para outro passando pelo 0–100 e voltando mantém a opção mais próxima. */
    @Test fun snapIsIdempotentForAllValues() {
        ScoreSystem.entries.forEach { sys ->
            for (raw in 0..100) {
                val once = Scores.snap(raw, sys)
                assertEquals("${sys.name} $raw", once, Scores.snap(once, sys))
                assertEquals("${sys.name} $raw", t(raw, sys), t(once, sys))
            }
        }
    }

    @Test fun crossSystemConversion() {
        // Nota dada em 0–7 (5 = 77) vista em letras e vice-versa.
        assertEquals("B", t(77, ScoreSystem.LETTERS))
        assertEquals("5", t(Scores.snap(80, ScoreSystem.LETTERS), ScoreSystem.POINT_7)) // B+ = 80 → 5
        assertEquals("Gostei muito", t(89, ScoreSystem.SMILEY_7)) // A → 6
        assertEquals("4,5", t(89, ScoreSystem.STARS_5))
    }
}
