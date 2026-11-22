package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Share
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.bounceOn
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.components.icon
import dev.viko.listaanimes.ui.theme.tinted
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.data.db.ReviewEntity
import dev.viko.listaanimes.data.repo.MediaDetail
import dev.viko.listaanimes.data.repo.StreamLink
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.ProgressRules
import dev.viko.listaanimes.ui.components.Cover
import dev.viko.listaanimes.ui.components.EntryEditorSheet
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.PlusOneButton
import dev.viko.listaanimes.ui.components.PosterCard
import dev.viko.listaanimes.ui.components.PosterRow
import dev.viko.listaanimes.ui.components.ScorePickerSheet
import dev.viko.listaanimes.ui.components.ScoreSize
import dev.viko.listaanimes.ui.components.ScoreView
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.countdown
import dev.viko.listaanimes.ui.components.nowEpoch
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetailViewModel(private val c: AppContainer, val id: Int) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val detail = c.media.observeDetail(id).state(null)
    val media = c.media.observeMedia(id).state(null)
    val entry = c.lists.observeEntry(id).state(null)
    val myReview = c.reviews.observeMineFor(id).state(null)
    val others = c.reviews.observeOthers(id).state(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val friends = MutableStateFlow<List<dev.viko.listaanimes.data.repo.FriendEntry>>(emptyList())

    init {
        refresh()
        if (c.auth.token() != null) viewModelScope.launch { runCatching { friends.value = c.social.friendsOnMedia(id) } }
    }

    fun refresh() = viewModelScope.launch {
        loading.value = true
        try {
            c.media.refreshDetail(id, c.settings.viewerIdNow())
            error.value = null
        } catch (e: Exception) {
            error.value = if (detail.value == null) e.toUserMessage() else null
        } finally { loading.value = false }
    }

    fun increment() = viewModelScope.launch { c.lists.increment(id) }
    fun setProgress(v: Int) = viewModelScope.launch { c.lists.setProgress(id, v) }
    fun save(e: ListEntry, special: Int?) = viewModelScope.launch { c.lists.save(e, special) }
    fun add(m: Media, status: ListStatus) = viewModelScope.launch { c.lists.add(m, status) }
    fun remove() = viewModelScope.launch { c.lists.remove(id) }
    fun setScore(raw: Int, special: Int?) = viewModelScope.launch {
        val e = c.db.entries().get(id) ?: return@launch
        c.lists.save(e.copy(scoreRaw = raw), special)
    }
    fun toggleFavourite(m: Media) = viewModelScope.launch {
        try { c.lists.toggleFavourite(m) } catch (e: Exception) {
            message.value = t("Não foi possível alterar o favorito.")
        }
    }
}


