package dev.viko.listaanimes.data

import dev.viko.listaanimes.data.api.RateLimitState
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimitTest {
    @Test fun noWaitWhileThereIsRoom() {
        val s = RateLimitState { 0 }
        s.update("20", null, null, 200, "30")
        assertEquals(0, s.waitMs())
    }

    @Test fun pacesWhenAlmostOut() {
        val s = RateLimitState { 0 }
        s.update("1", null, null, 200, "30")
        assertEquals(2_000, s.waitMs())
    }

    @Test fun usesResetHeaderWhenPresent() {
        var now = 1_000_000L
        val s = RateLimitState { now }
        s.update("0", "1010", null, 200, "90") // reset em epoch 1010 s
        assertEquals(10_000, s.waitMs())
    }

    @Test fun honoursRetryAfterOn429() {
        var now = 0L
        val s = RateLimitState { now }
        s.update("0", null, "42", 429, "30")
        assertEquals(42_000, s.waitMs())
        now = 42_000
        s.afterWait()
        assertEquals(0, s.waitMs())
    }
}
