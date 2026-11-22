package dev.viko.listaanimes.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.repo.KindStats
import dev.viko.listaanimes.data.repo.UserStats
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.score.ScoreBuckets
import dev.viko.listaanimes.i18n.t
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.FaceIcon
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.ScoreSize
import dev.viko.listaanimes.ui.components.ScoreView
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.StatusIcon
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.faceColor
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import dev.viko.listaanimes.ui.theme.statusColor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StatsViewModel(private val c: AppContainer, private val userId: Int) : ViewModel() {
    val stats = MutableStateFlow<UserStats?>(null)
    /** Notas (0–100) de cada tipo, para o gráfico no sistema de nota do app. */
    val scores = MutableStateFlow<Map<MediaKind, List<Int>>>(emptyMap())
    val error = MutableStateFlow<String?>(null)

    init { load() }

    fun load() = viewModelScope.launch {
        error.value = null
        try {
            val mine = c.settings.viewerIdNow() == userId
            // A minha lista já está no aparelho; a de outra pessoa vem do AniList.
            suspend fun raws(kind: MediaKind): List<Int> =
                if (mine) c.lists.observe(kind).first().map { it.entry.scoreRaw }
                else runCatching { c.social.userList(userId, kind).map { it.entry.scoreRaw } }.getOrDefault(emptyList())
            val a = async { raws(MediaKind.ANIME) }
            val m = async { raws(MediaKind.MANGA) }
            stats.value = c.social.stats(userId)
            scores.value = mapOf(MediaKind.ANIME to a.await(), MediaKind.MANGA to m.await())
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        }
    }
}

