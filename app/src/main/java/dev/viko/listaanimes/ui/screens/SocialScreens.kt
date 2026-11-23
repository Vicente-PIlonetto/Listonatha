package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.data.repo.ActivityItem
import dev.viko.listaanimes.data.repo.LikeTarget
import dev.viko.listaanimes.data.repo.ReplyItem
import dev.viko.listaanimes.data.repo.UserProfileData
import dev.viko.listaanimes.data.repo.UserRef
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.ui.components.ActivityRow
import dev.viko.listaanimes.ui.components.Avatar
import androidx.compose.material.icons.outlined.Edit
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.components.EmptyState
import dev.viko.listaanimes.ui.components.EntryRow
import dev.viko.listaanimes.ui.components.LikeButton
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalViewerId
import dev.viko.listaanimes.ui.components.MarkdownView
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.PosterRow
import dev.viko.listaanimes.ui.components.ReplyBar
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.Segmented
import dev.viko.listaanimes.ui.components.StatusIcon
import dev.viko.listaanimes.ui.components.Tag
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.components.UserLine
import dev.viko.listaanimes.ui.components.UserRow
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import dev.viko.listaanimes.ui.theme.tinted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Curtir com atualização otimista; desfaz se falhar. */
fun ActivityItem.toggled() = copy(isLiked = !isLiked, likeCount = likeCount + if (isLiked) -1 else 1)
fun ReplyItem.toggled() = copy(isLiked = !isLiked, likeCount = likeCount + if (isLiked) -1 else 1)

class FeedViewModel(private val c: AppContainer, following: Boolean, userId: Int? = null) :
    PagedViewModel<ActivityItem>({ page -> c.social.feed(page, following, userId) }) {
    val message = MutableStateFlow<String?>(null)

    fun like(item: ActivityItem) = viewModelScope.launch {
        update { if (it.id == item.id) it.toggled() else it }
        try { c.social.toggleLike(item.id, LikeTarget.ACTIVITY) } catch (e: Exception) {
            update { if (it.id == item.id) item else it }
            message.value = t("Não foi possível curtir. Tente de novo.")
        }
    }

    fun post(text: String, onDone: () -> Unit) = viewModelScope.launch {
        try { c.social.postStatus(text); onDone(); refresh() } catch (e: Exception) {
            message.value = t("Não foi possível publicar. Tente de novo.")
        }
    }
}

@Composable
fun FeedList(vm: FeedViewModel, nav: Nav, header: @Composable () -> Unit = {}) {
    val items by vm.items.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(vm) { vm.start() }
    OnEndReached(state) { vm.nextPage() }
    dev.viko.listaanimes.ui.components.AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            item { header() }
            error?.let { item { MessageBar(it) } }
            message?.let { item { MessageBar(it, t("OK")) { vm.message.value = null } } }
            items(items, key = { it.id }) { a ->
                Column(Modifier.animateItem()) {
                    ActivityRow(
                        a, expanded = false,
                        onUser = { nav.profile(it.id) },
                        onMedia = { m, k -> nav.detail(m.id, k) },
                        onOpen = { nav.activity(a.id) },
                        onLike = { vm.like(a) },
                    )
                }
            }
            if (!loading && items.isEmpty() && error == null) item { EmptyState(t("Nenhuma atividade.")) }
            if (loading && !refreshing) item { dev.viko.listaanimes.ui.components.SkeletonActivities(if (items.isEmpty()) 6 else 1) }
        }
    }
}

