package dev.viko.listaanimes.data

import dev.viko.listaanimes.data.db.ChangeType
import dev.viko.listaanimes.data.db.PendingChange
import dev.viko.listaanimes.data.sync.ChangeQueue
import dev.viko.listaanimes.data.sync.QueueStore
import dev.viko.listaanimes.data.sync.SendResult
import dev.viko.listaanimes.domain.EntryPatch
import dev.viko.listaanimes.domain.FuzzyDate
import dev.viko.listaanimes.domain.ListStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeQueueTest {
    private class MemoryStore : QueueStore {
        val items = mutableListOf<PendingChange>()
        private var seq = 0L
        override suspend fun all() = items.sortedBy { it.seq }
        override suspend fun last() = items.maxByOrNull { it.seq }
        override suspend fun insert(change: PendingChange): Long { seq++; items += change.copy(seq = seq); return seq }
        override suspend fun replacePayload(seq: Long, payload: String, at: Long) {
            val i = items.indexOfFirst { it.seq == seq }; items[i] = items[i].copy(payload = payload, createdAt = at)
        }
        override suspend fun delete(seq: Long) { items.removeAll { it.seq == seq } }
        override suspend fun bumpAttempts(seq: Long) {
            val i = items.indexOfFirst { it.seq == seq }; items[i] = items[i].copy(attempts = items[i].attempts + 1)
        }
    }

    private fun patch(mediaId: Int, progress: Int, status: ListStatus = ListStatus.CURRENT, score: Int = 0) =
        EntryPatch(mediaId, status, score, progress, 0, 0, null, false, FuzzyDate.EMPTY, FuzzyDate.EMPTY)

    @Test fun sendsInTheOrderTheyWereMade() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(1, 1))
        q.enqueueSave(patch(2, 5))
        q.enqueueDelete(3, 300)
        q.enqueueSave(patch(1, 2))
        val sent = mutableListOf<String>()
        val r = q.flush({ c ->
            sent += if (c.type == ChangeType.DELETE) "del-${c.mediaId}" else "save-${c.mediaId}-${q.decodePatch(c).progress}"
            SendResult.Sent()
        })
        assertEquals(listOf("save-1-1", "save-2-5", "del-3", "save-1-2"), sent)
        assertEquals(4, r.sent)
        assertTrue(r.complete)
        assertTrue(store.items.isEmpty())
    }

    @Test fun consecutiveChangesToSameWorkAreMerged() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(1, 1))
        q.enqueueSave(patch(1, 2))
        q.enqueueSave(patch(1, 3))
        assertEquals(1, store.items.size)
        assertEquals(3, q.decodePatch(store.items.single()).progress)
    }

    @Test fun mergeNeverReordersAcrossWorks() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(1, 1))
        q.enqueueSave(patch(2, 1))
        q.enqueueSave(patch(1, 2))
        assertEquals(listOf(1, 2, 1), store.items.sortedBy { it.seq }.map { it.mediaId })
    }

    @Test fun offlineStopsAndKeepsEverythingInOrder() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(1, 1))
        q.enqueueSave(patch(2, 1))
        q.enqueueSave(patch(3, 1))
        var calls = 0
        val r = q.flush({ c -> calls++; if (c.mediaId == 2) SendResult.RetryLater else SendResult.Sent() })
        assertEquals(1, r.sent)
        assertEquals(2, r.remaining)
        assertFalse(r.complete)
        assertEquals(2, calls) // não tenta a 3 antes da 2
        assertEquals(listOf(2, 3), store.items.sortedBy { it.seq }.map { it.mediaId })
        assertEquals(1, store.items.first { it.mediaId == 2 }.attempts)

        // Conexão voltou: continua de onde parou.
        val sent = mutableListOf<Int>()
        val r2 = q.flush({ c -> sent += c.mediaId; SendResult.Sent() })
        assertEquals(listOf(2, 3), sent)
        assertTrue(r2.complete)
    }

    @Test fun rejectedChangeIsDroppedAndQueueContinues() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(1, 1))
        q.enqueueSave(patch(2, 1))
        val r = q.flush({ c -> if (c.mediaId == 1) SendResult.Rejected("obra removida") else SendResult.Sent() })
        assertEquals(1, r.sent)
        assertEquals(listOf("obra removida"), r.rejected)
        assertTrue(r.complete)
    }

    @Test fun onSentReceivesNewEntryId() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        q.enqueueSave(patch(9, 1))
        var got: Int? = null
        q.flush({ SendResult.Sent(entryId = 555) }, onSent = { _, s -> got = s.entryId })
        assertEquals(555, got)
    }

    @Test fun payloadKeepsAllEditableFields() = runTest {
        val store = MemoryStore()
        val q = ChangeQueue(store, clock = { 0 })
        val p = EntryPatch(7, ListStatus.COMPLETED, 96, 12, 3, 2, "nota", true, FuzzyDate(2026, 1, 2), FuzzyDate(2026, 3, 4))
        q.enqueueSave(p)
        assertEquals(p, q.decodePatch(store.items.single()))
    }
}
