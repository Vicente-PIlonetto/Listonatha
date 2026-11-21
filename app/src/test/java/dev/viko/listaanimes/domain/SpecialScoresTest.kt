package dev.viko.listaanimes.domain

import dev.viko.listaanimes.domain.score.Scores
import dev.viko.listaanimes.domain.score.SpecialGrade
import dev.viko.listaanimes.domain.score.SpecialScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialScoresTest {
    @Test fun twelveGradesInOrder() {
        assertEquals(
            listOf("S", "S+", "S++", "S+++", "SS", "SS+", "SS++", "SS+++", "SSS", "SSS+", "SSS++", "SSS+++"),
            SpecialGrade.entries.map { it.label },
        )
        assertEquals((1..12).toList(), SpecialGrade.entries.map { it.position })
    }

    @Test fun compressionForAniList() {
        val expected = listOf(95, 95, 96, 96, 97, 97, 98, 98, 99, 99, 100, 100)
        assertEquals(expected, (1..12).map { SpecialScores.compress(it) })
    }

    @Test fun sortingRespectsExactPosition() {
        // (raw, special)
        val items = listOf(
            "A++" to (94 to null),
            "S" to (95 to 1),
            "S+" to (95 to 2),
            "SSS+++" to (100 to 12),
            "SSS++" to (100 to 11),
            "100 sem especial" to (100 to null),
            "S+++" to (96 to 4),
            "96 sem especial" to (96 to null),
            "B" to (77 to null),
        )
        val sorted = items.sortedByDescending { (_, v) -> Scores.sortKey(v.first, v.second) }.map { it.first }
        assertEquals(
            listOf("SSS+++", "SSS++", "100 sem especial", "S+++", "96 sem especial", "S+", "S", "A++", "B"),
            sorted,
        )
    }

    @Test fun staleSpecialDoesNotAffectSort() {
        // Especial guardada como SSS+++ mas a nota no AniList virou 80: vale 80.
        assertEquals(Scores.sortKey(80, null), Scores.sortKey(80, 12))
    }

    @Test fun keptWhenAniListValueMatches() {
        assertEquals(4, SpecialScores.reconcile(4, 96))
        assertEquals(12, SpecialScores.reconcile(12, 100))
        assertEquals(1, SpecialScores.reconcile(1, 95))
    }

    @Test fun discardedWhenChangedOnTheSite() {
        assertNull(SpecialScores.reconcile(4, 95))  // S+++ (96) mudou para 95
        assertNull(SpecialScores.reconcile(12, 99)) // SSS+++ (100) mudou para 99
        assertNull(SpecialScores.reconcile(1, 80))
        assertNull(SpecialScores.reconcile(5, 0))   // nota removida no site
        assertNull(SpecialScores.reconcile(null, 100))
    }
}
