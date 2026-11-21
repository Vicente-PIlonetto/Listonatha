package dev.viko.listaanimes.data.settings

import dev.viko.listaanimes.domain.ListStatus.COMPLETED
import dev.viko.listaanimes.domain.ListStatus.CURRENT
import dev.viko.listaanimes.domain.ListStatus.DROPPED
import dev.viko.listaanimes.domain.ListStatus.PAUSED
import dev.viko.listaanimes.domain.ListStatus.PLANNING
import dev.viko.listaanimes.domain.ListStatus.REPEATING
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusOrderTest {
    @Test fun defaultHomeTabs() {
        assertEquals(listOf(CURRENT, PLANNING, REPEATING, PAUSED), Settings().homeTabs)
    }

    @Test fun missingStatusesGoToTheEnd() {
        assertEquals(listOf(PAUSED, COMPLETED, CURRENT, PLANNING, REPEATING, DROPPED), normalizeStatusOrder(listOf(PAUSED, COMPLETED, PAUSED)))
    }

    @Test fun homeFollowsOrderWithoutHidden() {
        val s = Settings(statusOrder = listOf(COMPLETED, PAUSED, CURRENT, PLANNING, REPEATING, DROPPED), homeHidden = setOf(PLANNING, DROPPED))
        assertEquals(listOf(COMPLETED, PAUSED, CURRENT, REPEATING), s.homeTabs)
    }

    @Test fun neverEmpty() {
        val s = Settings(homeHidden = DEFAULT_STATUS_ORDER.toSet())
        assertEquals(listOf(CURRENT), s.homeTabs)
    }
}