@Composable
fun SocialScreen(nav: Nav) {
    val following = appViewModel(key = "feed-following") { FeedViewModel(it, following = true) }
    val global = appViewModel(key = "feed-global") { FeedViewModel(it, following = false) }
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    var compose by remember { mutableStateOf(false) }
    val p = AppTheme.palette
    val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val profile by remember { c.media.observeCache("profile", dev.viko.listaanimes.data.repo.Profile.serializer()) }.collectAsStateWithLifecycle(initialValue = null)
    val style by c.settings.profileStyle.collectAsStateWithLifecycle(initialValue = dev.viko.listaanimes.data.settings.ProfileStyle())
    val myAvatar = dev.viko.listaanimes.ui.components.resolveAvatar(ctx, style, profile?.avatar)
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Social")) {
            IconButton(onClick = nav::userSearch) { Icon(Icons.Outlined.PersonSearch, t("Buscar pessoas")) }
        }
        dev.viko.listaanimes.ui.components.PagerSegmented(pager, listOf(t("Seguindo"), t("Global")), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            FeedList(if (page == 0) following else global, nav) {
                Column(Modifier.padding(top = 6.dp)) {
                    // Atalhos grandes: fórum e pessoas.
                    Row(Modifier.padding(horizontal = 12.dp).enterAnimation(0), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SocialShortcut(
                            t("Fórum"), t("Tópicos e discussões"), Icons.Outlined.Forum,
                            listOf(p.accent, p.accent.copy(alpha = 0.55f)), Modifier.weight(1.25f),
                        ) { nav.forum() }
                        SocialShortcut(
                            t("Pessoas"), t("Buscar e seguir"), Icons.Outlined.PersonSearch,
                            listOf(androidx.compose.ui.graphics.Color(0xFF5B8DEF), androidx.compose.ui.graphics.Color(0xFF8A6CF0)), Modifier.weight(1f),
                        ) { nav.userSearch() }
                    }
                    // Escrever um status.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).enterAnimation(1)
                            .clip(RoundedCornerShape(18.dp)).background(p.surfaceAlt).clickable { compose = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(myAvatar, 34.dp, Modifier.clip(androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(t("Escreva um status…"), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Edit, null, tint = p.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (compose) PostStatusSheet(onDismiss = { compose = false }) { text ->
        (if (pager.currentPage == 0) following else global).post(text) { compose = false }
    }
}

@Composable
private fun SocialShortcut(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: List<androidx.compose.ui.graphics.Color>, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors))
            .pressable(onClick = onClick),
    ) {
        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 2.dp).size(64.dp).graphicsLayer { rotationZ = -12f })
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
fun PostStatusSheet(onDismiss: () -> Unit, onPost: (String) -> Unit) {
    val p = AppTheme.palette
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp), containerColor = p.background, dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Novo status"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Segmented(listOf(t("Escrever"), t("Visualizar")), if (preview) 1 else 0, { preview = it == 1 })
            }
            Spacer(Modifier.height(12.dp))
            if (preview) Box(Modifier.height(200.dp)) { MarkdownView(text) }
            else OutlinedTextField(text, { text = it }, placeholder = { Text(t("Markdown, img(url), @usuário…")) },
                minLines = 6, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp))
            Spacer(Modifier.height(12.dp))
            Row {
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = text.isNotBlank(), onClick = { onPost(text) }, shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                ) { Text(t("Publicar")) }
            }
        }
    }
}

// ---------------- Atividade com respostas ----------------

class ActivityViewModel(private val c: AppContainer, val id: Int) : ViewModel() {
    val item = MutableStateFlow<ActivityItem?>(null)
    val replies = MutableStateFlow<List<ReplyItem>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val sending = MutableStateFlow(false)
    val deleted = MutableStateFlow(false)

    init { load() }

    fun load() = viewModelScope.launch {
        try { val (a, r) = c.social.activity(id); item.value = a; replies.value = r; error.value = null } catch (e: Exception) { error.value = e.toUserMessage() }
    }

    fun likeActivity() = viewModelScope.launch {
        val a = item.value ?: return@launch
        item.value = a.toggled()
        try { c.social.toggleLike(a.id, LikeTarget.ACTIVITY) } catch (e: Exception) { item.value = a; error.value = t("Não foi possível curtir. Tente de novo.") }
    }

