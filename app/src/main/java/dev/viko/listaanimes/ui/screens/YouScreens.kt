package dev.viko.listaanimes.ui.screens

import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.heightIn
import dev.viko.listaanimes.i18n.t
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.data.repo.AppNotification
import dev.viko.listaanimes.data.repo.Profile
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.score.Scores
import dev.viko.listaanimes.domain.score.SpecialGrade
import dev.viko.listaanimes.ui.components.Cover
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.NewDot
import dev.viko.listaanimes.ui.components.PosterRow
import dev.viko.listaanimes.ui.components.ScoreSize
import dev.viko.listaanimes.ui.components.ScoreView
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.nowEpoch
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class YouViewModel(private val c: AppContainer) : ViewModel() {
    val profile = c.media.observeCache("profile", Profile.serializer()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val entries = c.lists.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favAnime = c.db.media().observeFavourites(MediaKind.ANIME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favManga = c.db.media().observeFavourites(MediaKind.MANGA).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** Seguidores e seguindo. */
    val follows = MutableStateFlow<Pair<Int, Int>?>(null)

    init { loadFollows() }

    private fun loadFollows() = viewModelScope.launch {
        val id = c.settings.viewerIdNow() ?: profile.first { it != null }?.id ?: return@launch
        runCatching { follows.value = c.social.followCounts(id) }
    }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        try { c.lists.fullSync(); error.value = null; loadFollows() } catch (e: Exception) { error.value = SYNC_ERROR } finally { refreshing.value = false }
    }
}

/** Média local das notas (0–100), para mostrar no meu sistema. */
fun meanRaw(entries: List<EntryWithMedia>, kind: MediaKind): Int? =
    entries.filter { it.entry.kind == kind && it.entry.scoreRaw > 0 }.map { it.entry.scoreRaw }.takeIf { it.isNotEmpty() }?.average()?.toInt()

@Composable
fun YouScreen(nav: Nav) {
    val vm = appViewModel { YouViewModel(it) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val favAnime by vm.favAnime.collectAsStateWithLifecycle()
    val favManga by vm.favManga.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val follows by vm.follows.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val style by c.settings.profileStyle.collectAsStateWithLifecycle(initialValue = dev.viko.listaanimes.data.settings.ProfileStyle())
    val pt = dev.viko.listaanimes.i18n.I18n.locale

    dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head") {
                val a = entries.count { it.entry.kind == MediaKind.ANIME }
                val m = entries.count { it.entry.kind == MediaKind.MANGA }
                dev.viko.listaanimes.ui.components.ProfileHeader(
                    name = profile?.name ?: t("Você"),
                    avatar = dev.viko.listaanimes.ui.components.resolveAvatar(ctx, style, profile?.avatar),
                    banner = dev.viko.listaanimes.ui.components.resolveBanner(ctx, style, profile?.banner),
                    color = dev.viko.listaanimes.data.settings.ProfileColor.byId(style.colorId),
                    animated = style.animatedBg,
                    subtitle = t("{0} animes · {1} mangás", a, m),
                    trailing = {
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = { nav.customize() },
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = p.accent.copy(alpha = 0.18f), contentColor = p.accent),
                        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Palette, t("Personalizar")) }
                    },
                    below = {
                        profile?.let { pr ->
                            dev.viko.listaanimes.ui.components.FollowStats(
                                follows?.first, follows?.second,
                                onFollowers = { nav.follows(pr.id, true) }, onFollowing = { nav.follows(pr.id, false) },
                            )
                        }
                    },
                )
                error?.let { MessageBar(it) }
            }
            item(key = "stats") {
                val pr = profile
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).enterAnimation(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(t("Dias assistidos"), pr?.let { "%.1f".format(pt, it.minutesWatched / 1440.0) }, androidx.compose.material.icons.Icons.Outlined.Schedule, Modifier.weight(1f))
                        StatTile(t("Episódios"), pr?.episodesWatched?.toString(), androidx.compose.material.icons.Icons.Outlined.PlayCircle, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(t("Capítulos"), pr?.chaptersRead?.toString(), androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook, Modifier.weight(1f))
                        StatTile(t("Volumes"), pr?.volumesRead?.toString(), androidx.compose.material.icons.Icons.Outlined.CollectionsBookmark, Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Média anime"), style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                            meanRaw(entries, MediaKind.ANIME)?.let { ScoreView(it, null, s.primaryScore, s.secondaryScore, ScoreSize.Normal) } ?: Dash()
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t("Média mangá"), style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                            meanRaw(entries, MediaKind.MANGA)?.let { ScoreView(it, null, s.primaryScore, s.secondaryScore, ScoreSize.Normal) } ?: Dash()
                        }
                    }
                    if (pr != null) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt)
                            .clickable { nav.stats(pr.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.BarChart, null, tint = p.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(t("Ver estatísticas"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary)
                    }
                }
            }
            item(key = "bio") { BioCard(profile) }
            val chars = profile?.favCharacters.orEmpty()
            if (chars.isNotEmpty() && style.showFavCharacters) item(key = "fc") {
                var edit by remember { androidx.compose.runtime.mutableStateOf(false) }
                val shown = chars.filter { it.id !in style.hiddenCharacters }
                SectionHeader(t("Personagens favoritos"), shown.size, action = t("editar"), onAction = { edit = true })
                if (shown.isNotEmpty()) dev.viko.listaanimes.ui.components.FavCharacterRow(shown) { nav.character(it) }
                if (edit) FavCharactersDialog(chars, style, onDismiss = { edit = false }) { c.settings.setProfileStyle(it) }
            }
            if (favAnime.isNotEmpty()) item(key = "fa") {
                SectionHeader(t("Animes favoritos"), favAnime.size)
                PosterRow(favAnime, "fa", filter = false) { m, k -> nav.detail(m.id, k) }
            }
            if (favManga.isNotEmpty()) item(key = "fm") {
                SectionHeader(t("Mangás favoritos"), favManga.size)
                PosterRow(favManga, "fm", filter = false) { m, k -> nav.detail(m.id, k) }
            }
            item(key = "links") {
                Column(Modifier.padding(top = 12.dp).enterAnimation(2)) {
                    profile?.let { pr ->
                        LinkRow(t("Meu perfil público"), icon = androidx.compose.material.icons.Icons.Outlined.AccountCircle, iconColor = androidx.compose.ui.graphics.Color(0xFF5B9BF0)) { nav.profile(pr.id) }
                    }
                    LinkRow(t("Minhas reviews"), icon = androidx.compose.material.icons.Icons.Outlined.RateReview, iconColor = androidx.compose.ui.graphics.Color(0xFFE88A4F)) { nav.myReviews() }
                    LinkRow(t("Histórico"), icon = androidx.compose.material.icons.Icons.Outlined.History, iconColor = androidx.compose.ui.graphics.Color(0xFF4CB782)) { nav.history() }
                    LinkRow(t("Notificações"), icon = androidx.compose.material.icons.Icons.Outlined.Notifications, iconColor = androidx.compose.ui.graphics.Color(0xFFE05C8A)) { nav.notifications() }
                    LinkRow(t("Configurações"), icon = androidx.compose.material.icons.Icons.Outlined.Settings, iconColor = androidx.compose.ui.graphics.Color(0xFF9A8CF0)) { nav.settings() }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (value == null) Dash() else Text(value, style = Mono.style(17, FontWeight.SemiBold))
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
        }
    }
}

