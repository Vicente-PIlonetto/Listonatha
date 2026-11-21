package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.components.adultBlur
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.AiringWithMedia
import dev.viko.listaanimes.ui.components.Cover
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.countdown
import dev.viko.listaanimes.ui.components.nowEpoch
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAO_PAULO = ZoneId.systemDefault()
private val PT get() = dev.viko.listaanimes.i18n.I18n.locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val c: AppContainer) : ViewModel() {
    private val start = nowEpoch() - 3 * 3600
    private val end = nowEpoch() + 7 * 24 * 3600
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    val items = combine(c.schedule.observe(start, end), c.lists.observeIds(), c.settings.settings) { list, ids, s ->
        val mine = ids.toSet()
        if (s.scheduleShowAll) list else list.filter { it.airing.mediaId in mine }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myIds = c.lists.observeIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Semana já agrupada por dia, montada fora da thread da tela; só emite quando algo muda de fato. */
    val week = combine(c.schedule.observe(start, end), c.lists.observeIds(), c.settings.settings) { list, ids, s ->
        val mine = ids.toSet()
        val zone = SAO_PAULO
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        (if (s.scheduleShowAll) list else list.filter { it.airing.mediaId in mine })
            .filter { it.media != null }
            .map { a ->
                val m = a.media!!
                AiringUi(
                    id = a.airing.id, mediaId = m.id, title = m.title(s.titleLanguage), episode = a.airing.episode,
                    airingAt = a.airing.airingAt, time = Instant.ofEpochSecond(a.airing.airingAt).atZone(zone).format(hhmm),
                    cover = m.coverMedium ?: m.coverLarge, banner = m.banner, color = m.coverColor, adult = m.isAdult,
                    mine = m.id in mine, markMine = s.scheduleShowAll,
                    day = Instant.ofEpochSecond(a.airing.airingAt).atZone(zone).toLocalDate(),
                )
            }
            .groupBy { it.day }.toSortedMap()
            .map { (day, rows) -> DayGroup(day, rows) }
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---- Calendário ----
    val month = MutableStateFlow(YearMonth.now(SAO_PAULO))
    val monthLoading = MutableStateFlow(false)
    private val loadedMonths = mutableSetOf<String>()

    private fun bounds(ym: YearMonth): Pair<Long, Long> =
        ym.atDay(1).atStartOfDay(SAO_PAULO).toEpochSecond() to ym.plusMonths(1).atDay(1).atStartOfDay(SAO_PAULO).toEpochSecond()

    /** Episódios do mês, agrupados por dia. */
    val monthItems = month.flatMapLatest { ym ->
        val (from, to) = bounds(ym)
        combine(c.schedule.observe(from, to), c.lists.observeIds(), c.settings.settings) { list, ids, s ->
            val mine = ids.toSet()
            (if (s.scheduleShowAll) list else list.filter { it.airing.mediaId in mine })
                .filter { it.media != null }
                .groupBy { Instant.ofEpochSecond(it.airing.airingAt).atZone(SAO_PAULO).toLocalDate() }
        }
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadMonth(force: Boolean = false) = viewModelScope.launch {
        val ym = month.value
        val showAll = c.settings.current().scheduleShowAll
        val key = "$ym-$showAll"
        if (!force && !loadedMonths.add(key)) return@launch
        monthLoading.value = true
        try {
            val (from, to) = bounds(ym)
            c.schedule.refresh(from, to, if (showAll) null else c.db.entries().animeIds(), maxPages = 20)
            error.value = null
        } catch (e: Exception) {
            loadedMonths.remove(key)
            error.value = SYNC_ERROR
        } finally { monthLoading.value = false }
    }

    fun shiftMonth(by: Long) { month.value = month.value.plusMonths(by); loadMonth() }

    private var loaded = false

    fun firstLoad() { if (!loaded) { loaded = true; refresh() } }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        try {
            val showAll = c.settings.current().scheduleShowAll
            c.schedule.refresh(nowEpoch() - 3 * 3600, nowEpoch() + 7 * 24 * 3600, if (showAll) null else c.db.entries().animeIds())
            error.value = null
        } catch (e: Exception) {
            error.value = SYNC_ERROR
        } finally { refreshing.value = false }
    }

    fun setShowAll(v: Boolean) = viewModelScope.launch { c.settings.setScheduleShowAll(v); refresh(); loadedMonths.clear() }
}

@Composable
fun ScheduleScreen(nav: Nav) {
    val vm = appViewModel { ScheduleViewModel(it) }
    val s = LocalSettings.current
    val error by vm.error.collectAsStateWithLifecycle()
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    LaunchedEffect(Unit) { vm.firstLoad() }
    LaunchedEffect(pager.currentPage, s.scheduleShowAll) { if (pager.currentPage == 1) vm.loadMonth() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Agenda"))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            dev.viko.listaanimes.ui.components.PagerSegmented(pager, listOf(t("Semana"), t("Calendário")))
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(t("Minha lista"), !s.scheduleShowAll, { vm.setShowAll(false) })
            Pill(t("Todos em lançamento"), s.scheduleShowAll, { vm.setShowAll(true) })
        }
        error?.let { MessageBar(it) }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) WeekList(vm, nav) else MonthCalendar(vm, nav)
        }
    }
}

