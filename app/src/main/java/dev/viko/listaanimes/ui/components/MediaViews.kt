package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.data.settings.Settings
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.ProgressRules
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono

val LocalSettings = staticCompositionLocalOf { Settings() }

/** Estado da minha lista para marcar os cards do Descobrir: mediaId → status. */
val LocalListStatus = staticCompositionLocalOf<Map<Int, ListStatus>> { emptyMap() }

/** Salva uma nota dada fora do Detalhe (ex.: direto na grade): mediaId, valor 0–100, nota especial. */
val LocalScoreSaver = staticCompositionLocalOf<(Int, Int, Int?) -> Unit> { { _, _, _ -> } }

/** Abre a folha de status rápido (toque longo em qualquer capa). */
val LocalQuickStatus = staticCompositionLocalOf<(Media) -> Unit> { {} }

fun nowEpoch() = System.currentTimeMillis() / 1000

/** Texto de progresso que "rola" para cima quando o número muda. */
@Composable
fun AnimatedProgressText(text: String, style: TextStyle, color: androidx.compose.ui.graphics.Color) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(220)) { it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)))
        },
        label = "progress",
    ) { Text(it, style = style, color = color) }
}

/** Linha da lista: capa pequena, título, "6/12 · 1 atrasado" e o "+1". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    entry: ListEntry,
    media: Media,
    special: Int?,
    showScore: Boolean,
    showPlusOne: Boolean,
    coverKey: String?,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onSetProgress: (Int) -> Unit,
    showStatus: Boolean = false,
) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val quick = LocalQuickStatus.current
    val behind = ProgressRules.episodesBehind(entry, media, nowEpoch())
    val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(media.coverColor)
    Row(
        Modifier.fillMaxWidth()
            .softHighlight(behind > 0, tint)
            .progressGlow(entry.progress, tint)
            .combinedClickable(onClick = onClick, onLongClick = { quick(media) })
            .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(behind > 0, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) { NewDot() }
        }
        Cover(media.coverMedium ?: media.coverLarge, 48.dp, placeholder = media.coverColor, adult = media.isAdult, sharedKey = coverKey)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title(s.titleLanguage), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStatus) {
                    StatusIcon(entry.status, media.kind, dev.viko.listaanimes.ui.theme.statusColor(entry.status), 14.dp)
                    Spacer(Modifier.width(6.dp))
                }
                AnimatedProgressText(ProgressRules.progressText(entry, media), Mono.style(13, androidx.compose.ui.text.font.FontWeight.Medium), tint)
                ProgressRules.behindText(behind)?.let {
                    Text(" · $it", style = Mono.style(13), color = p.textSecondary)
                }
                if (showScore && entry.scoreRaw > 0) {
                    Spacer(Modifier.weight(1f))
                    ScoreView(entry.scoreRaw, special, s.primaryScore, s.secondaryScore, ScoreSize.Small)
                }
            }
            val total = media.total?.takeIf { it > 0 }
            if (total != null) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(entry.progress / total.toFloat(), tint, height = 3.dp)
            }
        }
        if (showPlusOne) {
            Spacer(Modifier.width(14.dp))
            PlusOneButton(
                enabled = ProgressRules.canIncrement(entry, media),
                current = entry.progress,
                total = media.total,
                unit = if (media.kind == MediaKind.ANIME) t("Episódio") else t("Capítulo"),
                onIncrement = onIncrement,
                onSet = onSetProgress,
                color = tint,
            )
        }
    }
}

/** Célula de grade da minha lista: capa, título, progresso e "+1". */
@Composable
fun EntryGridCell(
    entry: ListEntry,
    media: Media,
    special: Int?,
    coverKey: String?,
    showPlusOne: Boolean,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onSetProgress: (Int) -> Unit,
) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val quick = LocalQuickStatus.current
    val behind = ProgressRules.episodesBehind(entry, media, nowEpoch())
    val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(media.coverColor)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        Column(Modifier.pressable(onClick = onClick, onLongClick = { quick(media) })) {
            Box(Modifier.progressGlow(entry.progress, tint)) {
                Cover(media.coverLarge ?: media.coverMedium, w, placeholder = media.coverColor, adult = media.isAdult, sharedKey = coverKey)
                if (behind > 0) Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(tint).padding(horizontal = 6.dp, vertical = 2.dp),
                ) { Text("+$behind", style = Mono.style(11, androidx.compose.ui.text.font.FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.Black) }
                media.total?.takeIf { it > 0 }?.let { total ->
                    ProgressBar(entry.progress / total.toFloat(), tint, Modifier.align(Alignment.BottomCenter).padding(6.dp), height = 4.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(media.title(s.titleLanguage), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedProgressText(ProgressRules.progressText(entry, media), Mono.style(12, androidx.compose.ui.text.font.FontWeight.Medium), tint)
                    if (s.gridScore) GridScoreChip(entry, media, special)
                    else if (entry.scoreRaw > 0) ScoreView(entry.scoreRaw, special, s.primaryScore, null, ScoreSize.Small)
                }
                if (showPlusOne) PlusOneButton(
                    enabled = ProgressRules.canIncrement(entry, media),
                    current = entry.progress, total = media.total,
                    unit = if (media.kind == MediaKind.ANIME) t("Episódio") else t("Capítulo"),
                    onIncrement = onIncrement, onSet = onSetProgress, color = tint,
                )
            }
        }
    }
}