@Composable private fun Dash() = Text("–", style = Mono.style(14), color = AppTheme.palette.textTertiary)
@Composable private fun Num(v: String?) = if (v == null) Dash() else Text(v, style = Mono.style(14, FontWeight.Medium))

@Composable
private fun StatRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.weight(1f))
        value()
    }
}

// ---------------- Histórico ----------------

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val entries = c.lists.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun HistoryScreen(nav: Nav) {
    val vm = appViewModel { HistoryViewModel(it) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val pt = dev.viko.listaanimes.i18n.I18n.locale
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Histórico"), onBack = nav::back)
        val all = entries ?: return@Column
        // Tudo acima de A++ (95–100), com ou sem nota especial guardada.
        val specials = all.filter { e -> e.entry.scoreRaw >= 95 && e.media != null }
            .sortedByDescending { Scores.sortKey(it.entry.scoreRaw, it.special?.position) }
        val completed = all.filter { it.entry.status == ListStatus.COMPLETED && it.media != null }
        val byMonth = completed.groupBy { e -> e.entry.completedAt.let { d -> if (d.year != null && d.month != null) YearMonth.of(d.year, d.month) else null } }
        val months = byMonth.keys.filterNotNull().sortedDescending()
        LazyColumn(Modifier.fillMaxSize()) {
            if (specials.isNotEmpty()) {
                item { SectionHeader(t("Notas especiais"), specials.size) }
                items(specials, key = { "sp-${it.entry.mediaId}" }) { HistoryRow(it) { nav.detail(it.entry.mediaId) } }
            }
            if (months.isEmpty() && specials.isEmpty()) item { EmptyState(t("Nada completo ainda.")) }
            months.forEach { ym ->
                val rows = byMonth[ym].orEmpty().sortedByDescending { it.entry.completedAt.toLocalDate() }
                val eps = rows.filter { it.entry.kind == MediaKind.ANIME }.sumOf { it.entry.progress }
                val chs = rows.filter { it.entry.kind == MediaKind.MANGA }.sumOf { it.entry.progress }
                item(key = "m-$ym") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)) {
                        Text(ym.format(DateTimeFormatter.ofPattern(t("MMMM 'de' yyyy"), pt)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(t("{0} obras", rows.size), eps.takeIf { it > 0 }?.let { t("{0} ep.", it) }, chs.takeIf { it > 0 }?.let { t("{0} cap.", it) }).joinToString(" · "),
                            style = Mono.style(12), color = p.textSecondary)
                    }
                }
                items(rows, key = { "c-${it.entry.mediaId}" }) { HistoryRow(it) { nav.detail(it.entry.mediaId) } }
            }
            val undated = byMonth[null].orEmpty()
            if (undated.isNotEmpty()) {
                item { SectionHeader(t("Sem data"), undated.size) }
                items(undated, key = { "u-${it.entry.mediaId}" }) { HistoryRow(it) { nav.detail(it.entry.mediaId) } }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: EntryWithMedia, onClick: () -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val m = row.media!!
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(m.coverMedium ?: m.coverLarge, 34.dp, placeholder = m.coverColor, adult = m.isAdult)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title(s.titleLanguage), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.entry.completedAt.format().ifEmpty { "–" }, style = Mono.style(12), color = p.textTertiary)
        }
        if (row.entry.scoreRaw > 0) ScoreView(row.entry.scoreRaw, row.special?.position, s.primaryScore, null, ScoreSize.Small)
    }
}

