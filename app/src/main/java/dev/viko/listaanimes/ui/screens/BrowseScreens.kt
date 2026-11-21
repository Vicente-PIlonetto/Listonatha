package dev.viko.listaanimes.ui.screens

import androidx.compose.material.icons.outlined.Casino
import androidx.compose.ui.graphics.graphicsLayer
import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.repo.BrowseParams
import dev.viko.listaanimes.data.repo.SeasonRef
import dev.viko.listaanimes.data.repo.TagInfo
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LocalListStatus
import dev.viko.listaanimes.ui.components.MediaListRow
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Lista paginada para qualquer combinação de filtros. */
class PagedBrowseViewModel(private val c: AppContainer, private val limit: Int? = null) : ViewModel() {
    val items = MutableStateFlow<List<Media>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)
    var params: BrowseParams? = null; private set
    private var page = 0
    private var job: Job? = null

    fun load(p: BrowseParams, debounceMs: Long = 0) {
        if (p == params && items.value.isNotEmpty()) return
        params = p
        page = 0
        items.value = emptyList()
        done.value = false
        job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            nextPageInternal()
        }
    }

    fun nextPage() {
        if (loading.value || done.value || params == null) return
        job = viewModelScope.launch { nextPageInternal() }
    }

    private suspend fun nextPageInternal() {
        val p = params ?: return
        loading.value = true
        try {
            val r = c.media.browse(p, page + 1)
            page++
            val merged = (items.value + r.items).distinctBy { it.id }
            items.value = if (limit != null) merged.take(limit) else merged
            done.value = !r.hasNext || (limit != null && items.value.size >= limit)
            error.value = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        } finally { loading.value = false }
    }
}

@Composable
private fun PagedList(vm: PagedBrowseViewModel, nav: Nav, hideOnList: Boolean = false, header: @Composable () -> Unit = {}) {
    val grid = dev.viko.listaanimes.ui.components.LocalSettings.current.gridLayout
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val onList = LocalListStatus.current
    val state = rememberLazyListState()
    EndReached(state) { vm.nextPage() }
    val shown = if (hideOnList) items.filter { it.id !in onList } else items
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        item { header() }
        item { RateLimitNotice() }
        error?.let { item { MessageBar(it) } }
        if (grid) {
            items(shown.chunked(3), key = { "g-${it.first().id}" }) { row ->
                dev.viko.listaanimes.ui.components.GridRow(row, 3) { m ->
                    val k = dev.viko.listaanimes.ui.components.coverKey("b", m.id)
                    dev.viko.listaanimes.ui.components.PosterGridCell(m, k) { nav.detail(m.id, k) }
                }
            }
        } else items(shown, key = { it.id }) { m ->
            val k = dev.viko.listaanimes.ui.components.coverKey("b", m.id)
            Column(Modifier.animateItem()) { MediaListRow(m, k) { nav.detail(m.id, k) } }
        }
        if (!loading && shown.isEmpty() && error == null && vm.params != null) item { EmptyState(t("Nada encontrado.")) }
        if (loading) item { dev.viko.listaanimes.ui.components.SkeletonRows(if (shown.isEmpty()) 6 else 2) }
    }
}

@Composable
private fun EndReached(state: LazyListState, onEnd: () -> Unit) {
    val reached by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(state) { snapshotFlow { reached }.distinctUntilChanged().filter { it }.collect { onEnd() } }
}

private fun presetTitle(preset: String) = when (preset) {
    "trending" -> t("Em alta")
    "recent" -> t("Recém-adicionados")
    "popular" -> t("Mais populares")
    "top" -> t("Top 100")
    else -> t("Obras")
}

private fun presetSort(preset: String) = when (preset) {
    "trending" -> listOf("TRENDING_DESC", "POPULARITY_DESC")
    "recent" -> listOf("ID_DESC")
    "top" -> listOf("SCORE_DESC")
    else -> listOf("POPULARITY_DESC")
}

