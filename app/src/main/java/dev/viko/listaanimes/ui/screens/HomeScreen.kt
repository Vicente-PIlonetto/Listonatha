package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.ui.components.pressable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import dev.viko.listaanimes.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import dev.viko.listaanimes.ui.components.enterAnimation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.auth.SessionState
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.ProgressRules
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.EntryRow
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.NewDot
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.nowEpoch
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.nav.startLogin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val kind = MutableStateFlow(MediaKind.ANIME)
    val anime = c.lists.observe(MediaKind.ANIME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val manga = c.lists.observe(MediaKind.MANGA).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val session = c.auth.state
    val pending = c.lists.pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val hasUnread = combine(c.notifications.observe(), c.notifications.seenAt) { list, seen -> list.any { it.createdAt > seen } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            val last = c.settings.lastSync.first()
            val empty = c.db.entries().count() == 0
            if (c.auth.state.value == SessionState.LOGGED_IN && (empty || nowEpoch() - last > 15 * 60)) refresh(minIntervalSec = 60)
        }
    }

    fun refresh(minIntervalSec: Long = 0) = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        try {
            c.lists.fullSync(minIntervalSec)
            error.value = null
            runCatching { c.notifications.refresh(markRead = false) }
        } catch (e: Exception) {
            error.value = SYNC_ERROR
        } finally {
            refreshing.value = false
        }
    }

    fun increment(id: Int) = viewModelScope.launch { c.lists.increment(id) }
    fun setProgress(id: Int, v: Int) = viewModelScope.launch { c.lists.setProgress(id, v) }
    fun toggleSection(key: String) = viewModelScope.launch { c.settings.toggleCollapsed(key) }
    fun toggleGrid(v: Boolean) = viewModelScope.launch { c.settings.setGridLayout(v) }
}