// ---------------- Notificações ----------------

class NotificationsViewModel(private val c: AppContainer) : ViewModel() {
    val list = c.notifications.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seenBefore = MutableStateFlow(Long.MAX_VALUE)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            seenBefore.value = c.notifications.seenAt.first()
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        loading.value = true
        try { c.notifications.refresh(markRead = true); error.value = null } catch (e: Exception) { error.value = e.toUserMessage() } finally { loading.value = false }
    }
}

@Composable
fun NotificationsScreen(nav: Nav) {
    val vm = appViewModel { NotificationsViewModel(it) }
    val list by vm.list.collectAsStateWithLifecycle()
    val seen by vm.seenBefore.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Notificações"), onBack = nav::back)
        error?.let { MessageBar(it) }
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = loading, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (list.isEmpty() && !loading) item { EmptyState(t("Nenhuma notificação.")) }
                items(list, key = { it.id }) { n ->
                    NotificationRow(n, unread = n.createdAt > seen) {
                        when {
                            n.media != null -> nav.detail(n.media.id)
                            n.activityId != null -> nav.activity(n.activityId)
                            n.threadId != null -> nav.thread(n.threadId)
                            n.user != null -> nav.profile(n.user.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, unread: Boolean, onClick: () -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 6.dp, end = 20.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) { if (unread) NewDot() }
        if (n.kind == "SOCIAL") dev.viko.listaanimes.ui.components.Avatar(n.user?.avatar, 36.dp)
        else Cover(n.media?.coverMedium, 36.dp, placeholder = n.media?.coverColor, adult = n.media?.isAdult == true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            n.media?.let { Text(it.title(s.titleLanguage), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(n.text, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
        Text(ago(n.createdAt), style = Mono.style(12), color = p.textTertiary)
    }
}

fun ago(epoch: Long): String {
    val d = nowEpoch() - epoch
    return when {
        d < 3600 -> t("{0} min", (d / 60).coerceAtLeast(1))
        d < 86400 -> "${d / 3600} h"
        d < 30 * 86400 -> "${d / 86400} d"
        else -> t("{0} mês", d / (30 * 86400))
    }
}

/** Bio do AniList: lida em markdown; dá para editar quando há login. */
@Composable
private fun BioCard(profile: Profile?) {
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val p = AppTheme.palette
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var saving by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val about = profile?.about.orEmpty()
    val canEdit = c.auth.token() != null && profile != null
    if (about.isBlank() && !canEdit) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceAlt).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Sobre mim"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (canEdit) androidx.compose.material3.IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, t("Editar"), tint = p.accent, modifier = Modifier.size(18.dp))
            }
        }
        if (about.isBlank()) Text(t("Sem bio."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        else {
            androidx.compose.foundation.layout.Box(Modifier.then(if (!expanded) Modifier.heightIn(max = 160.dp).clipToBounds() else Modifier)) {
                dev.viko.listaanimes.ui.components.MarkdownView(about)
            }
            if (about.length > 300) Text(
                if (expanded) t("Mostrar menos") else t("Ler tudo"), style = MaterialTheme.typography.labelLarge, color = p.accent,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    if (editing && profile != null) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(about) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!saving) editing = false },
            title = { Text(t("Sobre mim")) },
            text = {
                androidx.compose.material3.OutlinedTextField(text, { text = it }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(t("Markdown, img(url), @usuário…")) })
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            c.social.setAbout(text)
                            c.db.cache().put(dev.viko.listaanimes.data.db.CacheBlob("profile",
                                kotlinx.serialization.json.Json.encodeToString(Profile.serializer(), profile.copy(about = text)), System.currentTimeMillis() / 1000))
                            error = null; editing = false
                        } catch (e: Exception) { error = e.toUserMessage(t("Não foi possível salvar a bio.")) } finally { saving = false }
                    }
                }) { Text(if (saving) t("Salvando…") else t("Salvar")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text(t("Cancelar")) } },
        )
    }
}