    fun likeReply(r: ReplyItem) = viewModelScope.launch {
        replies.value = replies.value.map { if (it.id == r.id) it.toggled() else it }
        try { c.social.toggleLike(r.id, LikeTarget.ACTIVITY_REPLY) } catch (e: Exception) {
            replies.value = replies.value.map { if (it.id == r.id) r else it }; error.value = t("Não foi possível curtir. Tente de novo.")
        }
    }

    fun reply(text: String) = viewModelScope.launch {
        sending.value = true
        try {
            replies.value = replies.value + c.social.reply(id, text)
            item.value = item.value?.let { it.copy(replyCount = it.replyCount + 1) }
        } catch (e: Exception) { error.value = t("Não foi possível responder. Tente de novo.") } finally { sending.value = false }
    }

    fun deleteReply(r: ReplyItem) = viewModelScope.launch {
        try { c.social.deleteReply(r.id); replies.value = replies.value.filterNot { it.id == r.id } } catch (e: Exception) { error.value = t("Não foi possível apagar. Tente de novo.") }
    }

    fun deleteActivity() = viewModelScope.launch {
        try { c.social.deleteActivity(id); deleted.value = true } catch (e: Exception) { error.value = t("Não foi possível apagar. Tente de novo.") }
    }
}

@Composable
fun ActivityScreen(nav: Nav, id: Int) {
    val vm = appViewModel(key = "activity-$id") { ActivityViewModel(it, id) }
    val item by vm.item.collectAsStateWithLifecycle()
    val replies by vm.replies.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val me = LocalViewerId.current
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(deleted) { if (deleted) nav.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Atividade"), onBack = nav::back) {
            if (item?.user?.id != null && item?.user?.id == me) IconButton(onClick = { confirm = { vm.deleteActivity() } }) { Icon(Icons.Outlined.Delete, t("Apagar")) }
        }
        error?.let { MessageBar(it) }
        LazyColumn(Modifier.weight(1f)) {
            item?.let { a ->
                item {
                    ActivityRow(a, expanded = true, onUser = { nav.profile(it.id) }, onMedia = { m, k -> nav.detail(m.id, k) }, onOpen = {}, onLike = vm::likeActivity)
                    ThinDivider()
                }
            }
            items(replies, key = { it.id }) { r ->
                Column(Modifier.animateItem()) {
                    ReplyRow(r, mine = r.user?.id == me, onUser = { nav.profile(it.id) }, onLike = { vm.likeReply(r) }, onDelete = { confirm = { vm.deleteReply(r) } })
                    ThinDivider(Modifier.padding(start = 64.dp))
                }
            }
        }
        if (item != null) ReplyBar(t("Responder"), sending, onSend = vm::reply)
    }
    confirm?.let { action -> ConfirmDelete({ confirm = null }) { action(); confirm = null } }
}

@Composable
fun ConfirmDelete(onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(t("Apagar?")) },
    text = { Text(t("Não dá para desfazer.")) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(t("Apagar")) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancelar")) } },
    shape = RoundedCornerShape(6.dp),
)

@Composable
private fun ReplyRow(r: ReplyItem, mine: Boolean, onUser: (UserRef) -> Unit, onLike: () -> Unit, onDelete: () -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)) {
        Avatar(r.user?.avatar, 28.dp, Modifier.clickable { r.user?.let(onUser) })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            UserLine(r.user, r.createdAt, onUser)
            MarkdownView(r.text, Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (mine) TextButton(onClick = onDelete) { Text(t("Apagar"), color = p.textTertiary, style = MaterialTheme.typography.labelMedium) }
                LikeButton(r.isLiked, r.likeCount, onLike)
            }
        }
    }
}

// ---------------- Perfil ----------------

class ProfileViewModel(private val c: AppContainer, private val userId: Int?, private val name: String?) : ViewModel() {
    val profile = MutableStateFlow<UserProfileData?>(null)
    val error = MutableStateFlow<String?>(null)
    val list = MutableStateFlow<List<EntryWithMedia>?>(null)
    val listKind = MutableStateFlow(MediaKind.ANIME)
    private var feedVm: FeedViewModel? = null

