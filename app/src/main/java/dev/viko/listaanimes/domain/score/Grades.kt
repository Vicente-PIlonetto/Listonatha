package dev.viko.listaanimes.domain.score

/** Faixas base usadas por 0–7 e pelas 7 carinhas. */
object Bands {
    data class Band(val value: Int, val min: Int, val max: Int, val mid: Int, val face: String)

    val all = listOf(
        Band(7, 95, 100, 97, "Adorei"),
        Band(6, 85, 94, 89, "Gostei muito"),
        Band(5, 70, 84, 77, "Gostei"),
        Band(4, 55, 69, 62, "Ok"),
        Band(3, 40, 54, 47, "Não gostei"),
        Band(2, 20, 39, 29, "Ruim"),
        Band(1, 1, 19, 10, "Detestei"),
    )

    /** 0 = sem nota. */
    fun of(raw: Int): Band? = if (raw <= 0) null else all.first { raw in it.min..it.max || (raw > 100 && it.value == 7) }

    fun byValue(value: Int): Band? = all.firstOrNull { it.value == value }
}

enum class LetterModifier(val suffix: String) {
    MINUS2("−−"), MINUS1("−"), NORMAL(""), PLUS1("+"), PLUS2("++");
}

/** Letras de F a A, cada uma com cinco níveis. */
enum class Letter(val values: IntArray) {
    F(intArrayOf(2, 6, 10, 14, 18)),
    E(intArrayOf(21, 25, 29, 33, 37)),
    D(intArrayOf(41, 44, 47, 50, 53)),
    C(intArrayOf(56, 59, 62, 65, 68)),
    B(intArrayOf(71, 74, 77, 80, 83)),
    A(intArrayOf(85, 87, 89, 92, 94));

    fun value(mod: LetterModifier): Int = values[mod.ordinal]
    fun label(mod: LetterModifier): String = name + mod.suffix
}

data class LetterGrade(val letter: Letter, val modifier: LetterModifier) {
    val raw: Int get() = letter.value(modifier)
    val label: String get() = letter.label(modifier)

    companion object {
        val all: List<LetterGrade> = Letter.entries.flatMap { l -> LetterModifier.entries.map { LetterGrade(l, it) } }

        /** Grau mais próximo de um valor 0–100 (empate: o menor, para bater com as faixas base). */
        fun nearest(raw: Int): LetterGrade = all.minWith(compareBy<LetterGrade> { kotlin.math.abs(it.raw - raw) }.thenBy { it.raw })
    }
}

/** Família das notas especiais (S, SS, SSS). */
enum class SpecialTier { S, SS, SSS }

/**
 * As 12 notas especiais acima do A++, em ordem crescente.
 * [position] vai de 1 (S) a 12 (SSS+++).
 */
enum class SpecialGrade(val label: String, val tier: SpecialTier, val plus: Int) {
    S("S", SpecialTier.S, 0), S1("S+", SpecialTier.S, 1), S2("S++", SpecialTier.S, 2), S3("S+++", SpecialTier.S, 3),
    SS("SS", SpecialTier.SS, 0), SS1("SS+", SpecialTier.SS, 1), SS2("SS++", SpecialTier.SS, 2), SS3("SS+++", SpecialTier.SS, 3),
    SSS("SSS", SpecialTier.SSS, 0), SSS1("SSS+", SpecialTier.SSS, 1), SSS2("SSS++", SpecialTier.SSS, 2), SSS3("SSS+++", SpecialTier.SSS, 3);

    val position: Int get() = ordinal + 1

    /** Valor enviado ao AniList: S/S+ = 95, S++/S+++ = 96, SS/SS+ = 97, ..., SSS++/SSS+++ = 100. */
    val compressed: Int get() = 95 + ordinal / 2

    companion object {
        fun fromPosition(position: Int): SpecialGrade? = entries.getOrNull(position - 1)
        fun of(tier: SpecialTier, plus: Int): SpecialGrade = entries.first { it.tier == tier && it.plus == plus }

        /** Sem nota especial guardada, um valor 95–100 aparece como a base da família. */
        fun tierFor(raw: Int): SpecialTier? = when (raw) {
            95, 96 -> SpecialTier.S
            97, 98 -> SpecialTier.SS
            in 99..100 -> SpecialTier.SSS
            else -> null
        }
    }
}