/** Escolher se a seção aparece e quais personagens mostrar. */
@Composable
private fun FavCharactersDialog(
    chars: List<dev.viko.listaanimes.data.repo.FavCharacter>,
    style: dev.viko.listaanimes.data.settings.ProfileStyle,
    onDismiss: () -> Unit,
    onSave: suspend (dev.viko.listaanimes.data.settings.ProfileStyle) -> Unit,
) {
    val p = AppTheme.palette
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var show by remember { androidx.compose.runtime.mutableStateOf(style.showFavCharacters) }
    var hidden by remember { androidx.compose.runtime.mutableStateOf(style.hiddenCharacters) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Personagens favoritos")) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { show = !show }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Mostrar no perfil"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(show, { show = it })
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(chars, key = { it.id }) { ch ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = show) { hidden = if (ch.id in hidden) hidden - ch.id else hidden + ch.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = ch.id !in hidden, onCheckedChange = null, enabled = show)
                            Spacer(Modifier.width(8.dp))
                            AsyncImage(ch.image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt))
                            Spacer(Modifier.width(10.dp))
                            Text(ch.name, style = MaterialTheme.typography.bodyMedium, color = if (show) p.text else p.textTertiary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                scope.launch { onSave(style.copy(showFavCharacters = show, hiddenCharacters = hidden)) }
                onDismiss()
            }) { Text(t("Salvar")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("Cancelar")) } },
    )
}