/** Card do Descobrir: capa grande e texto embaixo, sem moldura. Toque longo muda o status. */
@Composable
fun PosterCard(media: Media, onClick: () -> Unit, width: Dp = 112.dp, sharedKey: String? = null) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val status = LocalListStatus.current[media.id]
    val quick = LocalQuickStatus.current
    Column(Modifier.width(width).pressable(onClick = onClick, onLongClick = { quick(media) })) {
        Cover(media.coverLarge ?: media.coverMedium, width, placeholder = media.coverColor, adult = media.isAdult, sharedKey = sharedKey)
        Spacer(Modifier.height(6.dp))
        Text(media.title(s.titleLanguage), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = p.text)
        val meta = listOfNotNull(
            Labels.format(media.format).takeIf { it.isNotEmpty() },
            media.averageScore?.let { "$it%" },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = Mono.style(11), color = p.textSecondary, maxLines = 1)
        if (status != null) {
            StatusLabel(status, media.kind)
        } else if (media.nextEpisode != null && media.nextAiringAt != null && media.nextAiringAt > nowEpoch()) {
            Text(t("EP {0} em {1}", media.nextEpisode, countdown(media.nextAiringAt - nowEpoch())), style = Mono.style(11), color = p.textTertiary, maxLines = 1)
        } else {
            media.popularity?.takeIf { it > 0 }?.let { Text(t("{0} na lista", compactNumber(it)), style = Mono.style(11), color = p.textTertiary, maxLines = 1) }
        }
    }
}

@Composable
fun PosterRow(items: List<Media>, keyPrefix: String, filter: Boolean = true, onClick: (Media, String) -> Unit) {
    // Respeita 18+ e gêneros bloqueados também em recomendações e relações.
    val st = LocalSettings.current
    val shown = remember(items, st.showAdult, st.blockedGenres) { if (!filter) items else items.filter { m -> (st.showAdult || !m.isAdult) && m.genres.none { it in st.blockedGenres } } }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(shown, key = { it.id }) { m ->
            val k = coverKey(keyPrefix, m.id)
            PosterCard(m, onClick = { onClick(m, k) }, sharedKey = k)
        }
    }
}

/** Linha de resultado (busca, listagens): capa, título e informações. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListRow(media: Media, sharedKey: String? = null, onClick: () -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val status = LocalListStatus.current[media.id]
    val quick = LocalQuickStatus.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { quick(media) }).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(media.coverMedium ?: media.coverLarge, 46.dp, placeholder = media.coverColor, adult = media.isAdult, sharedKey = sharedKey)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title(s.titleLanguage), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                Labels.format(media.format).takeIf { it.isNotEmpty() },
                media.seasonYear?.toString() ?: media.startYear?.toString(),
                media.episodes?.let { t("{0} ep.", it) } ?: media.chapters?.let { t("{0} cap.", it) },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Row {
                media.averageScore?.let { Text("$it%", style = Mono.style(12), color = p.textSecondary) }
                media.popularity?.let { Text(t("  {0} na lista", compactNumber(it)), style = Mono.style(12), color = p.textTertiary) }
            }
        }
        if (status != null) StatusIcon(status, media.kind, dev.viko.listaanimes.ui.theme.statusColor(status), 20.dp)
    }
}

/** Uma linha de grade com [columns] células iguais. */
@Composable
fun <T> GridRow(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { Box(Modifier.weight(1f)) { cell(it) } }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Card de grade para listagens do Descobrir. */
@Composable
fun PosterGridCell(media: Media, sharedKey: String?, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) { PosterCard(media, onClick, width = maxWidth, sharedKey = sharedKey) }
}


/** Nota na grade: toque abre o seletor (opção "Dar nota pela grade" nas Configurações). */
@Composable
private fun GridScoreChip(entry: ListEntry, media: Media, special: Int?) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val save = LocalScoreSaver.current
    var picker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        Modifier.padding(top = 2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(p.surfaceAlt).pressable(onClick = { picker = true }).padding(horizontal = 6.dp, vertical = 2.dp)
            .bounceOn(entry.scoreRaw),
    ) {
        if (entry.scoreRaw > 0) ScoreView(entry.scoreRaw, special, s.primaryScore, null, ScoreSize.Small)
        else Text(t("Dar nota"), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
    }
    if (picker) ScorePickerSheet(
        initialRaw = entry.scoreRaw, initialSpecial = special, system = s.primaryScore,
        onDismiss = { picker = false },
        onSave = { save(media.id, it.raw, it.special); picker = false },
    )
}
