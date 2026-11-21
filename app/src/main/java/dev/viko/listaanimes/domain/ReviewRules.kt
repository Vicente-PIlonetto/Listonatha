package dev.viko.listaanimes.domain

/**
 * Regras de review conforme o schema do AniList (SaveReview):
 * summary: mín. 20, máx. 120; body: mín. 2600; score: 0–100.
 */
object ReviewRules {
    const val SUMMARY_MIN = 20
    const val SUMMARY_MAX = 120
    const val BODY_MIN = 2600
    const val SCORE_MIN = 0
    const val SCORE_MAX = 100

    data class Check(
        val summaryLength: Int,
        val bodyLength: Int,
        val summaryMissing: Int,
        val summaryExcess: Int,
        val bodyMissing: Int,
        val scoreValid: Boolean,
    ) {
        val summaryOk get() = summaryMissing == 0 && summaryExcess == 0
        val bodyOk get() = bodyMissing == 0
        val canPublish get() = summaryOk && bodyOk && scoreValid
    }

    fun length(text: String): Int = text.trim().let { it.codePointCount(0, it.length) }

    fun check(summary: String, body: String, score: Int?): Check {
        val s = length(summary)
        val b = length(body)
        return Check(
            summaryLength = s,
            bodyLength = b,
            summaryMissing = (SUMMARY_MIN - s).coerceAtLeast(0),
            summaryExcess = (s - SUMMARY_MAX).coerceAtLeast(0),
            bodyMissing = (BODY_MIN - b).coerceAtLeast(0),
            scoreValid = score != null && score in SCORE_MIN..SCORE_MAX,
        )
    }
}
