package dev.viko.listaanimes.domain

import dev.viko.listaanimes.domain.score.ScoreBuckets
import dev.viko.listaanimes.domain.score.ScoreSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreBucketsTest {
    @Test fun unscoredIsIgnored() {
        assertNull(ScoreBuckets.of(0, ScoreSystem.POINT_100))
        assertEquals(0, ScoreBuckets.distribution(listOf(0, 0), ScoreSystem.LETTERS).sumOf { it.second })
    }

    @Test fun point100UsesTens() {
        val d = ScoreBuckets.distribution(listOf(1, 10, 11, 95, 100), ScoreSystem.POINT_100).associate { it.first.order to it.second }
        assertEquals(2, d[10]); assertEquals(1, d[20]); assertEquals(2, d[100])
    }

    @Test fun sevenFacesFollowBands() {
        val d = ScoreBuckets.distribution(listOf(97, 89, 10), ScoreSystem.SMILEY_7).associate { it.first.order to it.second }
        assertEquals(1, d[7]); assertEquals(1, d[6]); assertEquals(1, d[1]); assertEquals(7, d.size)
    }

    @Test fun axisIsComplete() {
        assertEquals(6, ScoreBuckets.axis(ScoreSystem.LETTERS).size)
        assertEquals(3, ScoreBuckets.axis(ScoreSystem.SMILEY_3).size)
        assertEquals(5, ScoreBuckets.axis(ScoreSystem.STARS_5).size)
    }

    @Test fun meanAndDeviation() {
        val (m, d) = ScoreBuckets.meanAndDeviation(listOf(0, 60, 80))!!
        assertEquals(70.0, m, 0.001); assertEquals(10.0, d, 0.001)
    }
}
