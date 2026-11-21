package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.ReviewDraft
import dev.viko.listaanimes.domain.ReviewRules
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MarkdownView
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewEditorViewModel(private val c: AppContainer, val mediaId: Int) : ViewModel() {
    val draft = MutableStateFlow<ReviewDraft?>(null)
    val media = c.media.observeMedia(mediaId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val publishing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            // Rascunho salvo > review publicada > nova, com a minha nota da obra.
            val saved = c.reviews.draft(mediaId)
            val published = c.reviews.mineFor(mediaId)
            draft.value = saved
                ?: published?.let { ReviewDraft(mediaId, it.id, it.summary, it.body, it.score, it.isPrivate) }
                ?: ReviewDraft(mediaId, score = c.db.entries().get(mediaId)?.scoreRaw ?: 0)
        }
    }

    suspend fun myScore(): Int? = c.db.entries().get(mediaId)?.scoreRaw

    fun update(d: ReviewDraft) {
        draft.value = d
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(500); c.reviews.saveDraft(d) }
    }

    fun publish() = viewModelScope.launch {
        val d = draft.value ?: return@launch
        publishing.value = true
        try {
            c.reviews.saveDraft(d)
            c.reviews.publish(d)
            done.value = true
        } catch (e: Exception) {
            error.value = (e as? dev.viko.listaanimes.data.api.ApiException)?.userMessage
                ?: t("Não foi possível publicar. O rascunho está salvo.")
        } finally { publishing.value = false }
    }

    fun delete() = viewModelScope.launch {
        val id = draft.value?.reviewId ?: return@launch
        try {
            c.reviews.delete(id)
            c.reviews.discardDraft(mediaId)
            done.value = true
        } catch (e: Exception) {
            error.value = t("Não foi possível apagar a review. Tente de novo.")
        }
    }
}

@Composable
fun ReviewEditorScreen(nav: Nav, mediaId: Int) {
    val vm = appViewModel(key = "review-$mediaId") { ReviewEditorViewModel(it, mediaId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) nav.back() }
    val d = draft
    if (d == null) {
        Column(Modifier.fillMaxSize()) { TopBar(t("Review"), onBack = nav::back); dev.viko.listaanimes.ui.components.SkeletonRows(4) }
        return
    }
    // O editor guarda o texto com cursor/seleção; o rascunho é atualizado a cada mudança.
    androidx.compose.runtime.key(d.mediaId) { ReviewEditor(vm, nav, d) }
}

