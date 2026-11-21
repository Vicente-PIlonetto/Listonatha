package dev.viko.listaanimes.ui.screens

import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.ui.graphics.graphicsLayer
import dev.viko.listaanimes.ui.components.enterAnimation
import androidx.compose.ui.draw.clip
import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.repo.CommentJson
import dev.viko.listaanimes.data.repo.CommentNode
import dev.viko.listaanimes.data.repo.ForumCategories
import dev.viko.listaanimes.data.repo.LikeTarget
import dev.viko.listaanimes.data.repo.Paged
import dev.viko.listaanimes.data.repo.ThreadItem
import dev.viko.listaanimes.ui.components.Avatar
import dev.viko.listaanimes.ui.components.Cover
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.LikeButton
import dev.viko.listaanimes.ui.components.LocalViewerId
import dev.viko.listaanimes.ui.components.MarkdownView
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.ReplyBar
import dev.viko.listaanimes.ui.components.Tag
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.UserLine
import dev.viko.listaanimes.ui.components.timeAgo
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Filtro do fórum: categoria, "Inscritos" ou obra. */
data class ForumFilter(val categoryId: Int? = null, val subscribed: Boolean = false, val search: String = "")

@Composable
fun ForumScreen(nav: Nav, mediaId: Int?, mediaTitle: String?) {
    var filter by remember { mutableStateOf(ForumFilter()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(q) { kotlinx.coroutines.delay(450); filter = filter.copy(search = q.trim()) }
    val vm = appViewModel(key = "forum-$mediaId-$filter") { c ->
        PagedViewModel<ThreadItem> { page -> c.social.threads(page, filter.categoryId, mediaId, filter.search, filter.subscribed) }
    }
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(vm) { vm.start() }
    OnEndReached(state) { vm.nextPage() }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (mediaTitle != null) t("Fórum · {0}", mediaTitle) else t("Fórum"), onBack = nav::back)
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(t("Buscar tópicos")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(4.dp))
        if (mediaId == null && !dev.viko.listaanimes.ui.components.LocalSettings.current.forumByCategory) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(t("Recentes"), filter.categoryId == null && !filter.subscribed, { filter = filter.copy(categoryId = null, subscribed = false) })
            Pill(t("Inscritos"), filter.subscribed, { filter = filter.copy(subscribed = !filter.subscribed, categoryId = null) })
            ForumCategories.all.forEach { (id, name) ->
                Pill(name, filter.categoryId == id, { filter = filter.copy(categoryId = if (filter.categoryId == id) null else id, subscribed = false) }, icon = categoryIcon(id))
            }
        }
        // Modo "por categoria" (opcional): primeiro as categorias, depois os tópicos de cada uma.
        val byCategory = dev.viko.listaanimes.ui.components.LocalSettings.current.forumByCategory && mediaId == null
        val showGrid = byCategory && filter.categoryId == null && !filter.subscribed && filter.search.isBlank()
        androidx.activity.compose.BackHandler(enabled = byCategory && filter.categoryId != null) { filter = filter.copy(categoryId = null) }
        if (showGrid) {
            ForumCategoryGrid { id -> filter = filter.copy(categoryId = id) }
            return@Column
        }
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                error?.let { item { MessageBar(it) } }
                items(items, key = { it.id }) { t ->
                    Column(Modifier.animateItem()) { ThreadRowView(t) { nav.thread(t.id) }; ThinDivider() }
                }
                if (!loading && items.isEmpty() && error == null) item { EmptyState(t("Nenhum tópico.")) }
                if (loading && !refreshing) item { dev.viko.listaanimes.ui.components.SkeletonActivities(if (items.isEmpty()) 6 else 1) }
            }
        }
    }
}

