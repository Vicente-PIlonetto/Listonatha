package dev.viko.listaanimes.domain.score

import dev.viko.listaanimes.i18n.t
import kotlinx.serialization.Serializable

@Serializable
enum class ScoreSystem(val label: String) {
    POINT_100("0–100"),
    POINT_10_DECIMAL(t("0–10 com decimal")),
    POINT_10("0–10"),
    STARS_5(t("5 estrelas")),
    SMILEY_3(t("3 carinhas")),
    LETTERS(t("Letras")),
    POINT_7("0–7"),
    SMILEY_7(t("7 carinhas"));

    companion object {
        fun parse(name: String?): ScoreSystem? = entries.firstOrNull { it.name == name }

        /** Formato de nota que a pessoa usa no AniList (mediaListOptions.scoreFormat). */
        fun fromAniList(format: String?): ScoreSystem? = when (format) {
            "POINT_100" -> POINT_100
            "POINT_10_DECIMAL" -> POINT_10_DECIMAL
            "POINT_10" -> POINT_10
            "POINT_5" -> STARS_5
            "POINT_3" -> SMILEY_3
            else -> null
        }
    }
}

/** Como uma nota aparece na tela. */
sealed interface ScoreDisplay {
    data object None : ScoreDisplay

    /** [special] = nota especial (acima de A++), mostrada com peso maior e na cor de destaque. */
    data class Text(val text: String, val special: Boolean = false) : ScoreDisplay

    /** Estrelas em meias unidades: 7 = 3,5 estrelas. */
    data class Stars(val halves: Int) : ScoreDisplay

    /** [level] de 1 a 7 na escala das 7 carinhas (as 3 carinhas usam os níveis 2, 4 e 6). */
    data class Face(val level: Int, val label: String) : ScoreDisplay
}

/** Uma opção que posso escolher ao avaliar. */
data class ScoreOption(val raw: Int, val label: String, val special: SpecialGrade? = null)
