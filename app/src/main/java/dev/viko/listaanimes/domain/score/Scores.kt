package dev.viko.listaanimes.domain.score

import dev.viko.listaanimes.i18n.t
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Conversão entre o valor guardado (0–100, o scoreRaw do AniList) e cada sistema de nota.
 * 0 sempre significa "sem nota".
 */
object Scores {

    /** Mostra um valor 0–100 no sistema escolhido. [special] = posição da nota especial guardada (1–12), se houver. */
    fun display(raw: Int, system: ScoreSystem, special: Int? = null): ScoreDisplay {
        if (raw <= 0) return ScoreDisplay.None
        val r = raw.coerceAtMost(100)
        return when (system) {
            ScoreSystem.POINT_100 -> ScoreDisplay.Text("$r")
            ScoreSystem.POINT_10_DECIMAL -> ScoreDisplay.Text(decimal(r))
            ScoreSystem.POINT_10 -> ScoreDisplay.Text("${point10(r)}")
            ScoreSystem.STARS_5 -> ScoreDisplay.Stars(starHalves(r))
            ScoreSystem.SMILEY_3 -> smiley3(r).let { ScoreDisplay.Face(it.first, it.second) }
            ScoreSystem.POINT_7 -> ScoreDisplay.Text("${Bands.of(r)!!.value}")
            ScoreSystem.SMILEY_7 -> Bands.of(r)!!.let { ScoreDisplay.Face(it.value, it.face) }
            ScoreSystem.LETTERS -> letterDisplay(r, special)
        }
    }

    /** Versão em texto (para o sistema secundário, acessibilidade e backup). */
    fun text(raw: Int, system: ScoreSystem, special: Int? = null): String = when (val d = display(raw, system, special)) {
        ScoreDisplay.None -> "–"
        is ScoreDisplay.Text -> d.text
        is ScoreDisplay.Stars -> starsText(d.halves)
        is ScoreDisplay.Face -> d.label
    }

    fun decimal(raw: Int): String {
        val v = raw / 10
        val d = raw % 10
        return "$v,$d"
    }

    fun point10(raw: Int): Int = (raw / 10.0).roundHalfUp().coerceIn(1, 10)

    fun starHalves(raw: Int): Int = (raw / 10.0).roundHalfUp().coerceIn(1, 10)

    fun starsText(halves: Int): String = if (halves % 2 == 0) "${halves / 2}" else "${halves / 2},5"

    private val smileyValues = listOf(35 to (2 to t("Triste")), 60 to (4 to t("Neutra")), 85 to (6 to t("Feliz")))

    /** Nível (escala de 7) e rótulo da carinha mais próxima (empate: a menor). */
    fun smiley3(raw: Int): Pair<Int, String> =
        smileyValues.minWith(compareBy<Pair<Int, Pair<Int, String>>> { abs(it.first - raw) }.thenBy { it.first }).second

    fun smiley3Raw(raw: Int): Int =
        smileyValues.minWith(compareBy<Pair<Int, Pair<Int, String>>> { abs(it.first - raw) }.thenBy { it.first }).first

    private fun letterDisplay(raw: Int, special: Int?): ScoreDisplay {
        val stored = special?.let { SpecialGrade.fromPosition(it) }
        if (stored != null && stored.compressed == raw) return ScoreDisplay.Text(stored.label, special = true)
        SpecialGrade.tierFor(raw)?.let { return ScoreDisplay.Text(it.name, special = true) }
        return ScoreDisplay.Text(LetterGrade.nearest(raw).label)
    }

    /** Opções para escolher a nota em cada sistema (valor gravado + rótulo). */
    fun options(system: ScoreSystem): List<ScoreOption> = when (system) {
        ScoreSystem.POINT_100 -> (1..100).map { ScoreOption(it, "$it") }
        ScoreSystem.POINT_10_DECIMAL -> (1..100).map { ScoreOption(it, decimal(it)) }
        ScoreSystem.POINT_10 -> (1..10).map { ScoreOption(it * 10, "$it") }
        ScoreSystem.STARS_5 -> (1..10).map { ScoreOption(it * 10, starsText(it)) }
        ScoreSystem.SMILEY_3 -> smileyValues.map { ScoreOption(it.first, it.second.second) }
        ScoreSystem.POINT_7 -> Bands.all.reversed().map { ScoreOption(it.mid, "${it.value}") }
        ScoreSystem.SMILEY_7 -> Bands.all.reversed().map { ScoreOption(it.mid, it.face) }
        ScoreSystem.LETTERS -> LetterGrade.all.map { ScoreOption(it.raw, it.label) } +
            SpecialGrade.entries.map { ScoreOption(it.compressed, it.label, it) }
    }

    /**
     * Normaliza um valor 0–100 para o valor que o sistema grava ao escolher a opção mais próxima.
     * Ex.: 72 em letras → 71 (B−−); 72 em 0–7 → 77 (5).
     */
    fun snap(raw: Int, system: ScoreSystem): Int {
        if (raw <= 0) return 0
        val r = raw.coerceAtMost(100)
        return when (system) {
            ScoreSystem.POINT_100, ScoreSystem.POINT_10_DECIMAL -> r
            ScoreSystem.POINT_10 -> point10(r) * 10
            ScoreSystem.STARS_5 -> starHalves(r) * 10
            ScoreSystem.SMILEY_3 -> smiley3Raw(r)
            ScoreSystem.POINT_7, ScoreSystem.SMILEY_7 -> Bands.of(r)!!.mid
            ScoreSystem.LETTERS -> if (r >= 95) r else LetterGrade.nearest(r).raw
        }
    }

    /** Chave de ordenação por nota: notas especiais ficam acima do valor comprimido correspondente. */
    fun sortKey(raw: Int, special: Int?): Int {
        val pos = special?.takeIf { s -> SpecialGrade.fromPosition(s)?.compressed == raw } ?: 0
        return raw * 100 + pos
    }

    private fun Double.roundHalfUp(): Int = (this + 1e-9).roundToInt()
}

/** Regras das notas especiais (guardadas localmente, comprimidas para o AniList). */
object SpecialScores {
    /** Valor enviado ao AniList para uma nota especial. */
    fun compress(position: Int): Int = requireNotNull(SpecialGrade.fromPosition(position)) { t("posição {0}", position) }.compressed

    /**
     * Ao receber a nota do AniList: se não bater mais com a nota especial guardada
     * (mudou no site), a especial é descartada e vale a do AniList.
     * Retorna a posição que continua válida ou null.
     */
    fun reconcile(storedPosition: Int?, remoteRaw: Int): Int? {
        val grade = storedPosition?.let { SpecialGrade.fromPosition(it) } ?: return null
        return if (grade.compressed == remoteRaw) storedPosition else null
    }
}