@Composable
private fun ThreadRowView(t: ThreadItem, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (t.mediaCover != null) {
            Cover(t.mediaCover, 34.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.isSticky) { Icon(Icons.Outlined.PushPin, t("Fixado"), tint = p.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                if (t.isLocked) { Icon(Icons.Outlined.Lock, t("Fechado"), tint = p.textTertiary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                Text(t.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    t.categories.firstOrNull(),
                    t.user?.name,
                    t.repliedAt?.let { t("resposta há {0}", timeAgo(it)) + (t.replyUser?.let { u -> t(" de {0}", u) } ?: "") } ?: t("há {0}", timeAgo(t.createdAt)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${t.replyCount}", style = Mono.style(13), color = p.text)
            Text(t("resp."), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
        }
    }
}

class ThreadViewModel(private val c: AppContainer, val id: Int) : ViewModel() {
    val thread = MutableStateFlow<ThreadItem?>(null)
    val comments = MutableStateFlow<List<CommentNode>>(emptyList())
    val hasMore = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val sending = MutableStateFlow(false)
    private var page = 0

    init { reload() }

    fun reload() = viewModelScope.launch {
        loading.value = true
        try {
            thread.value = c.social.thread(id)
            val r = c.social.comments(id, 1)
            page = 1; comments.value = r.items; hasMore.value = r.hasNext; error.value = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.toUserMessage() } finally { loading.value = false }
    }

    fun more() {
        if (loading.value || !hasMore.value) return
        viewModelScope.launch {
            loading.value = true
            try { val r = c.social.comments(id, page + 1); page++; comments.value = comments.value + r.items; hasMore.value = r.hasNext }
            catch (e: Exception) { error.value = e.toUserMessage() } finally { loading.value = false }
        }
    }

    private fun mapTree(list: List<CommentNode>, f: (CommentNode) -> CommentNode?): List<CommentNode> =
        list.mapNotNull { n -> f(n)?.let { it.copy(children = mapTree(it.children, f)) } }

    fun likeComment(n: CommentNode) = viewModelScope.launch {
        val toggle = { x: CommentNode -> if (x.id == n.id) x.copy(isLiked = !x.isLiked, likeCount = x.likeCount + if (x.isLiked) -1 else 1) else x }
        comments.value = mapTree(comments.value, toggle)
        try { c.social.toggleLike(n.id, LikeTarget.THREAD_COMMENT) } catch (e: Exception) {
            comments.value = mapTree(comments.value, toggle); error.value = t("Não foi possível curtir. Tente de novo.")
        }
    }

    fun likeThread() = viewModelScope.launch {
        val t = thread.value ?: return@launch
        thread.value = t.copy(isLiked = !t.isLiked, likeCount = t.likeCount + if (t.isLiked) -1 else 1)
        try { c.social.toggleLike(t.id, LikeTarget.THREAD) } catch (e: Exception) { thread.value = t; error.value = t("Não foi possível curtir. Tente de novo.") }
    }

    fun send(text: String, parent: CommentNode?) = viewModelScope.launch {
        sending.value = true
        try { c.social.comment(id, parent?.id, text); reloadAll() } catch (e: Exception) {
            error.value = t("Não foi possível comentar. Tente de novo.")
        } finally { sending.value = false }
    }

    fun delete(n: CommentNode) = viewModelScope.launch {
        try { c.social.deleteComment(n.id); comments.value = mapTree(comments.value) { if (it.id == n.id) null else it } }
        catch (e: Exception) { error.value = t("Não foi possível apagar. Tente de novo.") }
    }

    /** Recarrega as páginas já abertas (o novo comentário aparece no lugar certo). */
    private suspend fun reloadAll() {
        val pages = page.coerceAtLeast(1)
        val all = mutableListOf<CommentNode>()
        var next = false
        for (i in 1..pages) { val r = c.social.comments(id, i); all += r.items; next = r.hasNext }
        comments.value = all; hasMore.value = next
        thread.value = thread.value?.let { it.copy(replyCount = it.replyCount + 1) }
    }
}

@Composable
fun ThreadScreen(nav: Nav, id: Int) {
    val vm = appViewModel(key = "thread-$id") { ThreadViewModel(it, id) }
    val t by vm.thread.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val me = LocalViewerId.current
    val p = AppTheme.palette
    var replyTo by remember { mutableStateOf<CommentNode?>(null) }
    var confirm by remember { mutableStateOf<CommentNode?>(null) }
    val state = rememberLazyListState()
    OnEndReached(state, buffer = 3) { vm.more() }
    val flat = remember(comments) { CommentJson.flatten(comments) }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Tópico"), onBack = nav::back)
        error?.let { MessageBar(it) }
        dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = loading && t != null && comments.isEmpty(), onRefresh = { vm.reload() }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                t?.let { th ->
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(th.title, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                th.categories.forEach { Tag(it) }
                                th.mediaTitle?.let { mt -> Box(Modifier.clickable { th.mediaId?.let { nav.detail(it) } }) { Tag(mt, accent = true) } }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(th.user?.avatar, 28.dp, Modifier.clickable { th.user?.let { nav.profile(it.id) } })
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { UserLine(th.user, th.createdAt, { nav.profile(it.id) }) }
                            }
                            Spacer(Modifier.height(10.dp))
                            MarkdownView(th.body.orEmpty())
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(t("{0} respostas · {1} visualizações", th.replyCount, th.viewCount), style = Mono.style(12), color = p.textTertiary, modifier = Modifier.weight(1f))
                                LikeButton(th.isLiked, th.likeCount, vm::likeThread)
                            }
                        }
                        ThinDivider()
                    }
                }
                items(flat, key = { it.id }) { n ->
                    CommentView(
                        n, mine = n.user?.id == me,
                        onUser = { nav.profile(it) }, onLike = { vm.likeComment(n) },
                        onReply = { replyTo = n }, onDelete = { confirm = n },
                    )
                }
                if (loading) item { dev.viko.listaanimes.ui.components.SkeletonActivities(if (flat.isEmpty()) 5 else 1) }
                else if (!hasMore && t != null && flat.isEmpty()) item { EmptyState(t("Nenhum comentário.")) }
            }
        }
        val th = t
        if (th != null) {
            if (th.isLocked) Text(t("Tópico fechado para comentários."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(20.dp))
            else ReplyBar(
                placeholder = t("Comentar"), sending = sending,
                replyingTo = replyTo?.user?.name, onCancelReply = { replyTo = null },
            ) { text -> vm.send(text, replyTo); replyTo = null }
        }
    }
    confirm?.let { n -> ConfirmDelete({ confirm = null }) { vm.delete(n); confirm = null } }
}

@Composable
private fun CommentView(n: CommentNode, mine: Boolean, onUser: (Int) -> Unit, onLike: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    val p = AppTheme.palette
    val indent = (n.depth.coerceAtMost(4) * 14).dp
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp + indent, end = 12.dp)) {
        if (n.depth > 0) {
            Box(Modifier.width(1.5.dp).fillMaxHeight().background(p.divider))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(n.user?.avatar, 24.dp, Modifier.clickable { n.user?.let { onUser(it.id) } })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { UserLine(n.user, n.createdAt, { onUser(it.id) }) }
            }
            MarkdownView(n.text, Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (mine) TextButton(onClick = onDelete) { Text(t("Apagar"), color = p.textTertiary, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = onReply) { Text(t("Responder"), color = p.textSecondary, style = MaterialTheme.typography.labelMedium) }
                LikeButton(n.isLiked, n.likeCount, onLike)
            }
        }
    }
}