@Composable
fun HomeScreen(nav: Nav) {
    val vm = appViewModel { HomeViewModel(it) }
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val anime by vm.anime.collectAsStateWithLifecycle()
    val manga by vm.manga.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val unread by vm.hasUnread.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val kind = MediaKind.entries[pager.currentPage]

    Column(Modifier.fillMaxSize()) {
        TopBar(dev.viko.listaanimes.i18n.tc("tab", "Início")) {
            dev.viko.listaanimes.ui.components.LayoutToggle(s.gridLayout) { vm.toggleGrid(!s.gridLayout) }
            IconButton(onClick = { nav.search(kind) }) { Icon(Icons.Outlined.Search, t("Buscar")) }
            IconButton(onClick = { nav.notifications() }) {
                Box {
                    Icon(Icons.Outlined.Notifications, t("Notificações"))
                    if (unread) NewDot(Modifier.align(Alignment.TopEnd))
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(session == SessionState.EXPIRED || error != null || pending > 0) {
            when {
                session == SessionState.EXPIRED -> MessageBar(t("Sua sessão do AniList expirou. Entre de novo para sincronizar."), t("Entrar")) { startLogin(ctx, c) }
                error != null -> MessageBar(error!!)
                else -> MessageBar(if (pending == 1) t("1 alteração aguardando conexão.") else t("{0} alterações aguardando conexão.", pending))
            }
        }
        dev.viko.listaanimes.ui.components.PagerSegmented(pager, listOf(t("Anime"), t("Mangá")), Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                val k = MediaKind.entries[page]
                HomeList(nav, vm, k, if (k == MediaKind.ANIME) anime else manga, s)
            }
        }
    }
}

@Composable
private fun HomeList(nav: Nav, vm: HomeViewModel, kind: MediaKind, list: List<EntryWithMedia>?, s: dev.viko.listaanimes.data.settings.Settings) {
    // Abas por status, na ordem escolhida nas Configurações (Listas e títulos).
    val tabs = s.homeTabs
    var chosen by androidx.compose.runtime.saveable.rememberSaveable(kind) { androidx.compose.runtime.mutableStateOf<ListStatus?>(null) }
    // Sem escolha (ou se a aba escolhida foi tirada), abre na primeira da ordem.
    val selected = chosen?.takeIf { it in tabs } ?: tabs.first()
    // Agrupar e ordenar só quando a lista muda (não a cada recomposição).
    val groupedMemo = remember(list, tabs) {
        val now = nowEpoch()
        list?.let { l ->
            tabs.associateWith { st ->
                val rows = l.filter { it.entry.status == st && it.media != null }
                if (st == ListStatus.PLANNING) rows.sortedByDescending { it.entry.updatedAt }
                else rows.sortedWith(
                    compareByDescending<EntryWithMedia> { ProgressRules.episodesBehind(it.entry, it.media!!, now) > 0 }
                        .thenByDescending { it.entry.updatedAt },
                )
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            LinkRow(if (kind == MediaKind.ANIME) t("Lista completa de anime") else t("Lista completa de mangá"), list?.size?.toString()) { nav.list(kind) }
            ThinDivider()
        }
        val grouped = groupedMemo
        if (list == null || grouped == null) { item { dev.viko.listaanimes.ui.components.SkeletonRows() }; return@LazyColumn }
        item(key = "tabs") {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(tabs.size) { i ->
                    val st = tabs[i]
                    StatusTab(st, kind, grouped[st].orEmpty().size, st == selected) { chosen = st }
                }
            }
        }
        val items = grouped[selected].orEmpty()
        if (items.isEmpty()) item(key = "empty-$selected") { EmptyState(t("Nada aqui.")) }
        if (s.gridLayout) {
            itemsIndexed(items.chunked(3), key = { _, it -> "g-${selected.name}-${it.first().entry.mediaId}" }) { i, rowItems ->
                Box(Modifier.animateItem().enterAnimation(i)) {
                    dev.viko.listaanimes.ui.components.GridRow(rowItems, 3) { row ->
                        val k = dev.viko.listaanimes.ui.components.coverKey("home", row.entry.mediaId)
                        dev.viko.listaanimes.ui.components.EntryGridCell(
                            row.entry, row.media!!, row.special?.position, k, showPlusOne = true,
                            onClick = { nav.detail(row.entry.mediaId, k) },
                            onIncrement = { vm.increment(row.entry.mediaId) },
                            onSetProgress = { vm.setProgress(row.entry.mediaId, it) },
                        )
                    }
                }
            }
        } else {
            itemsIndexed(items, key = { _, it -> "${selected.name}-${it.entry.mediaId}" }) { i, row ->
                val k = dev.viko.listaanimes.ui.components.coverKey("home", row.entry.mediaId)
                Column(Modifier.animateItem().enterAnimation(i)) {
                    EntryRow(
                        entry = row.entry, media = row.media!!, special = row.special?.position,
                        showScore = false, showPlusOne = true, coverKey = k,
                        onClick = { nav.detail(row.entry.mediaId, k) },
                        onIncrement = { vm.increment(row.entry.mediaId) },
                        onSetProgress = { vm.setProgress(row.entry.mediaId, it) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Aba de status: ícone, nome e quantidade; a escolhida fica na cor do status. */
@Composable
private fun StatusTab(st: ListStatus, kind: MediaKind, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = dev.viko.listaanimes.ui.theme.AppTheme.palette
    val col = dev.viko.listaanimes.ui.theme.statusColor(st)
    val bg by androidx.compose.animation.animateColorAsState(if (selected) col.copy(alpha = 0.2f) else p.surfaceAlt, label = "tabbg")
    Row(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(bg)
            .pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.viko.listaanimes.ui.components.StatusIcon(st, kind, if (selected) col else p.textSecondary, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(st.label(kind), style = MaterialTheme.typography.labelLarge, color = if (selected) col else p.text)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = dev.viko.listaanimes.ui.theme.Mono.style(12), color = if (selected) col else p.textTertiary)
    }
}
