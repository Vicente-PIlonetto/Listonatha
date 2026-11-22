package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.TitleLanguage
import dev.viko.listaanimes.domain.score.Scores
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.EntryRow
import dev.viko.listaanimes.ui.components.icon
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ListSort(val label: String) {
    TITLE(t("Título")), SCORE(t("Nota")), PROGRESS(t("Progresso")), UPDATED(t("Última atualização")), STARTED(t("Data de início")), COMPLETED(t("Data de fim"))
}

data class ListFilter(
    val statuses: Set<ListStatus> = emptySet(),
    val formats: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val year: Int? = null,
) {
    val active get() = statuses.isNotEmpty() || formats.isNotEmpty() || genres.isNotEmpty() || year != null
}

object ListLogic {
    fun apply(list: List<EntryWithMedia>, f: ListFilter, query: String, lang: TitleLanguage): List<EntryWithMedia> {
        val q = query.trim().lowercase()
        return list.filter { row ->
            val m = row.media ?: return@filter false
            (f.statuses.isEmpty() || row.entry.status in f.statuses) &&
                (f.formats.isEmpty() || m.format in f.formats) &&
                (f.genres.isEmpty() || m.genres.any { it in f.genres }) &&
                (f.year == null || (m.seasonYear ?: m.startYear) == f.year) &&
                (q.isEmpty() || listOfNotNull(m.titleRomaji, m.titleEnglish, m.titleNative).plus(m.synonyms).any { it.lowercase().contains(q) })
        }
    }

    fun sort(list: List<EntryWithMedia>, sort: ListSort, lang: TitleLanguage): List<EntryWithMedia> = when (sort) {
        ListSort.TITLE -> list.sortedBy { it.media!!.title(lang).lowercase() }
        // Notas especiais no topo, na ordem exata (SSS+++ acima de tudo).
        ListSort.SCORE -> list.sortedWith(compareByDescending<EntryWithMedia> { Scores.sortKey(it.entry.scoreRaw, it.special?.position) }.thenBy { it.media!!.title(lang) })
        ListSort.PROGRESS -> list.sortedByDescending { it.entry.progress }
        ListSort.UPDATED -> list.sortedByDescending { it.entry.updatedAt }
        ListSort.STARTED -> list.sortedWith(compareByDescending { it.entry.startedAt.toLocalDate() })
        ListSort.COMPLETED -> list.sortedWith(compareByDescending { it.entry.completedAt.toLocalDate() })
    }
}

class ListViewModel(private val c: AppContainer, val kind: MediaKind) : ViewModel() {
    val entries = c.lists.observe(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filter = MutableStateFlow(ListFilter())
    val sort = MutableStateFlow(ListSort.UPDATED)
    val query = MutableStateFlow("")
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    // A ordenação escolhida fica salva: fechar e abrir a lista não volta para "Última atualização".
    init {
        viewModelScope.launch {
            c.settings.listSort(kind)?.let { n -> ListSort.entries.firstOrNull { it.name == n } }?.let { sort.value = it }
        }
    }

    fun setSort(v: ListSort) {
        sort.value = v
        viewModelScope.launch { c.settings.setListSort(kind, v.name) }
    }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        try { c.lists.fullSync(); error.value = null } catch (e: Exception) { error.value = SYNC_ERROR } finally { refreshing.value = false }
    }

    fun increment(id: Int) = viewModelScope.launch { c.lists.increment(id) }
    fun toggleGrid(v: Boolean) = viewModelScope.launch { c.settings.setGridLayout(v) }
    fun setProgress(id: Int, v: Int) = viewModelScope.launch { c.lists.setProgress(id, v) }
}

