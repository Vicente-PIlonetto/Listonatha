package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRulesTest {
    private val goodSummary = "a".repeat(20)
    private val goodBody = "b".repeat(2600)

    @Test fun valid() {
        val c = ReviewRules.check(goodSummary, goodBody, 80)
        assertTrue(c.canPublish)
        assertEquals(0, c.summaryMissing)
        assertEquals(0, c.bodyMissing)
    }

    @Test fun summaryLimits() {
        assertEquals(1, ReviewRules.check("a".repeat(19), goodBody, 80).summaryMissing)
        assertFalse(ReviewRules.check("a".repeat(19), goodBody, 80).canPublish)
        assertTrue(ReviewRules.check("a".repeat(120), goodBody, 80).canPublish)
        val over = ReviewRules.check("a".repeat(121), goodBody, 80)
        assertEquals(1, over.summaryExcess)
        assertFalse(over.canPublish)
    }

    @Test fun bodyMinimum() {
        val c = ReviewRules.check(goodSummary, "b".repeat(2599), 80)
        assertEquals(1, c.bodyMissing)
        assertFalse(c.canPublish)
        assertEquals(2600, ReviewRules.check(goodSummary, "", 80).bodyMissing)
    }

    @Test fun whitespaceDoesNotCount() {
        val c = ReviewRules.check("   " + "a".repeat(19) + "   ", "  " + goodBody, 80)
        assertEquals(19, c.summaryLength)
        assertFalse(c.canPublish)
    }

    @Test fun scoreRange() {
        assertTrue(ReviewRules.check(goodSummary, goodBody, 0).canPublish)
        assertTrue(ReviewRules.check(goodSummary, goodBody, 100).canPublish)
        assertFalse(ReviewRules.check(goodSummary, goodBody, 101).canPublish)
        assertFalse(ReviewRules.check(goodSummary, goodBody, -1).canPublish)
        assertFalse(ReviewRules.check(goodSummary, goodBody, null).canPublish)
    }

    @Test fun emojiCountsAsOneCharacter() {
        assertEquals(20, ReviewRules.check("a".repeat(19) + "😀", goodBody, 50).summaryLength)
    }
}
