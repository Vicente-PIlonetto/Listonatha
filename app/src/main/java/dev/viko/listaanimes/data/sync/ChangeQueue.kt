package dev.viko.listaanimes.data.sync

import dev.viko.listaanimes.data.db.ChangeType
import dev.viko.listaanimes.data.db.PendingChange
import dev.viko.listaanimes.domain.EntryPatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Armazenamento da fila (Room no app, memória nos testes). */
interface QueueStore {
    suspend fun all(): List<PendingChange>
    suspend fun last(): PendingChange?
    suspend fun insert(change: PendingChange): Long
    suspend fun replacePayload(seq: Long, payload: String, at: Long)
    suspend fun delete(seq: Long)
    suspend fun bumpAttempts(seq: Long)
}

sealed interface SendResult {
    /** Enviado. [entryId] = id devolvido pelo AniList, quando houver. */
    data class Sent(val entryId: Int? = null) : SendResult
    /** Sem conexão, limite de requisições, servidor fora, sessão expirada: tentar de novo depois, sem perder a ordem. */
    data object RetryLater : SendResult
    /** O AniList recusou a mudança (obra removida, dado inválido): descarta para não travar a fila. */
    data class Rejected(val reason: String) : SendResult
}

fun interface ChangeSender {
    suspend fun send(change: PendingChange): SendResult
}

data class FlushResult(val sent: Int, val rejected: List<String>, val remaining: Int) {
    val complete: Boolean get() = remaining == 0
}

/**
 * Fila de mudanças feitas sem internet. Cada SAVE leva o estado completo da entrada,
 * então reenviar é seguro. As mudanças saem na ordem em que foram feitas.
 */
class ChangeQueue(
    private val store: QueueStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueSave(patch: EntryPatch) {
        val payload = json.encodeToString(EntryPatch.serializer(), patch)
        val last = store.last()
        // Várias mudanças seguidas na mesma obra (ex.: +1, +1, +1) viram um envio só.
        // Só junta com a última da fila, para não alterar a ordem entre obras.
        if (last != null && last.type == ChangeType.SAVE && last.mediaId == patch.mediaId) {
            store.replacePayload(last.seq, payload, clock())
        } else {
            store.insert(PendingChange(mediaId = patch.mediaId, type = ChangeType.SAVE, payload = payload, createdAt = clock()))
        }
    }

    suspend fun enqueueDelete(mediaId: Int, entryId: Int) {
        store.insert(PendingChange(mediaId = mediaId, type = ChangeType.DELETE, payload = entryId.toString(), createdAt = clock()))
    }

    /** Envia tudo em ordem. Para no primeiro erro temporário e mantém o resto da fila. */
    suspend fun flush(sender: ChangeSender, onSent: suspend (PendingChange, SendResult.Sent) -> Unit = { _, _ -> }): FlushResult =
        mutex.withLock {
            var sent = 0
            val rejected = mutableListOf<String>()
            while (true) {
                val next = store.all().firstOrNull() ?: break
                when (val r = sender.send(next)) {
                    is SendResult.Sent -> {
                        store.delete(next.seq)
                        onSent(next, r)
                        sent++
                    }
                    is SendResult.Rejected -> {
                        store.delete(next.seq)
                        rejected += r.reason
                    }
                    SendResult.RetryLater -> {
                        store.bumpAttempts(next.seq)
                        return@withLock FlushResult(sent, rejected, store.all().size)
                    }
                }
            }
            FlushResult(sent, rejected, 0)
        }

    fun decodePatch(change: PendingChange): EntryPatch = json.decodeFromString(EntryPatch.serializer(), change.payload)
}