@Composable
fun BrowseScreen(nav: Nav, preset: String, kind: MediaKind, country: String?, formats: List<String>) {
    val vm = appViewModel(key = "browse-$preset-$kind-$country-$formats") { PagedBrowseViewModel(it, if (preset == "top") 100 else null) }
    LaunchedEffect(Unit) { vm.load(BrowseParams(kind, presetSort(preset), country = country, formats = formats)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(presetTitle(preset), onBack = nav::back) { GridButton() }
        PagedList(vm, nav)
    }
}

private val seasonSorts = listOf(
    "POPULARITY_DESC" to t("Popularidade"), "SCORE_DESC" to t("Nota"), "START_DATE" to t("Estreia"), "TITLE_ROMAJI" to t("Título"),
)

@Composable
fun SeasonScreen(nav: Nav, next: Boolean) {
    val vm = appViewModel(key = "season") { PagedBrowseViewModel(it) }
    var season by remember { mutableStateOf(SeasonRef.current().let { if (next) it.next() else it }) }
    var formats by remember { mutableStateOf(emptyList<String>()) }
    var sort by remember { mutableStateOf("POPULARITY_DESC") }
    var hide by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    LaunchedEffect(season, formats, sort) {
        vm.load(BrowseParams(MediaKind.ANIME, listOf(sort), season = season, formats = formats))
    }
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Temporada"), onBack = nav::back) {
            GridButton()
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, t("Ordenar")) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    seasonSorts.forEach { (k, label) ->
                        DropdownMenuItem(text = { Text(label, color = if (k == sort) p.accent else p.text) }, onClick = { sort = k; sortMenu = false })
                    }
                }
            }
        }
        PagedList(vm, nav, hideOnList = hide) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { season = season.previous() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Temporada anterior")) }
                    Text(season.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { season = season.next() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Próxima temporada")) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Labels.animeFormats.forEach { f ->
                        Pill(Labels.format(f), f in formats, { formats = if (f in formats) formats - f else formats + f })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Esconder o que está na minha lista"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = hide, onCheckedChange = { hide = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.surfaceAlt, uncheckedThumbColor = p.textTertiary))
                }
                ThinDivider()
            }
        }
    }
}

/** Gêneros "de nicho": no AniList são tags. Só aparecem as que existem na lista de tags. */
private val NICHE_TAGS = listOf(
    "Isekai", "Reincarnation", "Iyashikei", "Cute Girls Doing Cute Things", "Shounen", "Seinen", "Shoujo", "Josei",
    "Villainess", "Cultivation", "Time Loop", "Time Manipulation", "Super Power", "Martial Arts", "Survival", "Dungeon",
    "Video Games", "Virtual World", "Idol", "Female Harem", "Male Harem", "Boys' Love", "Yuri", "Love Triangle",
    "Vampire", "Zombie", "Demons", "Gods", "Mythology", "Kaiju", "Super Robot", "Real Robot", "Cyberpunk", "Space",
    "Post-Apocalyptic", "Military", "Samurai", "Ninja", "Pirates", "Historical", "Detective", "Revenge", "Tragedy",
    "Gore", "Body Swapping", "Food", "Magic", "School", "Rural", "Otaku Culture", "Gyaru", "Tsundere", "Anti-Hero",
    "Coming of Age", "Found Family",
)

class GenresViewModel(private val c: AppContainer) : ViewModel() {
    val genres = MutableStateFlow<List<String>>(emptyList())
    val tags = MutableStateFlow<List<TagInfo>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch {
            try { val gt = c.media.genresTags(); genres.value = gt.genres; tags.value = gt.tags } catch (e: Exception) { error.value = e.toUserMessage() }
        }
    }
}

