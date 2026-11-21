package dev.viko.listaanimes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.viko.listaanimes.i18n.AppLanguage
import dev.viko.listaanimes.i18n.I18n
import dev.viko.listaanimes.i18n.t
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Tradução no aparelho (ML Kit). Os textos do AniList vêm em inglês.
 * O markdown é preservado: só os trechos de texto entre marcações são traduzidos.
 */
object TextTranslator {
    fun target(): String? = when (I18n.lang) {
        AppLanguage.PT_BR -> TranslateLanguage.PORTUGUESE
        AppLanguage.ES -> TranslateLanguage.SPANISH
        AppLanguage.FR -> TranslateLanguage.FRENCH
        AppLanguage.DE -> TranslateLanguage.GERMAN
        AppLanguage.IT -> TranslateLanguage.ITALIAN
        AppLanguage.RU -> TranslateLanguage.RUSSIAN
        AppLanguage.JA -> TranslateLanguage.JAPANESE
        AppLanguage.ZH -> TranslateLanguage.CHINESE
        else -> null
    }

    private val TOKENS = Regex("""(~!|!~|~~~|__|\*\*|\[|\]\([^)]*\)|<[^>]+>|https?://\S+|img\d*%?\([^)]*\)|\n)""")

    suspend fun translate(text: String): String {
        val lang = target() ?: return text
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(lang).build())
        try {
            suspendCoroutine { c -> tr.downloadModelIfNeeded().addOnSuccessListener { c.resume(Unit) }.addOnFailureListener { c.resumeWithException(it) } }
            val out = StringBuilder()
            var last = 0
            suspend fun piece(s: String) {
                if (s.none { it.isLetter() }) { out.append(s); return }
                val lead = s.takeWhile { it.isWhitespace() }
                val trail = s.takeLastWhile { it.isWhitespace() }
                val core = s.trim()
                val done = suspendCoroutine<String> { c -> tr.translate(core).addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resumeWithException(it) } }
                out.append(lead).append(done).append(trail)
            }
            TOKENS.findAll(text).forEach { m ->
                piece(text.substring(last, m.range.first))
                out.append(m.value)
                last = m.range.last + 1
            }
            piece(text.substring(last))
            return out.toString()
        } finally { tr.close() }
    }
}

/** Botão "Traduzir" / "Ver original". [onText] recebe o texto a mostrar. */
@Composable
fun TranslateChip(original: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    if (TextTranslator.target() == null) return
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    var state by remember(original) { mutableStateOf(0) } // 0 original, 1 traduzindo, 2 traduzido, 3 erro
    var cached by remember(original) { mutableStateOf<String?>(null) }
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(p.accent.copy(alpha = 0.14f))
            .pressable(onClick = {
                when (state) {
                    1 -> {}
                    2 -> { state = 0; onText(original) }
                    else -> {
                        cached?.let { state = 2; onText(it); return@pressable }
                        state = 1
                        scope.launch {
                            try { val r = TextTranslator.translate(original); cached = r; state = 2; onText(r) } catch (e: Exception) { state = 3 }
                        }
                    }
                }
            })
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == 1) CircularProgressIndicator(Modifier.size(14.dp), color = p.accent, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.Translate, null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when (state) { 1 -> t("Traduzindo…"); 2 -> t("Ver original"); 3 -> t("Não foi possível traduzir"); else -> t("Traduzir") },
            style = MaterialTheme.typography.labelMedium, color = p.accent,
        )
    }
}