/** Ícone de cada categoria do fórum (id do AniList). */
private fun categoryIcon(id: Int): androidx.compose.ui.graphics.vector.ImageVector {
    return when (id) {
        7 -> androidx.compose.material.icons.Icons.Outlined.Forum
        1 -> androidx.compose.material.icons.Icons.Outlined.LiveTv
        2 -> androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook
        5 -> androidx.compose.material.icons.Icons.Outlined.NewReleases
        15 -> androidx.compose.material.icons.Icons.Outlined.ThumbUp
        3 -> androidx.compose.material.icons.Icons.Outlined.AutoStories
        4 -> androidx.compose.material.icons.Icons.Outlined.Gamepad
        8 -> androidx.compose.material.icons.Icons.Outlined.Newspaper
        9 -> androidx.compose.material.icons.Icons.Outlined.MusicNote
        10 -> androidx.compose.material.icons.Icons.Outlined.SportsEsports
        16 -> androidx.compose.material.icons.Icons.Outlined.Extension
        19 -> androidx.compose.material.icons.Icons.Outlined.Event
        11 -> androidx.compose.material.icons.Icons.Outlined.Lightbulb
        12 -> androidx.compose.material.icons.Icons.Outlined.BugReport
        13 -> androidx.compose.material.icons.Icons.Outlined.Campaign
        14 -> androidx.compose.material.icons.Icons.Outlined.Tune
        18 -> androidx.compose.material.icons.Icons.Outlined.Apps
        else -> androidx.compose.material.icons.Icons.Outlined.Forum
    }
}

private val CATEGORY_COLORS = listOf(0xFF5B8DEF, 0xFFE0457B, 0xFF4CB782, 0xFFF2A65E, 0xFF9A8CF0, 0xFF3DB4F2, 0xFFEC6F82, 0xFFF0CC4F)

/** Categorias do fórum em cartões coloridos. */
@Composable
private fun ForumCategoryGrid(onPick: (Int) -> Unit) {
    val p = AppTheme.palette
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        val cats = ForumCategories.all.toList()
        items(cats.chunked(2).size) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cats.chunked(2)[r].forEachIndexed { j, (id, name) ->
                    val col = androidx.compose.ui.graphics.Color(CATEGORY_COLORS[(r * 2 + j) % CATEGORY_COLORS.size])
                    Box(
                        Modifier.weight(1f).padding(vertical = 5.dp).height(88.dp).clip(RoundedCornerShape(18.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(col.copy(alpha = 0.35f), col.copy(alpha = 0.12f))))
                            .clickable { onPick(id) }.padding(14.dp).enterAnimation(r),
                    ) {
                        val icon = categoryIcon(id)
                        Icon(icon, null, tint = col.copy(alpha = 0.25f),
                            modifier = Modifier.align(Alignment.TopEnd).size(46.dp).graphicsLayer { rotationZ = -12f })
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Icon(icon, null, tint = col, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
                        }
                    }
                }
                if (cats.chunked(2)[r].size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
