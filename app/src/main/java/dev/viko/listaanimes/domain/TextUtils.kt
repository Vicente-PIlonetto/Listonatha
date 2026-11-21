package dev.viko.listaanimes.domain

object TextUtils {
    private val br = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</p>")
    private val tag = Regex("<[^>]+>")
    private val numericEntity = Regex("&#(x?[0-9a-fA-F]+);")
    private val manyNewlines = Regex("\n{3,}")
    private val entities = mapOf(
        "&amp;" to "&", "&quot;" to "\"", "&apos;" to "'", "&lt;" to "<",
        "&gt;" to ">", "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
    )

    /** Remove tags HTML da sinopse do AniList, preservando quebras de linha. */
    fun stripHtml(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s = input.replace("\r\n", "\n")
        // O AniList manda "<br>\n" — vira uma quebra só.
        s = s.replace(Regex("(?i)<br\\s*/?>\\n?"), "\n")
        s = s.replace(br, "\n").replace(blockEnd, "\n\n").replace(tag, "")
        entities.forEach { (k, v) -> s = s.replace(k, v) }
        s = numericEntity.replace(s) { m ->
            val v = m.groupValues[1]
            val code = if (v.startsWith("x") || v.startsWith("X")) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        return s.replace(manyNewlines, "\n\n").trim()
    }
}
