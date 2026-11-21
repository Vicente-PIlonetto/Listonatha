package dev.viko.listaanimes.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Estado do limite do AniList, lido dos cabeçalhos de cada resposta
 * (X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After). Nada de número fixo.
 */
class RateLimitState(private val nowMs: () -> Long = System::currentTimeMillis) {
    @Volatile var remaining: Int? = null; private set
    @Volatile var resetAtMs: Long? = null; private set
    @Volatile var blockedUntilMs: Long = 0; private set
    @Volatile var limit: Int? = null; private set

    /** Até quando o app está parado esperando o limite (para a tela avisar). null = não está esperando. */
    val waitingUntil = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    @Synchronized
    fun update(remainingHeader: String?, resetHeader: String?, retryAfterHeader: String?, code: Int, limitHeader: String? = null) {
        limitHeader?.toIntOrNull()?.let { limit = it }
        remainingHeader?.toIntOrNull()?.let { remaining = it }
        resetHeader?.toLongOrNull()?.let { resetAtMs = it * 1000 }
        if (code == 429) {
            val wait = retryAfterHeader?.toLongOrNull() ?: 60
            blockedUntilMs = nowMs() + wait * 1000
            remaining = 0
        }
    }

    /** Quanto esperar antes da próxima requisição (ms). */
    @Synchronized
    fun waitMs(): Long {
        val now = nowMs()
        if (blockedUntilMs > now) return blockedUntilMs - now
        val rem = remaining ?: return 0
        if (rem > 2) return 0
        val reset = resetAtMs
        if (reset != null && reset > now) return (reset - now).coerceAtMost(65_000)
        // Sem o horário de reset (o AniList só manda no 429): espaça as requisições
        // no ritmo do limite por minuto em vez de estourar e levar 429.
        val perMinute = limit ?: 30
        return 60_000L / perMinute.coerceAtLeast(1)
    }

    /** Depois de esperar, considera que a janela renovou até a próxima resposta dizer o contrário. */
    @Synchronized
    fun afterWait() {
        if (blockedUntilMs <= nowMs()) {
            remaining = null
        }
    }
}

class RateLimitInterceptor(
    private val state: RateLimitState,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val maxRetries: Int = 3,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            val wait = state.waitMs()
            if (wait > 0) {
                state.waitingUntil.value = System.currentTimeMillis() + wait
                try { sleep(wait) } finally { state.waitingUntil.value = null }
                state.afterWait()
            }
            val response = chain.proceed(chain.request())
            state.update(
                response.header("X-RateLimit-Remaining"),
                response.header("X-RateLimit-Reset"),
                response.header("Retry-After"),
                response.code,
                response.header("X-RateLimit-Limit"),
            )
            if (response.code == 429 && attempt < maxRetries) {
                attempt++
                response.close()
                continue
            }
            return response
        }
    }
}