@Composable
fun GenreTagScreen(nav: Nav, initialKind: MediaKind, initialGenre: String? = null, initialTag: String? = null) {
    val gvm = appViewModel { GenresViewModel(it) }
    val vm = appViewModel(key = "genre-browse-$initialKind-$initialGenre-$initialTag") { PagedBrowseViewModel(it) }
    val genres by gvm.genres.collectAsStateWithLifecycle()
    val tags by gvm.tags.collectAsStateWithLifecycle()
    val error by gvm.error.collectAsStateWithLifecycle()
    var kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialKind) }
    var selG by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOfNotNull(initialGenre)) }
    var selT by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOfNotNull(initialTag)) }
    var tagQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showTags by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(kind, selG, selT) {
        if (selG.isNotEmpty() || selT.isNotEmpty()) vm.load(BrowseParams(kind, listOf("POPULARITY_DESC"), genres = selG.toList(), tags = selT.toList()), 400)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(initialTag ?: initialGenre ?: t("Filtros"), onBack = nav::back) {
            RandomButton(kind, selG.toList(), selT.toList(), nav)
            GridButton()
        }
        error?.let { MessageBar(it) }
        PagedList(vm, nav) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Segmented(listOf(t("Anime"), t("Mangá")), kind.ordinal, { kind = MediaKind.entries[it] })
                Text(t("Gêneros"), style = MaterialTheme.typography.labelMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.forEach { g -> Pill(g, g in selG, { selG = selG.toggle(g) }) }
                }
                val known = remember(tags) { tags.map { it.name }.toSet() }
                val niche = NICHE_TAGS.filter { it in known }
                if (niche.isNotEmpty()) {
                    Text(t("Nichos"), style = MaterialTheme.typography.labelMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        niche.forEach { t -> Pill(t, t in selT, { selT = selT.toggle(t) }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(t("Tags"), style = MaterialTheme.typography.labelMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.weight(1f))
                    if (selG.isNotEmpty() || selT.isNotEmpty()) Text(t("limpar"), style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selG = emptySet(); selT = emptySet() }.padding(8.dp))
                    Text(if (showTags) t("ocultar todas") else t("ver todas ({0})", tags.size), style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showTags = !showTags }.padding(8.dp))
                }
                OutlinedTextField(tagQuery, { tagQuery = it; if (it.isNotBlank()) showTags = true }, placeholder = { Text(t("Procurar tag")) }, singleLine = true,
                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(14.dp))
                if (selT.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selT.forEach { t -> Pill(t, true, { selT = selT - t }) }
                }
                if (showTags) {
                    val q = tagQuery.trim().lowercase()
                    tags.filter { it.name !in selT && (q.isEmpty() || it.name.lowercase().contains(q)) }.groupBy { it.category ?: t("Outras") }
                        .toSortedMap().forEach { (cat, list) ->
                            Text(cat, style = MaterialTheme.typography.labelSmall, color = AppTheme.palette.textTertiary, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { t -> Pill(t.name, false, { selT = selT + t.name }) }
                            }
                        }
                }
                Spacer(Modifier.height(12.dp))
                ThinDivider()
                if (selG.isEmpty() && selT.isEmpty()) EmptyState(t("Escolha um gênero ou tag."))
            }
        }
    }
}

@Composable
fun SearchScreen(nav: Nav, initialKind: MediaKind) {
    val vm = appViewModel(key = "search") { PagedBrowseViewModel(it) }
    var kind by remember { mutableStateOf(initialKind) }
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q, kind) {
        if (q.trim().length >= 2) vm.load(BrowseParams(kind, listOf("SEARCH_MATCH", "POPULARITY_DESC"), search = q.trim()), debounceMs = 450)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Buscar"), onBack = nav::back) { GridButton() }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(if (kind == MediaKind.ANIME) t("Título do anime") else t("Título do mangá")) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, t("Limpar")) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(focus),
            shape = RoundedCornerShape(4.dp),
        )
        Segmented(listOf(t("Anime"), t("Mangá")), kind.ordinal, { kind = MediaKind.entries[it] }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (q.trim().length < 2) EmptyState(t("Digite pelo menos duas letras.")) else PagedList(vm, nav)
    }
}


@Composable
private fun GridButton() {
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val s = dev.viko.listaanimes.ui.components.LocalSettings.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    dev.viko.listaanimes.ui.components.LayoutToggle(s.gridLayout) { scope.launch { c.settings.setGridLayout(!s.gridLayout) } }
}

/** Sorteia uma obra (com os filtros dados) que ainda não está na lista e abre o detalhe. */
@Composable
fun RandomButton(kind: MediaKind, genres: List<String>, tags: List<String>, nav: Nav) {
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val onList = LocalListStatus.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val spin by androidx.compose.animation.core.animateFloatAsState(if (busy) 720f else 0f, androidx.compose.animation.core.tween(if (busy) 900 else 0), label = "dice")
    IconButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try { c.media.random(kind, genres, tags, onList.keys)?.let { nav.detail(it.id) } } catch (_: Exception) {} finally { busy = false }
        }
    }) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Casino, t("Sortear"), tint = AppTheme.palette.accent,
            modifier = Modifier.graphicsLayer { rotationZ = spin })
    }
}

/** Aviso enquanto o app espera o AniList liberar novas buscas (limite por minuto). */
@Composable
fun RateLimitNotice() {
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val until by c.api.rateLimit.waitingUntil.collectAsStateWithLifecycle()
    val end = until ?: return
    var left by remember(end) { mutableStateOf(((end - System.currentTimeMillis()) / 1000).coerceAtLeast(1)) }
    LaunchedEffect(end) {
        while (left > 0) { delay(1000); left = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0) }
    }
    MessageBar(t("O AniList limita as buscas por minuto. Continuando em {0} s.", left))
}