@Composable
fun StatsScreen(nav: Nav, userId: Int) {
    val vm = appViewModel(key = "stats-$userId") { StatsViewModel(it, userId) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val kind = MediaKind.entries[tab]

    Column(Modifier.fillMaxSize()) {
        TopBar(stats?.name?.let { t("Estatísticas de {0}", it) } ?: t("Estatísticas"), onBack = nav::back)
        error?.let { MessageBar(it, t("Tentar de novo")) { vm.load() } }
        Segmented(listOf(t("Anime"), t("Mangá")), tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        val st = stats
        if (st == null) {
            if (error == null) dev.viko.listaanimes.ui.components.SkeletonRows(6)
            return@Column
        }
        val k = if (kind == MediaKind.ANIME) st.anime else st.manga
        if (k.count == 0) { EmptyState(t("Nada na lista ainda.")); return@Column }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Overview(kind, k, scores[kind].orEmpty())
            ScoreChart(scores[kind].orEmpty())
            LengthChart(kind, k)
            StatusBars(kind, k)
            HBars(t("Formatos"), k.formats.map { Labels.format(it.first) to it.second })
            if (k.genres.isNotEmpty()) HBars(t("Gêneros mais vistos"), k.genres.map { it.first to it.second })
        }
    }
}

// ---------------- Resumo ----------------

@Composable
private fun Overview(kind: MediaKind, k: KindStats, raws: List<Int>) {
    val pt = dev.viko.listaanimes.i18n.I18n.locale
    val s = LocalSettings.current
    val md = ScoreBuckets.meanAndDeviation(raws)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(if (kind == MediaKind.ANIME) t("Total de animes") else t("Total de mangás"), "${k.count}", Modifier.weight(1f))
            if (kind == MediaKind.ANIME) Tile(t("Episódios assistidos"), "${k.secondary}", Modifier.weight(1f))
            else Tile(t("Capítulos lidos"), "${k.progress}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (kind == MediaKind.ANIME) {
                Tile(t("Dias assistidos"), "%.1f".format(pt, k.progress / 1440.0), Modifier.weight(1f))
                Tile(t("Dias planejados"), "%.1f".format(pt, k.planned / 1440.0), Modifier.weight(1f))
            } else {
                Tile(t("Volumes lidos"), "${k.secondary}", Modifier.weight(1f))
                Tile(t("Capítulos planejados"), "${k.planned}", Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileBox(t("Nota média"), Modifier.weight(1f)) {
                if (md == null) Text("–", style = Mono.style(22)) else ScoreView(md.first.toInt(), null, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
            }
            Tile(t("Desvio padrão"), md?.let { "%.1f".format(pt, it.second) } ?: "–", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) = TileBox(label, modifier) {
    Text(value, style = Mono.style(22, androidx.compose.ui.text.font.FontWeight.SemiBold), color = AppTheme.palette.text)
}

@Composable
private fun TileBox(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(horizontal = 16.dp, vertical = 12.dp)) {
        value()
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- Gráficos ----------------

/** Título de cada bloco + linha de detalhe do que foi tocado. */
@Composable
private fun ChartCard(title: String, detail: String?, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail ?: t("Toque numa barra para ver o número."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        content()
    }
}

private fun percent(n: Int, total: Int): String = if (total <= 0) "0%" else "${(n * 100.0 / total).let { if (it in 0.01..0.99) "<1" else "%.0f".format(it) }}%"

/**
 * Colunas verticais numa cor só (a de destaque): o valor fica em cima de cada barra e a escolhida
 * fica mais forte. [axis] desenha o rótulo de baixo (texto ou carinha).
 */
@Composable
private fun <T> Columns(items: List<Pair<T, Int>>, selected: Int?, onSelect: (Int) -> Unit, axis: @Composable (T) -> Unit) {
    val p = AppTheme.palette
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        items.forEachIndexed { i, (_, n) ->
            val frac by animateFloatAsState(n / max.toFloat(), label = "bar")
            val on = selected == null || selected == i
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).pressable(onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
            ) {
                Text("$n", style = Mono.style(11), color = if (on) p.text else p.textTertiary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth(0.62f).fillMaxHeight(if (n > 0) (frac.coerceIn(0f, 1f) * 0.82f).coerceAtLeast(0.02f) else 0f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(p.accent.copy(alpha = if (on) 1f else 0.35f)),
                )
            }
        }
    }
    // Linha de base discreta + rótulos.
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, _) -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { axis(label) } }
    }
}

@Composable
private fun ScoreChart(raws: List<Int>) {
    val system = LocalSettings.current.primaryScore
    val dist = remember(raws, system) { ScoreBuckets.distribution(raws, system) }
    val total = dist.sumOf { it.second }
    if (total == 0) return
    var sel by remember(system) { mutableStateOf<Int?>(null) }
    val detail = sel?.let { i -> dist.getOrNull(i)?.let { (b, n) -> t("{0}: {1} títulos ({2})", b.label, n, percent(n, total)) } }
    ChartCard(t("Notas"), detail) {
        Columns(dist, sel, { sel = if (sel == it) null else it }) { b ->
            val p = AppTheme.palette
            if (b.faceLevel != null) FaceIcon(b.faceLevel, faceColor(b.faceLevel, threeFaces = dist.size == 3), 20.dp, label = b.label)
            else Text(b.label, style = Mono.style(11), color = p.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LengthChart(kind: MediaKind, k: KindStats) {
    if (k.lengths.isEmpty()) return
    val total = k.lengths.sumOf { it.second }
    var sel by remember(kind) { mutableStateOf<Int?>(null) }
    val unit = if (kind == MediaKind.ANIME) t("episódios") else t("capítulos")
    val detail = sel?.let { i -> k.lengths.getOrNull(i)?.let { (l, n) -> t("{0} {1}: {2} títulos ({3})", l, unit, n, percent(n, total)) } }
    ChartCard(if (kind == MediaKind.ANIME) t("Quantidade de episódios") else t("Quantidade de capítulos"), detail) {
        Columns(k.lengths, sel, { sel = if (sel == it) null else it }) { l ->
            Text(l, style = Mono.style(10), color = AppTheme.palette.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** Status com a cor e o ícone de cada um (os mesmos do resto do app). */
@Composable
private fun StatusBars(kind: MediaKind, k: KindStats) {
    val p = AppTheme.palette
    val order = LocalSettings.current.statusOrder
    val rows = k.statuses.sortedBy { order.indexOf(it.first) }
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val total = rows.sumOf { it.second }
    ChartCard(t("Status"), t("{0} no total", total)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (st, n) ->
                val col = statusColor(st)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(st, kind, col, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(st.label(kind), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarTrack(n / max.toFloat(), col, Modifier.weight(1f))
                    Text("$n", style = Mono.style(12), color = p.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

/** Barras horizontais em ordem (formatos, gêneros). */
@Composable
private fun HBars(title: String, rows: List<Pair<String, Int>>) {
    val p = AppTheme.palette
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val total = rows.sumOf { it.second }
    var sel by remember(rows) { mutableStateOf<Int?>(null) }
    val detail = sel?.let { i -> rows.getOrNull(i)?.let { (l, n) -> t("{0}: {1} títulos ({2})", l, n, percent(n, total)) } }
    ChartCard(title, detail) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { i, (label, n) ->
                val on = sel == null || sel == i
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).pressable(onClick = { sel = if (sel == i) null else i }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (on) p.text else p.textTertiary,
                        modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarTrack(n / max.toFloat(), p.accent.copy(alpha = if (on) 1f else 0.35f), Modifier.weight(1f))
                    Text("$n", style = Mono.style(12), color = p.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun BarTrack(frac: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), label = "hbar")
    Box(modifier.height(10.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.02f)).clip(RoundedCornerShape(4.dp)).background(color))
    }
}