    val follows = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Perfil secreto (easter egg): montado no app, nada de follow, feed ou lista no AniList. */
    // Abre pelo nome: a rota de perfil ignora ids <= 0.
    val secret = userId == dev.viko.listaanimes.data.repo.SecretProfile.ID || dev.viko.listaanimes.data.repo.SecretProfile.matches(name)

    init { load() }

    fun load() = viewModelScope.launch {
        if (secret) {
            profile.value = dev.viko.listaanimes.data.repo.SecretProfile.build(c)
            follows.value = 0 to 0
            return@launch
        }
        try {
            val pr = c.social.profile(userId, name)
            profile.value = pr; error.value = null
            runCatching { follows.value = c.social.followCounts(pr.id) }
        } catch (e: Exception) { error.value = e.toUserMessage() }
    }

    fun loadList(kind: MediaKind) = viewModelScope.launch {
        val id = profile.value?.id ?: return@launch
        listKind.value = kind
        if (secret) { list.value = emptyList(); return@launch }
        list.value = null
        try { list.value = c.social.userList(id, kind) } catch (e: Exception) { error.value = e.toUserMessage(); list.value = emptyList() }
    }

    fun toggleFollow() = viewModelScope.launch {
        val pr = profile.value ?: return@launch
        profile.value = pr.copy(isFollowing = !pr.isFollowing)
        if (secret) return@launch
        try { profile.value = pr.copy(isFollowing = c.social.toggleFollow(pr.id)) } catch (e: Exception) {
            profile.value = pr; error.value = t("Não foi possível seguir. Tente de novo.")
        }
    }
}