private enum class Fmt(val label: String) {
    BOLD(t("Negrito")), ITALIC(t("Itálico")), STRIKE(t("Riscado")), HEADING(t("Título")), QUOTE(t("Citação")), LIST(t("Lista")),
    SPOILER(t("Spoiler")), LINK(t("Link")), IMAGE(t("Imagem")), CENTER(t("Centralizar")), RULE(t("Divisor")),
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReviewEditor(vm: ReviewEditorViewModel, nav: Nav, initial: ReviewDraft) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val publishing by vm.publishing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val d = draft ?: initial
    var body by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial.body)) }
    var preview by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    // Desfazer/refazer: fotos do texto a cada pausa ou formatação.
    val undo = remember { ArrayDeque<androidx.compose.ui.text.input.TextFieldValue>() }
    val redo = remember { ArrayDeque<androidx.compose.ui.text.input.TextFieldValue>() }
    var lastSnap by remember { mutableStateOf(0L) }
    var undoTick by remember { mutableStateOf(0) }
    val check = ReviewRules.check(d.summary, body.text, d.score)
    val imeVisible = WindowInsets.isImeVisible
    val myScore by produceState<Int?>(null) { value = vm.myScore() }

    fun setBody(v: androidx.compose.ui.text.input.TextFieldValue, force: Boolean = false) {
        if (v.text != body.text) {
            val now = System.currentTimeMillis()
            if (force || now - lastSnap > 1500) {
                undo.addLast(body); if (undo.size > 60) undo.removeFirst()
                redo.clear(); lastSnap = now; undoTick++
            }
            vm.update(d.copy(body = v.text)); savedAt = now
        }
        body = v
    }

    fun apply(r: dev.viko.listaanimes.domain.MarkdownEdit.Result) =
        setBody(androidx.compose.ui.text.input.TextFieldValue(r.text, androidx.compose.ui.text.TextRange(r.selStart, r.selEnd)), force = true)

    fun format(f: Fmt) {
        val txt = body.text; val a = body.selection.min; val b = body.selection.max
        val E = dev.viko.listaanimes.domain.MarkdownEdit
        apply(when (f) {
            Fmt.BOLD -> E.wrap(txt, a, b, "__", "__", t("negrito"))
            Fmt.ITALIC -> E.wrap(txt, a, b, "_", "_", t("itálico"))
            Fmt.STRIKE -> E.wrap(txt, a, b, "~~", "~~", t("riscado"))
            Fmt.HEADING -> E.linePrefix(txt, a, b, "## ")
            Fmt.QUOTE -> E.linePrefix(txt, a, b, "> ")
            Fmt.LIST -> E.linePrefix(txt, a, b, "- ")
            Fmt.SPOILER -> E.wrap(txt, a, b, "~!", "!~", t("spoiler"))
            Fmt.LINK -> E.wrap(txt, a, b, "[", "](https://)", t("texto"))
            Fmt.IMAGE -> E.insertBlock(txt, b, "img(https://)", 4, 12)
            Fmt.CENTER -> E.wrap(txt, a, b, "~~~", "~~~", t("centralizado"))
            Fmt.RULE -> E.insertBlock(txt, b, "---")
        })
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (d.reviewId == null) t("Escrever review") else t("Editar review"), onBack = nav::back) {
            androidx.compose.material3.IconButton(onClick = { preview = !preview }) {
                androidx.compose.material3.Icon(
                    if (preview) androidx.compose.material.icons.Icons.Outlined.EditNote else androidx.compose.material.icons.Icons.Outlined.Visibility,
                    if (preview) t("Voltar a escrever") else t("Visualizar"), tint = p.accent,
                )
            }
        }
        error?.let { MessageBar(it) }
        if (preview) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.summary.ifBlank { t("Sem resumo") }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${d.score}/100", style = Mono.style(14), color = p.accent)
                }
                Spacer(Modifier.height(12.dp))
                if (body.text.isBlank()) Text(t("Sem texto"), color = p.textTertiary) else MarkdownView(body.text)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                androidx.compose.animation.AnimatedVisibility(!focusMode) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        media?.let { m ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                dev.viko.listaanimes.ui.components.Cover(m.coverMedium ?: m.coverLarge, 34.dp, placeholder = m.coverColor, adult = m.isAdult)
                                Spacer(Modifier.width(12.dp))
                                Text(m.title(s.titleLanguage), style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        RulesCard(check)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = d.summary, onValueChange = { vm.update(d.copy(summary = it.replace("\n", " "))); savedAt = System.currentTimeMillis() },
                            label = { Text(t("Resumo")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            supportingText = { Counter(summaryCounter(check), ok = check.summaryOk) },
                            isError = check.summaryExcess > 0, maxLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        )
                        ScoreCard(d.score, myScore) { vm.update(d.copy(score = it)) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(t("Privada"), style = MaterialTheme.typography.bodyLarge)
                                Text(t("Só você vê no AniList."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            }
                            Switch(checked = d.isPrivate, onCheckedChange = { vm.update(d.copy(isPrivate = it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.surfaceAlt, uncheckedThumbColor = p.textTertiary))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                // Texto: campo grande, letra confortável para escrever no celular.
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (body.text.isEmpty()) Text(
                        t("Escreva a review…"),
                        style = MaterialTheme.typography.bodyLarge, color = p.textTertiary,
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = body, onValueChange = { setBody(it) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text, lineHeight = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (focusMode) 480.dp else 300.dp),
                    )
                }
                Text(
                    t("{0} palavras · ~{1} min de leitura · ", dev.viko.listaanimes.domain.MarkdownEdit.words(body.text), dev.viko.listaanimes.domain.MarkdownEdit.readingMinutes(body.text)) +
                        if (savedAt != null) t("rascunho salvo") else t("rascunho salvo automaticamente"),
                    style = Mono.style(11), color = p.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (d.reviewId != null && !focusMode) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(t("Apagar review"), color = p.textSecondary) }
                }
                Spacer(Modifier.height(12.dp))
            }
            // Barra de formatação, sempre acima do teclado.
            FormatBar(
                canUndo = undoTick >= 0 && undo.isNotEmpty(), canRedo = undoTick >= 0 && redo.isNotEmpty(), focusMode = focusMode,
                onUndo = { undo.removeLastOrNull()?.let { prev -> redo.addLast(body); body = prev; vm.update(d.copy(body = prev.text)); undoTick++ } },
                onRedo = { redo.removeLastOrNull()?.let { next -> undo.addLast(body); body = next; vm.update(d.copy(body = next.text)); undoTick++ } },
                onFocus = { focusMode = !focusMode },
                onFormat = ::format,
            )
        }
        if (!imeVisible) {
            ThinDivider()
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !check.summaryOk -> t("Ajuste o resumo para publicar")
                        !check.bodyOk -> t("Faltam {0} caracteres", check.bodyMissing)
                        else -> t("Pronta para publicar")
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (check.canPublish) p.accent else p.textSecondary, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = vm::publish,
                    enabled = check.canPublish && !publishing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                ) { Text(if (publishing) t("Publicando…") else t("Publicar")) }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(t("Apagar review?")) },
        text = { Text(t("A review será removida do AniList. Não dá para desfazer.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(t("Apagar")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancelar")) } },
        shape = RoundedCornerShape(16.dp),
    )
}

