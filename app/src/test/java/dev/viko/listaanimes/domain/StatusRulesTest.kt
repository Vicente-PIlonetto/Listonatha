package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatusRulesTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val t = FuzzyDate(2026, 11, 20)
    private val anime = Media(id = 1, kind = MediaKind.ANIME, episodes = 12, status = "FINISHED")
    private fun e(st: ListStatus, progress: Int = 0) = ListEntry(mediaId = 1, kind = MediaKind.ANIME, status = st, progress = progress)

    @Test fun markAsSeenFromDiscoverCompletesEverything() {
        val r = ProgressRules.applyStatus(e(ListStatus.PLANNING), anime, ListStatus.COMPLETED, today, 0)
        assertEquals(ListStatus.COMPLETED, r.status)
        assertEquals(12, r.progress)
        assertEquals(t, r.startedAt)
        assertEquals(t, r.completedAt)
    }

    @Test fun watchingFillsStartOnly() {
        val r = ProgressRules.applyStatus(e(ListStatus.PLANNING), anime, ListStatus.CURRENT, today, 0)
        assertEquals(t, r.startedAt)
        assertEquals(true, r.completedAt.isEmpty)
        assertEquals(0, r.progress)
    }

    @Test fun keepsExistingStartDate() {
        val r = ProgressRules.applyStatus(e(ListStatus.CURRENT, 4).copy(startedAt = FuzzyDate(2025, 2, 3)), anime, ListStatus.COMPLETED, today, 0)
        assertEquals(FuzzyDate(2025, 2, 3), r.startedAt)
        assertEquals(t, r.completedAt)
    }

    @Test fun finishingRewatchCountsRepeat() {
        val r = ProgressRules.applyStatus(e(ListStatus.REPEATING, 5).copy(repeat = 2), anime, ListStatus.COMPLETED, today, 0)
        assertEquals(3, r.repeat)
    }

    @Test fun rewatchStartsFromZero() {
        val r = ProgressRules.applyStatus(e(ListStatus.COMPLETED, 12), anime, ListStatus.REPEATING, today, 0)
        assertEquals(0, r.progress)
    }

    @Test fun unknownTotalKeepsProgress() {
        val manga = Media(id = 2, kind = MediaKind.MANGA, chapters = null, status = "RELEASING")
        val r = ProgressRules.applyStatus(ListEntry(mediaId = 2, kind = MediaKind.MANGA, status = ListStatus.CURRENT, progress = 80), manga, ListStatus.COMPLETED, today, 0)
        assertEquals(80, r.progress)
    }
}
