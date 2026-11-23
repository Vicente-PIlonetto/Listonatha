package dev.viko.listaanimes.domain

import dev.viko.listaanimes.domain.score.ScoreSystem
import dev.viko.listaanimes.domain.score.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListScoreFormatTest {
    @Test fun mapsEveryAniListFormat() {
        assertEquals(ScoreSystem.POINT_100, ScoreSystem.fromAniList("POINT_100"))
        assertEquals(ScoreSystem.POINT_10_DECIMAL, ScoreSystem.fromAniList("POINT_10_DECIMAL"))
        assertEquals(ScoreSystem.POINT_10, ScoreSystem.fromAniList("POINT_10"))
        assertEquals(ScoreSystem.STARS_5, ScoreSystem.fromAniList("POINT_5"))
        assertEquals(ScoreSystem.SMILEY_3, ScoreSystem.fromAniList("POINT_3"))
        assertNull(ScoreSystem.fromAniList(null))
    }

    @Test fun friendScoreShowsInTheirFormat() {
        // Um 80 de quem usa 0–100 aparece como 80, não como a letra de quem está olhando.
        assertEquals("80", Scores.text(80, ScoreSystem.fromAniList("POINT_100")!!))
        assertEquals("4", Scores.text(80, ScoreSystem.fromAniList("POINT_5")!!))
    }
}