/** Progresso das regras do AniList: resumo 20–120 e texto com 2600+ caracteres. */
@Composable
private fun RulesCard(check: ReviewRules.Check) {
    val p = AppTheme.palette
    val ok = androidx.compose.ui.graphics.Color(0xFF4CB782)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RuleLine(t("Resumo"), "${check.summaryLength}/${ReviewRules.SUMMARY_MAX}", check.summaryOk,
            (check.summaryLength / ReviewRules.SUMMARY_MIN.toFloat()).coerceAtMost(1f), if (check.summaryOk) ok else p.accent)
        RuleLine(t("Texto"), "${check.bodyLength}/${ReviewRules.BODY_MIN}", check.bodyOk,
            (check.bodyLength / ReviewRules.BODY_MIN.toFloat()).coerceAtMost(1f), if (check.bodyOk) ok else p.accent)
    }
}

@Composable
private fun RuleLine(label: String, value: String, ok: Boolean, fraction: Float, color: androidx.compose.ui.graphics.Color) {
    val p = AppTheme.palette
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (ok) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.CheckCircle, null, tint = color, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            Text(value, style = Mono.style(12), color = if (ok) color else p.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        dev.viko.listaanimes.ui.components.ProgressBar(fraction, color, height = 5.dp)
    }
}

@Composable
private fun ScoreCard(score: Int, myScore: Int?, onChange: (Int) -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Nota da review"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = { onChange((score - 1).coerceAtLeast(0)) }, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Remove, t("Menos"))
            }
            dev.viko.listaanimes.ui.components.AnimatedProgressText("$score", Mono.style(22, androidx.compose.ui.text.font.FontWeight.SemiBold), p.accent)
            Text("/100", style = Mono.style(13), color = p.textSecondary)
            androidx.compose.material3.IconButton(onClick = { onChange((score + 1).coerceAtMost(100)) }, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Add, t("Mais"))
            }
        }
        androidx.compose.material3.Slider(
            value = score.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.divider),
        )
        if (myScore != null && myScore > 0 && myScore != score) Text(
            t("Usar a minha nota da obra ({0})", myScore),
            style = MaterialTheme.typography.labelMedium, color = p.accent,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(myScore) }.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun FormatBar(
    canUndo: Boolean, canRedo: Boolean, focusMode: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, onFocus: () -> Unit, onFormat: (Fmt) -> Unit,
) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxWidth().background(p.background)) {
        ThinDivider()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FmtButton(Icons.AutoMirrored.Outlined.Undo, t("Desfazer"), enabled = canUndo, onClick = onUndo)
            FmtButton(Icons.AutoMirrored.Outlined.Redo, t("Refazer"), enabled = canRedo, onClick = onRedo)
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(p.divider))
            FmtButton(Icons.Outlined.FormatBold, Fmt.BOLD.label) { onFormat(Fmt.BOLD) }
            FmtButton(Icons.Outlined.FormatItalic, Fmt.ITALIC.label) { onFormat(Fmt.ITALIC) }
            FmtButton(Icons.Outlined.Title, Fmt.HEADING.label) { onFormat(Fmt.HEADING) }
            FmtButton(Icons.Outlined.VisibilityOff, Fmt.SPOILER.label) { onFormat(Fmt.SPOILER) }
            FmtButton(Icons.Outlined.FormatQuote, Fmt.QUOTE.label) { onFormat(Fmt.QUOTE) }
            FmtButton(Icons.AutoMirrored.Outlined.FormatListBulleted, Fmt.LIST.label) { onFormat(Fmt.LIST) }
            FmtButton(Icons.Outlined.Link, Fmt.LINK.label) { onFormat(Fmt.LINK) }
            FmtButton(Icons.Outlined.Image, Fmt.IMAGE.label) { onFormat(Fmt.IMAGE) }
            FmtButton(Icons.Outlined.FormatAlignCenter, Fmt.CENTER.label) { onFormat(Fmt.CENTER) }
            FmtButton(Icons.Outlined.FormatStrikethrough, Fmt.STRIKE.label) { onFormat(Fmt.STRIKE) }
            FmtButton(Icons.Outlined.HorizontalRule, Fmt.RULE.label) { onFormat(Fmt.RULE) }
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(p.divider))
            FmtButton(if (focusMode) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull, if (focusMode) t("Mostrar campos") else t("Só o texto"), active = focusMode, onClick = onFocus)
        }
    }
}

