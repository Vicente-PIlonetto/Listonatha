package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.pressable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.repo.DiscoverSection
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.PosterRow
import dev.viko.listaanimes.ui.components.ScoreSize
import dev.viko.listaanimes.ui.components.ScoreView
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(private val c: AppContainer) : ViewModel() {
    val kind = MutableStateFlow(MediaKind.ANIME)
    val country = MutableStateFlow<String?>(null)
    val formats = MutableStateFlow<List<String>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val cacheKey = combine(kind, country, formats) { k, ct, f ->
        if (k == MediaKind.ANIME) "anime" else c.media.mangaDiscoverKey(ct, f)
    }
    val sections = cacheKey.flatMapLatest { c.media.observeDiscover(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recs = kind.flatMapLatest { c.media.observeRecommendations(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val loadedKeys = mutableSetOf<String>()
    private val recsLoaded = mutableSetOf<MediaKind>()

    /** Carrega cada combinação uma vez por abertura; recomendações uma vez por tipo. */
    fun ensureLoaded() {
        val key = if (kind.value == MediaKind.ANIME) "anime" else c.media.mangaDiscoverKey(country.value, formats.value)
        if (loadedKeys.add(key)) refresh(withRecs = recsLoaded.add(kind.value))
    }

    fun refresh(withRecs: Boolean = true) = viewModelScope.launch {
        loading.value = true
        try {
            val k = kind.value
            if (k == MediaKind.ANIME) c.media.refreshDiscoverAnime() else c.media.refreshDiscoverManga(country.value, formats.value)
            if (withRecs) {
                val entries = c.db.entries().getAll(k)
                val media = c.db.media().getAll(entries.map { it.mediaId }).associateBy { it.id }
                val specials = c.db.specials().all().associate { it.mediaId to it.position }
                c.media.refreshRecommendations(k, entries.mapNotNull { e -> media[e.mediaId]?.let { e to it } }, specials, entries.map { it.mediaId }.toSet())
            }
            error.value = null
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        } finally { loading.value = false }
    }
}

@Composable
fun DiscoverScreen(nav: Nav) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val p = AppTheme.palette
    val kind = MediaKind.entries[pager.currentPage]
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Descobrir")) {
            RandomButton(kind, emptyList(), emptyList(), nav)
            IconButton(onClick = { nav.genres(kind) }) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Tune, t("Gêneros e tags"), tint = p.accent)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            dev.viko.listaanimes.ui.components.PagerSegmented(pager, listOf(t("Anime"), t("Mangá")))
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(p.surfaceAlt)
                    .clickable { nav.search(kind) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = p.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Buscar"), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            DiscoverPage(nav, MediaKind.entries[page])
        }
    }
}

@Composable
private fun DiscoverPage(nav: Nav, pageKind: MediaKind) {
    val vm = appViewModel(key = "discover-$pageKind") { DiscoverViewModel(it).apply { kind.value = pageKind } }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()
    val formats by vm.formats.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val recs by vm.recs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    LaunchedEffect(kind, country, formats) { vm.ensureLoaded() }

    Column(Modifier.fillMaxSize()) {
        error?.let { MessageBar(it) }
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = loading && sections != null, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                val trending = sections?.firstOrNull { it.key == "trending" }?.items.orEmpty()
                if (trending.isNotEmpty()) item(key = "hero") { Box(Modifier.enterAnimation(0, distance = 40.dp)) { HeroCarousel(trending.take(8), kind) { m, k -> nav.detail(m.id, k) } } }
                if (sections == null) item {
                    Column {
                        dev.viko.listaanimes.ui.components.SkeletonBox(null, 220.dp, Modifier.padding(20.dp), radius = 20.dp)
                        repeat(2) {
                            dev.viko.listaanimes.ui.components.SkeletonBox(140.dp, 16.dp, Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp))
                            dev.viko.listaanimes.ui.components.SkeletonPosterRow()
                        }
                    }
                }
                if (kind == MediaKind.MANGA) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(t("Todas as origens"), country == null, { vm.country.value = null })
                            listOf("JP", "KR", "CN").forEach { ct -> Pill(Labels.country(ct), country == ct, { vm.country.value = if (country == ct) null else ct }) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Labels.mangaFormats.forEach { f ->
                                Pill(Labels.format(f), f in formats, { vm.formats.value = if (f in formats) formats - f else formats + f })
                            }
                        }
                    }
                }
                val list = sections
                if (list == null && !loading) item { EmptyState(LOAD_ERROR) }
                list.orEmpty().forEachIndexed { si, sec ->
                    if (sec.items.isEmpty() || sec.key == "trending") return@forEachIndexed
                    item(key = "sec-${sec.key}") {
                        Column(Modifier.enterAnimation(si)) {
                        SectionHeader(sec.title, action = t("ver tudo"), onAction = { seeAll(nav, sec, kind, country, formats) })
                        PosterRow(sec.items, "d${kind.name}${sec.key}") { m, k -> nav.detail(m.id, k) }
                        }
                    }
                }
                recs.orEmpty().forEach { g ->
                    item(key = "rec-${g.because.id}") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Porque você deu "), style = MaterialTheme.typography.titleSmall)
                            ScoreView(g.scoreRaw, g.special, s.primaryScore, null, ScoreSize.Small)
                            Text(t(" para {0}", g.because.title(s.titleLanguage)), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PosterRow(g.items, "rec${g.because.id}") { m, k -> nav.detail(m.id, k) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun seeAll(nav: Nav, sec: DiscoverSection, kind: MediaKind, country: String?, formats: List<String>) {
    when (sec.key) {
        "season" -> nav.season(next = false)
        "nextSeason" -> nav.season(next = true)
        else -> nav.browse(sec.key, kind, country, formats)
    }
}


/** Carrossel grande no topo: banner da obra, avança sozinho, o fundo pega a cor da capa. */
@Composable
private fun HeroCarousel(items: List<dev.viko.listaanimes.domain.Media>, kind: MediaKind, onClick: (dev.viko.listaanimes.domain.Media, String) -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val pager = androidx.compose.foundation.pager.rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = androidx.compose.animation.core.tween(700))
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(
            pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
        ) { page ->
            val m = items[page]
            val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(m.coverColor)
            val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
            val k = dev.viko.listaanimes.ui.components.coverKey("hero${kind.name}", m.id)
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .graphicsLayer {
                        val f = 1f - (kotlin.math.abs(off).coerceIn(0f, 1f) * 0.08f)
                        scaleX = f; scaleY = f
                    }
                    .clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.3f))
                    .pressable(onClick = { onClick(m, k) }),
            ) {
                coil3.compose.AsyncImage(
                    m.banner ?: m.coverLarge, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = off * size.width * 0.25f }.then(with(dev.viko.listaanimes.ui.components.LocalSettings.current) { if (m.isAdult && blurAdult) Modifier.blur(30.dp) else Modifier }),
                )
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.35f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                )))
                Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.Bottom) {
                    dev.viko.listaanimes.ui.components.Cover(m.coverLarge, 64.dp, placeholder = m.coverColor, adult = m.isAdult, sharedKey = k)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t("Em alta · #{0}", page + 1), style = MaterialTheme.typography.labelMedium, color = tint)
                        Text(m.title(s.titleLanguage), style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(Labels.format(m.format).takeIf { it.isNotEmpty() }, m.averageScore?.let { "$it%" }, m.genres.take(2).joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1,
                        )
                    }
                }
            }
        }
        // Pontinhos da página.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val sel = i == pager.currentPage
                val w by androidx.compose.animation.core.animateDpAsState(if (sel) 18.dp else 6.dp, label = "dot")
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(RoundedCornerShape(3.dp)).background(if (sel) p.accent else p.divider))
            }
        }
    }
}