/** Um episódio pronto para a tela (estável: o Compose pula cartões que não mudaram). */
@androidx.compose.runtime.Immutable
data class AiringUi(
    val id: Int, val mediaId: Int, val title: String, val episode: Int, val airingAt: Long, val time: String,
    val cover: String?, val banner: String?, val color: String?, val adult: Boolean, val mine: Boolean, val markMine: Boolean,
    val day: LocalDate,
)

@androidx.compose.runtime.Immutable
data class DayGroup(val day: LocalDate, val rows: List<AiringUi>)

@Composable
private fun WeekList(vm: ScheduleViewModel, nav: Nav) {
    val week by vm.week.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val days = week ?: return@AppRefreshBox dev.viko.listaanimes.ui.components.SkeletonRows()
        val today = LocalDate.now(SAO_PAULO)
        LazyColumn(Modifier.fillMaxSize()) {
            if (days.isEmpty() && !refreshing) item { EmptyState(t("Nada nos próximos 7 dias.")) }
            var index = 0
            days.forEach { g ->
                val i0 = index++
                item(key = "d-${g.day}", contentType = "day") { Box(Modifier.enterAnimation(i0)) { DayBanner(g.day, today, g.rows) } }
                g.rows.forEach { row ->
                    val i = index++
                    item(key = row.id, contentType = "ep") {
                        Box(Modifier.enterAnimation(i)) { AiringCard(row) { nav.detail(row.mediaId) } }
                    }
                }
            }
        }
    }
}

/** Iniciais dos dias (domingo primeiro) no idioma do app. */
private val WEEKDAYS: List<String> get() = (0..6).map { i ->
    java.time.DayOfWeek.SUNDAY.plus(i.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, dev.viko.listaanimes.i18n.I18n.locale)
}

