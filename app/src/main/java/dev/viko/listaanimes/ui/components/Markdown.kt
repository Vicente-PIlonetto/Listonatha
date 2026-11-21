package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.PlexMono

/** Blocos do markdown do AniList (subconjunto). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String, val center: Boolean = false) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class Spoiler(val text: String) : MdBlock
    data class Image(val url: String, val widthHint: Int?) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val video = Regex("(?i)(youtube|webm)\\s*\\(([^)]*)\\)")
    private val img = Regex("(?i)img\\s*(\\d*)%?\\s*\\(([^)\\s]+)\\)")
    private val mdImg = Regex("!\\[[^\\]]*]\\(([^)\\s]+)\\)")
    private val htmlImg = Regex("(?i)<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>")
    private val htmlLink = Regex("(?is)<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>")
    private val htmlTag = Regex("<(?!br)[^>]+>", RegexOption.IGNORE_CASE)
    private val br = Regex("(?i)<br\\s*/?>")
    private val mention = Regex("(?<![\\w/@])@([A-Za-z0-9_]{2,20})\\b")

    fun parse(src: String): List<MdBlock> {
        val text = src.replace("\r\n", "\n")
            .replace(htmlImg) { "img(${it.groupValues[1]})" }
            .replace(mdImg) { "img(${it.groupValues[1]})" }
            .replace(htmlLink) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
            .replace(br, "\n").replace(htmlTag, "")
            .replace(video) { m ->
                val v = m.groupValues[2].trim()
                val url = if (m.groupValues[1].lowercase() == "youtube" && !v.startsWith("http")) "https://youtu.be/$v" else v
                t("[vídeo]({0})", url)
            }
            .replace(mention) { "[@${it.groupValues[1]}](https://anilist.co/user/${it.groupValues[1]})" }
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush(center: Boolean = false) {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim(), center)
            para.clear()
        }
        // Spoilers de várias linhas: ~! ... !~
        val spoilerSplit = Regex("~!([\\s\\S]*?)!~")
        var last = 0
        val chunks = mutableListOf<Pair<Boolean, String>>()
        spoilerSplit.findAll(text).forEach { m ->
            chunks += false to text.substring(last, m.range.first)
            chunks += true to m.groupValues[1].trim()
            last = m.range.last + 1
        }
        chunks += false to text.substring(last)
        for ((spoiler, chunk) in chunks) {
            if (spoiler) { flush(); out += MdBlock.Spoiler(chunk); continue }
            var center = false
            for (raw in chunk.split("\n")) {
                var line = raw.trimEnd()
                if (line.contains("~~~")) {
                    flush(center); center = !center
                    line = line.replace("~~~", "").trim()
                    if (line.isEmpty()) continue
                }
                var t = line.trim()
                // Imagens viram blocos próprios.
                if (img.containsMatchIn(t)) {
                    flush(center)
                    img.findAll(t).forEach { m -> out += MdBlock.Image(m.groupValues[2], m.groupValues[1].toIntOrNull()) }
                    t = t.replace(img, "").trim()
                    if (t.isEmpty()) continue
                }
                when {
                    t.isEmpty() -> flush(center)
                    t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flush(center); out += MdBlock.Rule }
                    t.startsWith("#") -> {
                        flush(center)
                        val level = t.takeWhile { it == '#' }.length.coerceIn(1, 5)
                        out += MdBlock.Heading(level, t.drop(level).trim())
                    }
                    t.startsWith(">") -> { flush(center); out += MdBlock.Quote(t.trimStart('>', ' ')) }
                    Regex("^[-*+]\\s+.*").matches(t) -> { flush(center); out += MdBlock.Bullet("•", t.drop(1).trim()) }
                    Regex("^\\d+[.)]\\s+.*").matches(t) -> {
                        flush(center)
                        val n = t.takeWhile { it.isDigit() }
                        out += MdBlock.Bullet("$n.", t.drop(n.length + 1).trim())
                    }
                    else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
                }
            }
            flush(center)
        }
        return out
    }

    private val inline = Regex(
        "\\[([^\\]]+)]\\(([^)\\s]+)\\)" +       // 1,2 link
            "|\\*\\*(.+?)\\*\\*|__(.+?)__" +      // 3,4 negrito
            "|~~(.+?)~~" +                        // 5 riscado
            "|\\*(.+?)\\*|(?<![\\w])_(.+?)_(?![\\w])" + // 6,7 itálico
            "|`([^`]+)`" +                        // 8 código
            "|(https?://\\S+)",                   // 9 URL solta
    )

    fun inline(text: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        for (m in inline.findAll(text)) {
            append(text.substring(i, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withLink(LinkAnnotation.Url(g[2], TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) { append(g[1]) }
                g[3].isNotEmpty() || g[4].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(g[3].ifEmpty { g[4] }, accent)) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[5]) }
                g[6].isNotEmpty() || g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(g[6].ifEmpty { g[7] }, accent)) }
                g[8].isNotEmpty() -> withStyle(SpanStyle(fontFamily = PlexMono)) { append(g[8]) }
                g[9].isNotEmpty() -> withLink(LinkAnnotation.Url(g[9], TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) { append(g[9]) }
            }
            i = m.range.last + 1
        }
        append(text.substring(i))
    }
}

@Composable
fun MarkdownView(source: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val blocks = remember(source) { MarkdownParser.parse(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    MarkdownParser.inline(b.text, p.accent),
                    style = when (b.level) { 1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall },
                )
                is MdBlock.Paragraph -> Text(
                    MarkdownParser.inline(b.text, p.accent),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = if (b.center) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(p.divider))
                    Text(MarkdownParser.inline(b.text, p.accent), style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.padding(start = 12.dp))
                }
                is MdBlock.Bullet -> Row {
                    Text(b.marker, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.width(22.dp))
                    Text(MarkdownParser.inline(b.text, p.accent), style = MaterialTheme.typography.bodyMedium)
                }
                is MdBlock.Spoiler -> {
                    var open by remember { mutableStateOf(false) }
                    Text(
                        if (open) MarkdownParser.inline(b.text, p.accent) else AnnotatedString(t("Spoiler, toque para ver")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (open) p.text else p.textTertiary,
                        modifier = Modifier.fillMaxWidth().background(p.surfaceAlt).clickable { open = !open }.padding(10.dp),
                    )
                }
                is MdBlock.Image -> {
                    val w = b.widthHint?.let { (it / 2).coerceIn(60, 400).dp }
                    coil3.compose.AsyncImage(
                        model = b.url, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = (if (w != null) Modifier.width(w) else Modifier.fillMaxWidth())
                            .heightIn(max = 480.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    )
                }
                MdBlock.Rule -> ThinDivider()
            }
        }
    }
}
