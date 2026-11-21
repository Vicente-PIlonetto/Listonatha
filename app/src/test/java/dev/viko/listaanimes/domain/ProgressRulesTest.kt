package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressRulesTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val todayF = FuzzyDate(2026, 11, 19)
    private val now = 1_800_000_000L

    private fun anime(eps: Int?, status: String = "FINISHED", next: Int? = null, nextAt: Long? = null) =
        Media(id = 1, kind = MediaKind.ANIME, episodes = eps, status = status, nextEpisode = next, nextAiringAt = nextAt)

    private fun manga(ch: Int?, status: String = "FINISHED") = Media(id = 2, kind = MediaKind.MANGA, chapters = ch, status = status)

    private fun entry(status: ListStatus, progress: Int = 0, kind: MediaKind = MediaKind.ANIME) =
        ListEntry(mediaId = 1, kind = kind, status = status, progress = progress)

    @Test fun firstEpisodeFromPlanningStartsWatching() {
        val r = ProgressRules.increment(entry(ListStatus.PLANNING), anime(12), today, now)
        assertEquals(1, r.progress)
        assertEquals(ListStatus.CURRENT, r.status)
        assertEquals(todayF, r.startedAt)
        assertTrue(r.completedAt.isEmpty)
    }

    @Test fun firstChapterFromPlanningStartsReading() {
        val r = ProgressRules.increment(entry(ListStatus.PLANNING, kind = MediaKind.MANGA), manga(null, "RELEASING"), today, now)
        assertEquals(ListStatus.CURRENT, r.status)
        assertEquals(todayF, r.startedAt)
    }

    @Test fun existingStartDateIsKept() {
        val e = entry(ListStatus.PLANNING).copy(startedAt = FuzzyDate(2025, 1, 2))
        assertEquals(FuzzyDate(2025, 1, 2), ProgressRules.increment(e, anime(12), today, now).startedAt)
    }

    @Test fun lastEpisodeOfFinishedShowCompletes() {
        val r = ProgressRules.increment(entry(ListStatus.CURRENT, 11), anime(12), today, now)
        assertEquals(12, r.progress)
        assertEquals(ListStatus.COMPLETED, r.status)
        assertEquals(todayF, r.completedAt)
    }

    @Test fun lastEpisodeOfReleasingShowDoesNotComplete() {
        val r = ProgressRules.increment(entry(ListStatus.CURRENT, 11), anime(12, "RELEASING"), today, now)
        assertEquals(ListStatus.CURRENT, r.status)
        assertTrue(r.completedAt.isEmpty)
    }

    @Test fun singleEpisodeMovieFromPlanningSetsBothDates() {
        val r = ProgressRules.increment(entry(ListStatus.PLANNING), anime(1), today, now)
        assertEquals(ListStatus.COMPLETED, r.status)
        assertEquals(todayF, r.startedAt)
        assertEquals(todayF, r.completedAt)
    }

    @Test fun finishingARewatchCountsARepeat() {
        val r = ProgressRules.increment(entry(ListStatus.REPEATING, 11).copy(repeat = 1), anime(12), today, now)
        assertEquals(ListStatus.COMPLETED, r.status)
        assertEquals(2, r.repeat)
    }

    @Test fun exactValueToLastCompletesToo() {
        val r = ProgressRules.applyProgress(entry(ListStatus.CURRENT, 3), anime(24), 24, today, now)
        assertEquals(ListStatus.COMPLETED, r.status)
    }

    @Test fun progressIsCappedAtTotal() {
        val r = ProgressRules.applyProgress(entry(ListStatus.CURRENT, 3), anime(12), 40, today, now)
        assertEquals(12, r.progress)
        assertFalse(ProgressRules.canIncrement(r, anime(12)))
        assertTrue(ProgressRules.canIncrement(entry(ListStatus.CURRENT, 500, MediaKind.MANGA), manga(null, "RELEASING")))
    }

    @Test fun pausedStaysPausedOnIncrement() {
        val r = ProgressRules.increment(entry(ListStatus.PAUSED, 3), anime(12), today, now)
        assertEquals(ListStatus.PAUSED, r.status)
    }

    @Test fun progressText() {
        assertEquals("6/12", ProgressRules.progressText(entry(ListStatus.CURRENT, 6), anime(12)))
        assertEquals("Cap. 143", ProgressRules.progressText(entry(ListStatus.CURRENT, 143, MediaKind.MANGA), manga(null, "RELEASING")))
        assertEquals("143/150", ProgressRules.progressText(entry(ListStatus.CURRENT, 143, MediaKind.MANGA), manga(150)))
        assertEquals("Ep. 6", ProgressRules.progressText(entry(ListStatus.CURRENT, 6), anime(null, "RELEASING")))
    }

    @Test fun behindUsesNextAiringEpisode() {
        // Próximo é o 8, ainda não saiu: 7 lançados, vi 6 → 1 atrasado.
        val m = anime(12, "RELEASING", next = 8, nextAt = now + 3600)
        assertEquals(1, ProgressRules.episodesBehind(entry(ListStatus.CURRENT, 6), m, now))
        assertEquals("1 atrasado", ProgressRules.behindText(1))
        assertEquals("3 atrasados", ProgressRules.behindText(3))
        assertEquals(null, ProgressRules.behindText(0))
    }

    @Test fun behindCountsEpisodeWhoseTimeAlreadyPassed() {
        // Cache diz "EP 8 às X" e X já passou: 8 lançados.
        val m = anime(12, "RELEASING", next = 8, nextAt = now - 60)
        assertEquals(2, ProgressRules.episodesBehind(entry(ListStatus.CURRENT, 6), m, now))
    }

    @Test fun notBehindWhenUpToDateOrNotWatching() {
        val m = anime(12, "RELEASING", next = 8, nextAt = now + 3600)
        assertEquals(0, ProgressRules.episodesBehind(entry(ListStatus.CURRENT, 7), m, now))
        assertEquals(0, ProgressRules.episodesBehind(entry(ListStatus.CURRENT, 9), m, now))
        assertEquals(0, ProgressRules.episodesBehind(entry(ListStatus.PLANNING, 0), m, now))
        assertEquals(0, ProgressRules.episodesBehind(entry(ListStatus.CURRENT, 2), anime(12), now))
        assertEquals(3, ProgressRules.episodesBehind(entry(ListStatus.REPEATING, 4), m, now))
    }
}