@Composable
private fun MonthCalendar(vm: ScheduleViewModel, nav: Nav) {
    val p = AppTheme.palette
    val s = LocalSettings.current
    val month by vm.month.collectAsStateWithLifecycle()
    val byDay by vm.monthItems.collectAsStateWithLifecycle()
    val loading by vm.monthLoading.collectAsStateWithLifecycle()
    val myIds by vm.myIds.collectAsStateWithLifecycle()
    val today = LocalDate.now(SAO_PAULO)
    var selected by remember { mutableStateOf(today) }
    LaunchedEffect(month) { if (YearMonth.from(selected) != month) selected = if (YearMonth.from(today) == month) today else month.atDay(1) }
    val mine = remember(myIds) { myIds.toSet() }
    val days = byDay.orEmpty()

    dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = loading, onRefresh = { vm.loadMonth(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { vm.shiftMonth(-1) }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Mês anterior"))
                    }
                    androidx.compose.animation.AnimatedContent(month, Modifier.weight(1f), label = "mes", transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (androidx.compose.animation.slideInHorizontally { it * dir / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                            (androidx.compose.animation.slideOutHorizontally { -it * dir / 3 } + androidx.compose.animation.fadeOut())
                    }) { ym ->
                        Text(
                            ym.format(DateTimeFormatter.ofPattern(t("MMMM 'de' yyyy"), PT)).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { vm.shiftMonth(1) }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Próximo mês"))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    WEEKDAYS.forEach { d ->
                        Text(d, style = MaterialTheme.typography.labelMedium, color = p.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Grade do mês, começando no domingo.
                val first = month.atDay(1)
                val offset = first.dayOfWeek.value % 7
                val cells = offset + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (cIdx in 0 until 7) {
                                val n = r * 7 + cIdx - offset + 1
                                if (n < 1 || n > month.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                                val date = month.atDay(n)
                                val list = days[date].orEmpty()
                                DayCell(
                                    date = date, items = list, isToday = date == today, isSelected = date == selected,
                                    hasMine = list.any { it.airing.mediaId in mine },
                                    index = r * 7 + cIdx,
                                    modifier = Modifier.weight(1f),
                                ) { selected = date }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val dayRows = days[selected].orEmpty().sortedBy { it.airing.airingAt }
            item(key = "sel-$selected") {
                Box(Modifier.enterAnimation()) { SectionHeader(dayLabel(selected, today), dayRows.size) }
            }
            if (dayRows.isEmpty()) item(key = "empty-$selected") {
                Text(
                    if (loading) t("Carregando o mês…") else t("Nenhum episódio neste dia."),
                    style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            dayRows.forEachIndexed { i, row ->
                item(key = "c-${row.airing.id}") {
                    Box(Modifier.enterAnimation(i)) {
                        AiringRow(row, row.airing.mediaId in mine, s.scheduleShowAll, s.titleLanguage) { nav.detail(row.airing.mediaId) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Um dia do calendário: a capa do primeiro episódio dá a cor; o número fica por cima. */
@Composable
private fun DayCell(
    date: LocalDate, items: List<AiringWithMedia>, isToday: Boolean, isSelected: Boolean, hasMine: Boolean, index: Int,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = AppTheme.palette
    val first = items.firstOrNull()?.media
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    // Escolhido: cresce um pouco e a capa fica mais forte; sem contorno.
    val sel by androidx.compose.animation.core.animateFloatAsState(if (isSelected) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "sel")
    Box(
        modifier.aspectRatio(0.78f).enterAnimation(index / 7, distance = 12.dp)
            .graphicsLayer { val k = 1f + 0.06f * sel; scaleX = k; scaleY = k }
            .clip(shape)
            .background(
                when {
                    isSelected && first == null -> p.accent.copy(alpha = 0.35f)
                    isToday -> p.accent.copy(alpha = 0.18f)
                    else -> p.surfaceAlt
                },
            )
            .pressable(onClick = onClick),
    ) {
        if (first != null) {
            coil3.compose.AsyncImage(
                first.coverMedium ?: first.coverLarge, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f + 0.5f * sel },
            )
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), 0.6f to androidx.compose.ui.graphics.Color.Transparent,
            )))
        }
        Text(
            "${date.dayOfMonth}",
            style = Mono.style(13, if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
            color = if (first != null) androidx.compose.ui.graphics.Color.White else if (isToday) p.accent else p.textSecondary,
            modifier = Modifier.padding(5.dp),
        )
        if (items.size > 1) Text(
            "${items.size}",
            style = Mono.style(10, androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = p.background,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(if (hasMine) p.accent else p.text.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
        )
    }
}

fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> t("Hoje")
    today.plusDays(1) -> t("Amanhã")
    today.minusDays(1) -> t("Ontem")
    else -> day.format(DateTimeFormatter.ofPattern(t("EEEE, d MMM"), PT)).replaceFirstChar { it.uppercase() }
}

@Composable
private fun AiringRow(row: AiringWithMedia, mine: Boolean, markMine: Boolean, lang: dev.viko.listaanimes.domain.TitleLanguage, onClick: () -> Unit) {
    val p = AppTheme.palette
    val m = row.media!!
    val time = Instant.ofEpochSecond(row.airing.airingAt).atZone(SAO_PAULO).format(DateTimeFormatter.ofPattern("HH:mm"))
    val left = row.airing.airingAt - nowEpoch()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = Mono.style(14), color = p.textSecondary, modifier = Modifier.width(52.dp))
        Cover(m.coverMedium ?: m.coverLarge, 36.dp, placeholder = m.coverColor, adult = m.isAdult)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title(lang), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (left > 0) t("EP {0} em {1}", row.airing.episode, countdown(left)) else t("EP {0} disponível", row.airing.episode),
                style = Mono.style(13),
                color = if (left <= 0 && mine) p.accent else p.textSecondary,
            )
        }
        if (mine && markMine) Text(t("na lista"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
    }
}

/** Cabeçalho do dia: banner de um anime do dia, com a data e "Hoje"/"Amanhã"/dia da semana. */
@Composable
private fun DayBanner(day: LocalDate, today: LocalDate, rows: List<AiringUi>) {
    val p = AppTheme.palette
    val pick = rows.firstOrNull { it.banner != null && !it.adult } ?: rows.firstOrNull()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp).fillMaxWidth().height(96.dp).clip(shape).background(p.surfaceAlt)) {
        coil3.compose.AsyncImage(
            pick?.banner ?: pick?.cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(DAY_SCRIM))
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                day.format(DAY_SHORT),
                style = Mono.style(13), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(dayLabel(day, today), style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
        }
        Text(
            "${rows.size}", style = Mono.style(13, androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.background,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(p.accent).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val DAY_SCRIM = androidx.compose.ui.graphics.Brush.horizontalGradient(
    0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), 0.7f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f),
)
private val DAY_SHORT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", PT)

/** Episódio na semana: horário à esquerda, cartão com a capa, título e episódio. */
@Composable
private fun AiringCard(row: AiringUi, onClick: () -> Unit) {
    val p = AppTheme.palette
    val left = row.airingAt - nowEpoch()
    val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(row.color)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.time, style = Mono.style(13), color = if (left <= 0) p.textTertiary else p.textSecondary, modifier = Modifier.width(50.dp))
        Row(
            Modifier.weight(1f).height(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(p.surfaceAlt).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            coil3.compose.AsyncImage(
                row.cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.width(72.dp).fillMaxSize().background(tint.copy(alpha = 0.3f)).adultBlur(row.adult),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (left > 0) t("EP {0} em {1}", row.episode, countdown(left)) else t("EP {0} disponível", row.episode),
                    style = Mono.style(12), color = if (left <= 0 && row.mine) p.accent else p.textSecondary,
                )
            }
            if (row.mine && row.markMine) Box(Modifier.padding(end = 12.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.accent))
        }
    }
}
