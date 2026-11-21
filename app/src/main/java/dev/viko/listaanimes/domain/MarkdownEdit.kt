package dev.viko.listaanimes.domain

/**
 * Edições de markdown para a barra de formatação do editor de review.
 * Trabalham com texto + seleção, sem depender do Compose (testáveis).
 */
object MarkdownEdit {
    data class Result(val text: String, val selStart: Int, val selEnd: Int)

    /**
     * Envolve a seleção com [prefix] e [suffix]. Sem seleção, insere [placeholder] já selecionado,
     * para a pessoa só digitar por cima.
     */
    fun wrap(text: String, start: Int, end: Int, prefix: String, suffix: String, placeholder: String): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val inner = text.substring(s, e)
        // Já envolvido: tira a formatação (alterna).
        if (inner.isNotEmpty() && s >= prefix.length && e + suffix.length <= text.length &&
            text.substring(s - prefix.length, s) == prefix && text.substring(e, e + suffix.length) == suffix
        ) {
            val t = text.substring(0, s - prefix.length) + inner + text.substring(e + suffix.length)
            return Result(t, s - prefix.length, e - prefix.length)
        }
        val body = inner.ifEmpty { placeholder }
        val t = text.substring(0, s) + prefix + body + suffix + text.substring(e)
        return Result(t, s + prefix.length, s + prefix.length + body.length)
    }

    /** Põe [prefix] no começo de cada linha tocada pela seleção (títulos, citações, listas). */
    fun linePrefix(text: String, start: Int, end: Int, prefix: String): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val lineStart = text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0) 0 else if (it < 0) 0 else it + 1 }
        val block = text.substring(lineStart, e)
        val lines = block.split('\n')
        val allHave = lines.all { it.startsWith(prefix) }
        val changed = lines.joinToString("\n") { if (allHave) it.removePrefix(prefix) else prefix + it }
        val delta = changed.length - block.length
        val t = text.substring(0, lineStart) + changed + text.substring(e)
        val firstDelta = if (allHave) -prefix.length else prefix.length
        return Result(t, (s + firstDelta).coerceAtLeast(lineStart), e + delta)
    }

    /** Insere um bloco em linha própria (divisor, imagem, vídeo) no cursor. */
    fun insertBlock(text: String, at: Int, block: String, selectFrom: Int = block.length, selectTo: Int = block.length): Result {
        val i = at.coerceIn(0, text.length)
        val before = if (i > 0 && text[i - 1] != '\n') "\n\n" else if (i > 1 && text[i - 2] != '\n') "\n" else ""
        val after = if (i < text.length && text[i] != '\n') "\n\n" else "\n"
        val t = text.substring(0, i) + before + block + after + text.substring(i)
        val base = i + before.length
        return Result(t, base + selectFrom, base + selectTo)
    }

    fun words(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    /** Minutos de leitura, ~200 palavras por minuto. */
    fun readingMinutes(text: String): Int = ((words(text) + 199) / 200).coerceAtLeast(1)
}