@Composable
fun ProfileScreen(nav: Nav, userId: Int?, name: String?) {
    val vm = appViewModel(key = "profile-$userId-$name") { ProfileViewModel(it, userId, name) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val follows by vm.follows.collectAsStateWithLifecycle()
    val me = LocalViewerId.current
    val p = AppTheme.palette
    var tab by remember { mutableStateOf(if (vm.secret) 2 else 0) }
    val pr = profile
    if (pr == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(name ?: t("Perfil"), onBack = nav::back)
            error?.let { MessageBar(it) } ?: dev.viko.listaanimes.ui.components.SkeletonProfile()
        }
        return
    }
    val feed = appViewModel(key = "feed-user-${pr.id}") { FeedViewModel(it, following = false, userId = pr.id) }
    val state = rememberLazyListState()
    val cc = dev.viko.listaanimes.ui.nav.LocalContainer.current
    val myStyleTop by cc.settings.profileStyle.collectAsStateWithLifecycle(initialValue = dev.viko.listaanimes.data.settings.ProfileStyle())
    val profileColor = (if (pr.id == me) dev.viko.listaanimes.data.settings.ProfileColor.byId(myStyleTop.colorId) else null)
        ?: dev.viko.listaanimes.data.settings.ProfileColor.fromAniList(pr.profileColor)
    val basePalette = AppTheme.palette
    val profilePalette = remember(profileColor, basePalette) {
        profileColor?.let { basePalette.tinted(androidx.compose.ui.graphics.Color(it.colors.first()), 0.06f) } ?: basePalette
    }

    androidx.compose.runtime.CompositionLocalProvider(dev.viko.listaanimes.ui.theme.LocalPalette provides profilePalette) {
    Column(Modifier.fillMaxSize().background(profilePalette.background)) {
        @Composable
        fun header() {
            val c = dev.viko.listaanimes.ui.nav.LocalContainer.current
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val myStyle by c.settings.profileStyle.collectAsStateWithLifecycle(initialValue = dev.viko.listaanimes.data.settings.ProfileStyle())
            val mine = pr.id == me
            dev.viko.listaanimes.ui.components.ProfileHeader(
                name = pr.name,
                avatar = if (mine) dev.viko.listaanimes.ui.components.resolveAvatar(ctx, myStyle, pr.avatar) else pr.avatar,
                banner = if (mine) dev.viko.listaanimes.ui.components.resolveBanner(ctx, myStyle, pr.banner) else pr.banner,
                color = if (mine) dev.viko.listaanimes.data.settings.ProfileColor.byId(myStyle.colorId) ?: dev.viko.listaanimes.data.settings.ProfileColor.fromAniList(pr.profileColor)
                    else dev.viko.listaanimes.data.settings.ProfileColor.fromAniList(pr.profileColor),
                animated = if (mine) myStyle.animatedBg else dev.viko.listaanimes.data.settings.AnimatedBg.NONE,
                subtitle = if (vm.secret) t("Perfil secreto") else if (pr.isFollower) t("Segue você") else null,
                top = {
                    IconButton(onClick = nav::back, colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar"))
                    }
                },
                trailing = {
                    if (mine) TextButton(onClick = { nav.customize() }) { Text(t("Personalizar"), color = p.accent) }
                    else if (pr.isFollowing) OutlinedButton(onClick = vm::toggleFollow, shape = RoundedCornerShape(12.dp)) { Text(t("Seguindo"), color = p.text) }
                    else Button(onClick = vm::toggleFollow, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background)) { Text(t("Seguir")) }
                },
                below = {
                    dev.viko.listaanimes.ui.components.FollowStats(
                        follows?.first, follows?.second,
                        onFollowers = { nav.follows(pr.id, true) }, onFollowing = { nav.follows(pr.id, false) },
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            val pt = dev.viko.listaanimes.i18n.I18n.locale
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp))
                    .then(if (vm.secret) Modifier else Modifier.pressable(onClick = { nav.stats(pr.id) }))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat("${pr.animeCount}", "animes")
                Stat("%.1f".format(pt, pr.minutesWatched / 1440.0), t("dias"))
                Stat("${pr.mangaCount}", t("mangás"))
                Stat("${pr.chaptersRead}", t("capítulos"))
                if (!vm.secret) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Estatísticas"), tint = p.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            error?.let { MessageBar(it) }
            Segmented(listOf(t("Atividade"), t("Lista"), t("Favoritos"), t("Sobre")), tab, {
                tab = it
                if (it == 1 && vm.list.value == null) vm.loadList(MediaKind.ANIME)
            }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            ThinDivider()
        }
        when (tab) {
            0 -> if (vm.secret) LazyColumn(Modifier.fillMaxSize(), state = state) {
                item { header() }
                item { EmptyState(t("Nada por aqui. Só pirulito.")) }
            } else FeedList(feed, nav) { header() }
            else -> LazyColumn(Modifier.fillMaxSize(), state = state) {
                item { header() }
                when (tab) {
                    1 -> item { ProfileList(vm, nav) }
                    2 -> item {
                        if (pr.favAnime.isNotEmpty()) { SectionHeader(t("Animes favoritos"), pr.favAnime.size); PosterRow(pr.favAnime, "pfa${pr.id}") { m, k -> nav.detail(m.id, k) } }
                        if (pr.favManga.isNotEmpty()) { SectionHeader(t("Mangás favoritos"), pr.favManga.size); PosterRow(pr.favManga, "pfm${pr.id}") { m, k -> nav.detail(m.id, k) } }
                        if (pr.favCharacters.isNotEmpty()) { SectionHeader(t("Personagens favoritos"), pr.favCharacters.size); dev.viko.listaanimes.ui.components.FavCharacterRow(pr.favCharacters) { nav.character(it) } }
                        if (pr.favAnime.isEmpty() && pr.favManga.isEmpty() && pr.favCharacters.isEmpty()) EmptyState(t("Nenhum favorito."))
                    }
                    3 -> item { if (pr.about.isBlank()) EmptyState(t("Sem descrição.")) else MarkdownView(pr.about, Modifier.padding(20.dp)) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Mono.style(16, FontWeight.Medium))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.palette.textSecondary)
    }
}

@Composable
private fun ProfileList(vm: ProfileViewModel, nav: Nav) {
    val list by vm.list.collectAsStateWithLifecycle()
    val kind by vm.listKind.collectAsStateWithLifecycle()
    val pr = vm.profile.collectAsStateWithLifecycle().value
    val s = dev.viko.listaanimes.ui.components.LocalSettings.current
    // As notas aparecem no formato que a pessoa usa no AniList.
    androidx.compose.runtime.CompositionLocalProvider(
        dev.viko.listaanimes.ui.components.LocalSettings provides s.copy(primaryScore = pr?.scoreSystem ?: s.primaryScore, secondaryScore = null, gridScore = false),
    ) {
    Column {
        Segmented(listOf(t("Anime"), t("Mangá")), kind.ordinal, { vm.loadList(MediaKind.entries[it]) }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (pr != null) dev.viko.listaanimes.ui.components.LinkRow(
            if (kind == MediaKind.ANIME) t("Abrir lista completa de anime") else t("Abrir lista completa de mangá"),
        ) { nav.userList(pr.id, pr.name, kind, pr.scoreSystem) }
        val l = list
        if (l == null) { dev.viko.listaanimes.ui.components.SkeletonRows(5); return@Column }
        if (l.isEmpty()) EmptyState(t("Lista vazia ou privada."))
        dev.viko.listaanimes.ui.components.LocalSettings.current.statusOrder.forEach { st ->
            val group = l.filter { it.entry.status == st }.sortedByDescending { it.entry.updatedAt }
            if (group.isEmpty()) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, top = 16.dp)) {
                StatusIcon(st, kind, AppTheme.palette.textSecondary)
                Spacer(Modifier.width(6.dp))
                Text(st.label(kind), style = MaterialTheme.typography.titleSmall)
                Text("  ${group.size}", style = Mono.style(13), color = AppTheme.palette.textSecondary)
            }
            group.forEach { row ->
                EntryRow(row.entry, row.media!!, null, showScore = true, showPlusOne = false, coverKey = null,
                    onClick = { nav.detail(row.entry.mediaId) }, onIncrement = {}, onSetProgress = {})
            }
        }
    }
}
}

// ---------------- Seguidores / seguindo / busca ----------------

class UsersViewModel(c: AppContainer, loader: suspend (Int) -> dev.viko.listaanimes.data.repo.Paged<UserRef>) : PagedViewModel<UserRef>(loader)

@Composable
fun FollowsScreen(nav: Nav, userId: Int, followers: Boolean) {
    val vm = appViewModel(key = "follows-$userId-$followers") { c -> UsersViewModel(c) { page -> c.social.follows(userId, followers, page) } }
    LaunchedEffect(Unit) { vm.start() }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (followers) t("Seguidores") else t("Seguindo"), onBack = nav::back)
        UserList(vm, nav)
    }
}

@Composable
private fun UserList(vm: UsersViewModel, nav: Nav) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    OnEndReached(state) { vm.nextPage() }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        error?.let { item { MessageBar(it) } }
        items(items, key = { it.id }) { u -> UserRow(u) { nav.profile(u.id) } }
        if (!loading && items.isEmpty() && error == null) item { EmptyState(t("Ninguém aqui.")) }
    }
}

@Composable
fun UserSearchScreen(nav: Nav) {
    var q by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    LaunchedEffect(q) { kotlinx.coroutines.delay(450); applied = q.trim() }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Buscar usuários"), onBack = nav::back)
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(t("Nome de usuário")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(4.dp))
        if (dev.viko.listaanimes.data.repo.SecretProfile.matches(applied)) {
            UserRow(dev.viko.listaanimes.data.repo.SecretProfile.ref) { nav.profileByName(dev.viko.listaanimes.data.repo.SecretProfile.NAME) }
            ThinDivider()
        }
        if (applied.length < 2) EmptyState(t("Digite pelo menos duas letras."))
        else {
            val vm = appViewModel(key = "users-$applied") { c -> UsersViewModel(c) { page -> c.social.searchUsers(applied, page) } }
            LaunchedEffect(applied) { vm.start() }
            UserList(vm, nav)
        }
    }
}