@Composable
fun ListScreen(nav: Nav, kind: MediaKind) {
    val vm = appViewModel(key = "list-$kind") { ListViewModel(it, kind) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (kind == MediaKind.ANIME) t("Anime") else t("Mangá"), onBack = nav::back) {
            dev.viko.listaanimes.ui.components.LayoutToggle(s.gridLayout) { vm.toggleGrid(!s.gridLayout) }
            IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) { Icon(Icons.Outlined.Search, t("Buscar na lista")) }
            IconButton(onClick = { filterSheet = true }) {
                Icon(Icons.Outlined.FilterList, t("Filtros"), tint = if (filter.active) AppTheme.palette.accent else AppTheme.palette.text)
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, t("Ordenar")) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ListSort.entries.forEach {
                        DropdownMenuItem(
                            text = { Text(it.label, color = if (it == sort) AppTheme.palette.accent else AppTheme.palette.text) },
                            onClick = { vm.setSort(it); sortMenu = false },
                        )
                    }
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                placeholder = { Text(t("Buscar na lista")) }, singleLine = true,
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, t("Limpar")) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }
        error?.let { MessageBar(it) }
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            val all = entries ?: return@AppRefreshBox dev.viko.listaanimes.ui.components.SkeletonRows()
            val filtered = ListLogic.sort(ListLogic.apply(all, filter, query, s.titleLanguage), sort, s.titleLanguage)
            LazyColumn(Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) item { EmptyState(if (all.isEmpty()) t("Lista vazia.") else t("Nada encontrado com esses filtros.")) }
                s.statusOrder.forEach { st ->
                    val group = filtered.filter { it.entry.status == st }
                    if (group.isEmpty()) return@forEach
                    item(key = "h-$st") { dev.viko.listaanimes.ui.components.StatusHeader(st, kind, group.size) }
                    val plus = st == ListStatus.CURRENT || st == ListStatus.REPEATING
                    if (s.gridLayout) {
                        items(group.chunked(3), key = { "g-${it.first().entry.mediaId}" }) { rowItems ->
                            dev.viko.listaanimes.ui.components.GridRow(rowItems, 3) { row ->
                                val k = dev.viko.listaanimes.ui.components.coverKey("list", row.entry.mediaId)
                                dev.viko.listaanimes.ui.components.EntryGridCell(
                                    row.entry, row.media!!, row.special?.position, k, showPlusOne = plus,
                                    onClick = { nav.detail(row.entry.mediaId, k) },
                                    onIncrement = { vm.increment(row.entry.mediaId) },
                                    onSetProgress = { vm.setProgress(row.entry.mediaId, it) },
                                )
                            }
                        }
                    } else items(group, key = { it.entry.mediaId }) { row ->
                        val k = dev.viko.listaanimes.ui.components.coverKey("list", row.entry.mediaId)
                        Column(Modifier.animateItem()) {
                            EntryRow(
                                entry = row.entry, media = row.media!!, special = row.special?.position,
                                showScore = true, showPlusOne = plus, coverKey = k,
                                onClick = { nav.detail(row.entry.mediaId, k) },
                                onIncrement = { vm.increment(row.entry.mediaId) },
                                onSetProgress = { vm.setProgress(row.entry.mediaId, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (filterSheet) {
        val all = entries.orEmpty()
        ModalBottomSheet(
            onDismissRequest = { filterSheet = false },
            containerColor = AppTheme.palette.background,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            dragHandle = null,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp)) {
                FilterGroup(t("Status"), s.statusOrder.map { it to it.label(kind) }, filter.statuses, icon = { it.icon(kind) }) { st ->
                    vm.filter.value = filter.copy(statuses = filter.statuses.toggle(st))
                }
                val formats = all.mapNotNull { it.media?.format }.distinct().sorted()
                FilterGroup(t("Formato"), formats.map { it to Labels.format(it) }, filter.formats) { f ->
                    vm.filter.value = filter.copy(formats = filter.formats.toggle(f))
                }
                val genres = all.flatMap { it.media?.genres.orEmpty() }.distinct().sorted()
                FilterGroup(t("Gênero"), genres.map { it to it }, filter.genres) { g ->
                    vm.filter.value = filter.copy(genres = filter.genres.toggle(g))
                }
                val years = all.mapNotNull { it.media?.seasonYear ?: it.media?.startYear }.distinct().sortedDescending()
                FilterGroup(t("Ano"), years.map { it to it.toString() }, setOfNotNull(filter.year)) { y ->
                    vm.filter.value = filter.copy(year = if (filter.year == y) null else y)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.filter.value = ListFilter() }) { Text(t("Limpar filtros"), color = AppTheme.palette.textSecondary) }
            }
        }
    }
}

fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
fun <T> FilterGroup(title: String, options: List<Pair<T, String>>, selected: Set<T>, icon: ((T) -> androidx.compose.ui.graphics.vector.ImageVector)? = null, onToggle: (T) -> Unit) {
    if (options.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> Pill(label, v in selected, { onToggle(v) }, icon = icon?.invoke(v)) }
    }
}