@Composable
private fun FmtButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val p = AppTheme.palette
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        androidx.compose.material3.Icon(icon, label, tint = when { !enabled -> p.textTertiary.copy(alpha = 0.5f); active -> p.accent; else -> p.text }, modifier = Modifier.size(22.dp))
    }
}

fun summaryCounter(c: ReviewRules.Check): String = when {
    c.summaryMissing > 0 -> t("{0}/{1} · faltam {2} para o mínimo de {3}", c.summaryLength, ReviewRules.SUMMARY_MAX, c.summaryMissing, ReviewRules.SUMMARY_MIN)
    c.summaryExcess > 0 -> t("{0}/{1} · {2} acima do máximo", c.summaryLength, ReviewRules.SUMMARY_MAX, c.summaryExcess)
    else -> "${c.summaryLength}/${ReviewRules.SUMMARY_MAX}"
}

fun bodyCounter(c: ReviewRules.Check): String = when {
    c.bodyMissing > 0 -> t("{0} · faltam {1} para o mínimo de {2}", c.bodyLength, c.bodyMissing, ReviewRules.BODY_MIN)
    else -> t("{0} caracteres", c.bodyLength)
}

@Composable
private fun Counter(text: String, ok: Boolean) {
    Text(text, style = Mono.style(12), color = if (ok) AppTheme.palette.textSecondary else AppTheme.palette.textTertiary)
}

class ReviewReaderViewModel(c: AppContainer, id: Int) : ViewModel() {
    val review = c.reviews.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun ReviewReaderScreen(nav: Nav, id: Int) {
    val vm = appViewModel(key = "read-$id") { ReviewReaderViewModel(it, id) }
    val r by vm.review.collectAsStateWithLifecycle()
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Review"), onBack = nav::back) {
            r?.takeIf { it.mine }?.let { rv -> TextButton(onClick = { nav.reviewEditor(rv.mediaId) }) { Text(t("Editar"), color = p.text) } }
        }
        val rv = r ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (rv.mine) t("Minha review") else rv.userName.orEmpty(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${rv.score}/100", style = Mono.style(14), color = p.textSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(rv.summary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            MarkdownView(rv.body)
            Spacer(Modifier.height(32.dp))
        }
    }
}

class MyReviewsViewModel(private val c: AppContainer) : ViewModel() {
    val reviews = c.reviews.observeMine().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val error = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch { try { c.reviews.refreshMine() } catch (e: Exception) { error.value = e.toUserMessage() } }
    }
}

@Composable
fun MyReviewsScreen(nav: Nav) {
    val vm = appViewModel { MyReviewsViewModel(it) }
    val list by vm.reviews.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Minhas reviews"), onBack = nav::back)
        error?.let { MessageBar(it) }
        LazyColumn {
            val l = list ?: return@LazyColumn
            if (l.isEmpty()) item { EmptyState(t("Nenhuma review.")) }
            items(l, key = { it.review.id }) { row ->
                Column(Modifier.fillMaxWidth()) {
                    row.media?.let { Text(it.title(s.titleLanguage), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
                    ReviewRow(row.review, mine = true) { nav.reviewRead(row.review.id) }
                    ThinDivider()
                }
            }
        }
    }
}