@Composable
fun DetailScreen(nav: Nav, id: Int, coverKey: String? = null) {
    val vm = appViewModel(key = "detail-$id") { DetailViewModel(it, id) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val cached by vm.media.collectAsStateWithLifecycle()
    val entryRow by vm.entry.collectAsStateWithLifecycle()
    val myReview by vm.myReview.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val ctx = LocalContext.current
    var editor by remember { mutableStateOf(false) }
    var scorePicker by remember { mutableStateOf(false) }

    // Favorito e próximo episódio: o registro da obra no Room é o mais atual.
    val media = cached ?: detail?.media
    if (media == null) {
        Box(Modifier.fillMaxSize()) {
            if (error != null) Column(Modifier.statusBarsPadding().padding(top = 56.dp)) { MessageBar(error!!) }
            else dev.viko.listaanimes.ui.components.SkeletonDetail()
            IconButton(onClick = nav::back, modifier = Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
        }
        return
    }
    val entry = entryRow?.entry
    val special = entryRow?.special?.position
    val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(media.coverColor)
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(media.coverColor) { reveal.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    // Paleta tingida calculada uma vez; a entrada da cor anima só no desenho do fundo
    // (ler reveal.value dentro de drawBehind não recompõe a tela).
    val tinted = remember(tint, p) { p.tinted(tint, 0.10f) }
    val baseBg = p.background

    CompositionLocalProvider(dev.viko.listaanimes.ui.theme.LocalPalette provides tinted) {
    LazyColumn(
        Modifier.fillMaxSize().drawBehind {
            drawRect(baseBg)
            drawRect(tinted.background.copy(alpha = reveal.value))
        },
        state = listState,
    ) {
        item { Header(media, detail, coverKey ?: dev.viko.listaanimes.ui.components.coverKey("detail", media.id), listState, onBack = nav::back) }
        error?.let { item { MessageBar(it) } }
        message?.let { item { MessageBar(it) } }
        item {
            Actions(
                media = media, entry = entry, special = special,
                onAdd = { vm.add(media, it) },
                onIncrement = vm::increment, onSetProgress = vm::setProgress,
                onEdit = { editor = true }, onScore = { scorePicker = true },
                onFavourite = { vm.toggleFavourite(media) },
                onReview = { nav.reviewEditor(id) },
                onForum = { nav.forum(media.id, media.title(s.titleLanguage)) },
            )
            ScoreLine(entry, special, media, detail)
            NextEpisode(media)
            Spacer(Modifier.height(8.dp))
        }
        item { InfoBlock(media, detail, onGenre = { nav.genres(media.kind, genre = it) }, onTag = { nav.genres(media.kind, tag = it) }) }
        if (friends.isNotEmpty()) item { FriendsSection(friends, media.kind) { nav.profile(it) } }
        detail?.let { d ->
            if (d.description.isNotBlank()) item { Synopsis(d.description) }
            if (d.links.isNotEmpty()) item { WhereToWatch(d.links, s.myServices) { link -> dev.viko.listaanimes.ui.components.StreamingApps.open(ctx, link.site, link.url) } }
            item { OpenInReader(media) }
            if (d.relations.isNotEmpty()) item {
                SectionHeader(t("Relações"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(d.relations, key = { "${it.type}-${it.media.id}" }) { r ->
                        Column {
                            Text(Labels.relation(r.type), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            val k = dev.viko.listaanimes.ui.components.coverKey("rel$id", r.media.id)
                            PosterCard(r.media, { nav.detail(r.media.id, k) }, width = 96.dp, sharedKey = k)
                        }
                    }
                }
            }
            if (d.characters.isNotEmpty()) item {
                SectionHeader(t("Personagens"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(d.characters) { ch ->
                        Column(Modifier.width(72.dp).clip(RoundedCornerShape(6.dp)).pressable(onClick = { if (ch.id > 0) nav.character(ch.id) })) {
                            AsyncImage(ch.image, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(4.dp)).background(p.surfaceAlt))
                            Text(ch.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            ch.role?.let { Text(if (it == "MAIN") dev.viko.listaanimes.i18n.tc("role", "Principal") else if (it == "SUPPORTING") t("Secundário") else t("Figurante"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary) }
                        }
                    }
                }
            }
            if (d.recommendations.isNotEmpty()) item {
                SectionHeader(t("Recomendações"))
                PosterRow(d.recommendations, "drec$id") { m, k -> nav.detail(m.id, k) }
            }
        }
        item {
            SectionHeader(t("Reviews"), action = if (myReview == null && entry != null) t("escrever") else null, onAction = { nav.reviewEditor(id) })
        }
        myReview?.let { r -> item { ReviewRow(r, mine = true) { nav.reviewRead(r.id) } } }
        items(others, key = { it.id }) { r -> ReviewRow(r, mine = false) { nav.reviewRead(r.id) } }
        if (myReview == null && others.isEmpty()) item {
            Text(t("Nenhuma review."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (editor && entry != null) EntryEditorSheet(
        media = media, initial = entry, initialSpecial = special, isNew = false,
        onDismiss = { editor = false },
        onSave = { e, sp -> vm.save(e, sp); editor = false },
        onRemove = { vm.remove(); editor = false },
    )
    if (scorePicker && entry != null) ScorePickerSheet(
        initialRaw = entry.scoreRaw, initialSpecial = special, system = s.primaryScore,
        onDismiss = { scorePicker = false },
        onSave = { vm.setScore(it.raw, it.special); scorePicker = false },
    )
    }
}

@Composable
private fun Header(media: Media, detail: MediaDetail?, coverKey: String, listState: androidx.compose.foundation.lazy.LazyListState, onBack: () -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    Box(Modifier.fillMaxWidth().height(330.dp).clipToBounds()) {
        // Fundo: banner (ou a própria capa) desfocado, com zoom e parallax ao rolar.
        AsyncImage(
            media.banner ?: media.coverLarge, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 600f
                    translationY = off * 0.45f
                    val z = 1.08f + off / 2500f
                    scaleX = z; scaleY = z
                }
                .then(if (android.os.Build.VERSION.SDK_INT >= 31 && (media.banner == null || (media.isAdult && LocalSettings.current.blurAdult))) Modifier.blur(28.dp) else if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(6.dp) else Modifier)
                .background(p.surfaceAlt),
        )
        // Escurece até a cor do fundo para o texto ler bem.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to p.background.copy(alpha = 0.15f), 0.45f to p.background.copy(alpha = 0.55f), 1f to p.background,
        )))
        IconButton(
            onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f)),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        IconButton(
            onClick = { shareMedia(ctx, media, media.title(s.titleLanguage)) },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f)),
        ) { Icon(Icons.Outlined.Share, t("Compartilhar")) }
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Cover(media.coverLarge, 118.dp, placeholder = media.coverColor, adult = media.isAdult, sharedKey = coverKey)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).enterAnimation(1)) {
                Text(media.title(s.titleLanguage), style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val alt = listOfNotNull(media.titleRomaji, media.titleEnglish, media.titleNative).distinct() - media.title(s.titleLanguage)
                alt.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(Labels.format(media.format))
                    InfoChip(Labels.releaseStatus(media.status), accent = media.isReleasing)
                    media.averageScore?.let { InfoChip("$it%") }
                }
            }
        }
    }
    val alts = (listOfNotNull(media.titleRomaji, media.titleEnglish, media.titleNative).distinct() - media.title(s.titleLanguage)).drop(1)
    if (alts.isNotEmpty() || (media.synonyms.isNotEmpty() && detail != null)) {
        Text(
            (alts + media.synonyms.take(2)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InfoChip(text: String, accent: Boolean = false) {
    if (text.isBlank()) return
    val p = AppTheme.palette
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (accent) p.accent.copy(alpha = 0.2f) else p.surfaceAlt)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (accent) p.accent else p.textSecondary, maxLines = 1) }
}

@Composable
private fun Actions(
    media: Media, entry: ListEntry?, special: Int?,
    onAdd: (ListStatus) -> Unit, onIncrement: () -> Unit, onSetProgress: (Int) -> Unit,
    onEdit: () -> Unit, onScore: () -> Unit, onFavourite: () -> Unit, onReview: () -> Unit, onForum: () -> Unit,
) {
    val p = AppTheme.palette
    var addMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).enterAnimation(2).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(p.surfaceAlt).padding(16.dp),
    ) {
        if (entry == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Fora da sua lista"), style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.weight(1f))
                Box {
                    androidx.compose.material3.Button(
                        onClick = { addMenu = true }, shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                    ) { Text(t("Adicionar")) }
                    androidx.compose.material3.DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        listOf(ListStatus.PLANNING, ListStatus.CURRENT, ListStatus.COMPLETED, ListStatus.PAUSED, ListStatus.DROPPED).forEach {
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = { Icon(it.icon(media.kind), null, tint = dev.viko.listaanimes.ui.theme.statusColor(it)) },
                                text = { Text(it.label(media.kind)) }, onClick = { addMenu = false; onAdd(it) },
                            )
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sc = dev.viko.listaanimes.ui.theme.statusColor(entry.status)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(sc.copy(alpha = 0.18f)).clickable(onClick = onEdit)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dev.viko.listaanimes.ui.components.StatusIcon(entry.status, media.kind, sc, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(entry.status.label(media.kind), style = MaterialTheme.typography.labelLarge, color = sc)
                }
                Spacer(Modifier.weight(1f))
                dev.viko.listaanimes.ui.components.AnimatedProgressText(
                    ProgressRules.progressText(entry, media) + if (media.kind == MediaKind.MANGA && entry.progressVolumes > 0) t(" · Vol. {0}", entry.progressVolumes) else "",
                    Mono.style(16, FontWeight.SemiBold), p.accent,
                )
                Spacer(Modifier.width(12.dp))
                PlusOneButton(
                    enabled = ProgressRules.canIncrement(entry, media), current = entry.progress, total = media.total,
                    unit = if (media.kind == MediaKind.ANIME) t("Episódio") else t("Capítulo"),
                    onIncrement = onIncrement, onSet = onSetProgress,
                )
            }
            media.total?.takeIf { it > 0 }?.let { total ->
                Spacer(Modifier.height(12.dp))
                dev.viko.listaanimes.ui.components.ProgressBar(entry.progress / total.toFloat(), p.accent, height = 6.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (entry != null) {
                ActionIcon(Icons.Outlined.StarOutline, t("Nota"), onScore)
                ActionIcon(Icons.Outlined.Edit, t("Editar"), onEdit)
                ActionIcon(Icons.Outlined.RateReview, t("Review"), onReview)
            }
            ActionIcon(Icons.Outlined.Forum, t("Fórum"), onForum)
            ActionIcon(
                if (media.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (media.isFavourite) t("Favorito") else t("Favoritar"), onFavourite, active = media.isFavourite,
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    val p = AppTheme.palette
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) p.accent else p.text, modifier = Modifier.size(22.dp).bounceOn(active))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) p.accent else p.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ActionText(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (accent) AppTheme.palette.accent else AppTheme.palette.text,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScoreLine(entry: ListEntry?, special: Int?, media: Media, detail: MediaDetail?) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val avg = media.averageScore ?: detail?.meanScore
    if ((entry?.scoreRaw ?: 0) == 0 && avg == null) return
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (entry != null && entry.scoreRaw > 0) {
            Text(t("você "), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            ScoreView(entry.scoreRaw, special, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
            if (avg != null) Text("  ·  ", style = Mono.style(12), color = p.textTertiary)
        }
        if (avg != null) {
            Text("AniList ", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Text("$avg", style = Mono.style(15, FontWeight.Medium), color = p.text)
        }
    }
}

@Composable
private fun NextEpisode(media: Media) {
    val ep = media.nextEpisode ?: return
    val at = media.nextAiringAt ?: return
    val left = at - nowEpoch()
    if (left <= 0) return
    val time = Instant.ofEpochSecond(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(t("EEE, d MMM, HH:mm"), dev.viko.listaanimes.i18n.I18n.locale))
    Text(t("EP {0} em {1} · {2}", ep, countdown(left), time), style = Mono.style(13), color = AppTheme.palette.textSecondary, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun InfoBlock(media: Media, d: MediaDetail?, onGenre: (String) -> Unit, onTag: (String) -> Unit) {
    val p = AppTheme.palette
    val rows = buildList {
        add(t("Formato") to Labels.format(media.format))
        if (media.kind == MediaKind.ANIME) {
            media.episodes?.let { add(t("Episódios") to "$it" + (media.duration?.let { d -> t(" · {0} min", d) } ?: "")) }
        } else {
            media.chapters?.let { add(t("Capítulos") to "$it") }
            media.volumes?.let { add(t("Volumes") to "$it") }
        }
        add(t("Status") to Labels.releaseStatus(media.status))
        if (media.season != null && media.seasonYear != null) add(t("Temporada") to "${Labels.season(media.season)} ${media.seasonYear}")
        else media.startYear?.let { add(t("Ano") to "$it") }
        d?.studios?.takeIf { it.isNotEmpty() }?.let { add(t("Estúdio") to it.joinToString(", ")) }
        if (media.kind == MediaKind.MANGA) d?.staff?.filter { it.role.contains("Story", true) || it.role.contains("Art", true) }
            ?.takeIf { it.isNotEmpty() }?.let { add(t("Autores") to it.joinToString(", ") { s -> s.name }) }
        d?.source?.let { add(t("Origem") to Labels.source(it)) }
        media.country?.takeIf { media.kind == MediaKind.MANGA }?.let { add(t("País") to Labels.country(it)) }
    }.filter { it.second.isNotBlank() }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).enterAnimation(3), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.width(96.dp))
                Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
    // Gêneros e tags: tocar abre a busca filtrada.
    val tags = d?.tags?.filter { !it.spoiler }?.sortedByDescending { it.rank }?.take(12).orEmpty()
    if (media.genres.isNotEmpty() || tags.isNotEmpty()) {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp).enterAnimation(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            media.genres.forEach { g -> FilterChip(g, accent = true) { onGenre(g) } }
            tags.forEach { t -> FilterChip("#" + t.name, accent = false) { onTag(t.name) } }
        }
    }
}

@Composable
private fun FilterChip(text: String, accent: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (accent) p.accent else p.textSecondary,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (accent) p.accent.copy(alpha = 0.15f) else p.surfaceAlt)
            .pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun Synopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var shown by remember(text) { mutableStateOf(text) }
    SectionHeader(t("Sinopse"))
    dev.viko.listaanimes.ui.components.TranslateChip(text, { shown = it }, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
    Text(
        shown,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp).enterAnimation().animateContentSize().clickable { expanded = !expanded },
    )
}

@Composable
private fun WhereToWatch(links: List<StreamLink>, mine: Set<String>, open: (StreamLink) -> Unit) {
    val p = AppTheme.palette
    val ctx = LocalContext.current
    SectionHeader(t("Onde assistir"))
    val sorted = links.sortedWith(compareByDescending<StreamLink> { it.site in mine }.thenBy { it.site })
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.forEach { l ->
            val isMine = l.site in mine
            val brand = l.color?.let { runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: p.accent
            val state = remember(l.site) { dev.viko.listaanimes.ui.components.StreamingApps.state(ctx, l.site) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(brand.copy(alpha = if (isMine) 0.20f else 0.10f))
                    .pressable(onClick = { open(l) }).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Ícone: o do app instalado; senão, o que o AniList fornece para o site.
                val appIcon = remember(l.site) {
                    dev.viko.listaanimes.ui.components.StreamingApps.installedIcon(ctx, l.site)?.let { d ->
                        runCatching { d.toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(brand.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    if (appIcon != null) androidx.compose.foundation.Image(appIcon, null, modifier = Modifier.fillMaxSize())
                    else if (l.icon != null) AsyncImage(l.icon, null, modifier = Modifier.size(22.dp))
                    else Text(l.site.take(1), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.site, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isMine) FontWeight.SemiBold else FontWeight.Normal))
                    l.language?.let { Text(languageLabel(it), style = MaterialTheme.typography.bodySmall, color = p.textSecondary) }
                }
                Text(
                    when (state) {
                        dev.viko.listaanimes.ui.components.StreamingApps.State.INSTALLED -> t("Abrir no app")
                        dev.viko.listaanimes.ui.components.StreamingApps.State.STORE -> t("Play Store")
                        dev.viko.listaanimes.ui.components.StreamingApps.State.WEB -> t("Navegador")
                    },
                    style = MaterialTheme.typography.labelMedium, color = brand,
                )
            }
        }
    }
}

/** "Ler no Komikku" etc.: só aparece se algum leitor com extensões estiver instalado. */
@Composable
private fun OpenInReader(media: Media) {
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val apps = remember(media.kind) { dev.viko.listaanimes.ui.components.ReaderApps.installed(ctx, media.kind) }
    if (apps.isEmpty()) return
    // Romaji costuma achar mais nas fontes; sem ele, o título que estiver.
    val query = media.titleRomaji ?: media.titleEnglish ?: media.titleNative ?: return
    SectionHeader(if (media.kind == MediaKind.ANIME) t("Assistir em") else t("Ler em"))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { app ->
            val icon = remember(app.pkg) {
                dev.viko.listaanimes.ui.components.ReaderApps.icon(ctx, app)?.let { d -> runCatching { d.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accent.copy(alpha = 0.10f))
                    .pressable(onClick = { dev.viko.listaanimes.ui.components.ReaderApps.open(ctx, app, media.kind, query) })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(p.surfaceAlt), contentAlignment = Alignment.Center) {
                    if (icon != null) androidx.compose.foundation.Image(icon, null, modifier = Modifier.fillMaxSize())
                    else Text(app.name.take(1), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge)
                    Text(t("Busca por \"{0}\"", query), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Text(t("Abrir no app"), style = MaterialTheme.typography.labelMedium, color = p.accent)
            }
        }
    }
}

private fun languageLabel(lang: String): String = when (lang) {
    "Japanese" -> t("Japonês")
    "English" -> t("Inglês")
    "Portuguese" -> t("Português")
    "Spanish" -> t("Espanhol")
    "Korean" -> t("Coreano")
    "Chinese" -> t("Chinês")
    "French" -> t("Francês")
    "German" -> t("Alemão")
    "Italian" -> t("Italiano")
    else -> lang
}

@Composable
fun ReviewRow(r: ReviewEntity, mine: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (mine) t("Minha review") else (r.userName ?: t("Usuário")), style = MaterialTheme.typography.labelLarge,
                color = if (mine) p.accent else p.text, modifier = Modifier.weight(1f))
            if (mine && r.isPrivate) Text(t("privada  "), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            Text("${r.score}", style = Mono.style(13), color = p.textSecondary)
        }
        Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}


/** Quem eu sigo e tem esta obra na lista. */
@Composable
private fun FriendsSection(list: List<dev.viko.listaanimes.data.repo.FriendEntry>, kind: MediaKind, onUser: (Int) -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    SectionHeader(t("Seguindo"), list.size)
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(list, key = { it.user.id }) { f ->
            Column(Modifier.width(64.dp).clip(RoundedCornerShape(4.dp)).clickable { onUser(f.user.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                dev.viko.listaanimes.ui.components.Avatar(f.user.avatar, 48.dp)
                Text(f.user.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                f.status?.let { dev.viko.listaanimes.ui.components.StatusIcon(it, kind, p.textSecondary, 14.dp) }
                if (f.scoreRaw > 0) dev.viko.listaanimes.ui.components.ScoreGlyph(f.scoreRaw, s.primaryScore, null, 12, 14.dp, p.textSecondary)
                else if (f.progress > 0) Text("${f.progress}", style = Mono.style(11), color = p.textTertiary)
            }
        }
    }
}

/** Abre o menu de compartilhar do Android com o título e o link da obra no AniList. */
private fun shareMedia(ctx: android.content.Context, media: Media, title: String) {
    val path = if (media.kind == MediaKind.ANIME) "anime" else "manga"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, title)
        putExtra(android.content.Intent.EXTRA_TEXT, "$title\nhttps://anilist.co/$path/${media.id}")
    }
    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, t("Compartilhar"))) }
}
