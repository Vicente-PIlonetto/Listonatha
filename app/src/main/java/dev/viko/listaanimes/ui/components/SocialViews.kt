package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.viko.listaanimes.data.repo.ActivityItem
import dev.viko.listaanimes.data.repo.ActivityKind
import dev.viko.listaanimes.data.repo.UserRef
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.SocialText
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono

/** Id da minha conta, para saber o que é meu (apagar, editar). */
val LocalViewerId = staticCompositionLocalOf<Int?> { null }

fun timeAgo(epoch: Long): String {
    val d = nowEpoch() - epoch
    return when {
        d < 60 -> t("agora")
        d < 3600 -> t("{0} min", d / 60)
        d < 86400 -> "${d / 3600} h"
        d < 30 * 86400 -> "${d / 86400} d"
        d < 365 * 86400 -> t("{0} mês", d / (30 * 86400))
        else -> "${d / (365 * 86400)} a"
    }
}

@Composable
fun Avatar(url: Any?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        url, null, contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(4.dp)).background(AppTheme.palette.surfaceAlt),
    )
}

/** Coração que "pula" ao curtir. */
@Composable
fun LikeButton(liked: Boolean, count: Int, onToggle: () -> Unit) {
    val p = AppTheme.palette
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }
        scale.snapTo(0.6f)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (liked) t("Descurtir") else t("Curtir"),
            tint = if (liked) p.accent else p.textTertiary,
            modifier = Modifier.size(17.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            AnimatedProgressText("$count", Mono.style(12), if (liked) p.accent else p.textTertiary)
        }
    }
}

@Composable
fun ReplyCount(count: Int, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, t("Respostas"), tint = p.textTertiary, modifier = Modifier.size(16.dp))
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", style = Mono.style(12), color = p.textTertiary)
        }
    }
}

@Composable
fun UserLine(user: UserRef?, createdAt: Long, onUser: (UserRef) -> Unit, recipient: UserRef? = null, trailing: @Composable () -> Unit = {}) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            user?.name ?: t("Usuário"), style = MaterialTheme.typography.labelLarge, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = user != null) { user?.let(onUser) },
        )
        if (recipient != null) {
            Text(" → ", style = MaterialTheme.typography.labelLarge, color = p.textTertiary)
            Text(recipient.name, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onUser(recipient) })
        }
        Spacer(Modifier.width(8.dp))
        Text(timeAgo(createdAt), style = Mono.style(11), color = p.textTertiary)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Atividade no feed: lista ("Assistiu ao episódio 5 de X"), status em texto ou mensagem. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityRow(
    item: ActivityItem,
    expanded: Boolean,
    onUser: (UserRef) -> Unit,
    onMedia: (Media, String) -> Unit,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val m = item.media.takeIf { item.kind == ActivityKind.LIST }
    // Cartão com a cor da capa (atividades de lista) ou neutro (status e mensagens).
    val tint = dev.viko.listaanimes.ui.theme.rememberCoverTint(m?.coverColor)
    val base = p.surfaceAlt
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                drawRect(base)
                if (m != null) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.04f))))
            }
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Avatar(item.user?.avatar, 34.dp, Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { item.user?.let(onUser) })
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                UserLine(item.user, item.createdAt, onUser, recipient = item.recipient.takeIf { item.kind == ActivityKind.MESSAGE }) {
                    if (item.isPrivate) Icon(Icons.Outlined.Lock, t("Privada"), tint = p.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(end = 8.dp)) {
            when (item.kind) {
                ActivityKind.LIST -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m != null) {
                            Cover(m.coverMedium ?: m.coverLarge, 46.dp, placeholder = m.coverColor, adult = m.isAdult, sharedKey = coverKey("act${item.id}", m.id),
                                modifier = Modifier.clickable { onMedia(m, coverKey("act${item.id}", m.id)) })
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(SocialText.listActivity(item.status, item.progress, m?.kind ?: dev.viko.listaanimes.domain.MediaKind.ANIME),
                                style = MaterialTheme.typography.bodySmall, color = if (m != null) tint else p.textSecondary)
                            if (m != null) Text(
                                m.title(s.titleLanguage), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onMedia(m, coverKey("act${item.id}", m.id)) },
                            )
                        }
                    }
                }
                else -> {
                    val text = item.text.orEmpty()
                    if (expanded || text.length < 700) MarkdownView(text)
                    else Column {
                        MarkdownView(text.take(600) + "…")
                        Text(t("ver tudo"), style = MaterialTheme.typography.labelMedium, color = p.accent, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ReplyCount(item.replyCount, onOpen)
            LikeButton(item.isLiked, item.likeCount, onLike)
        }
    }
}

/** Campo de resposta fixo embaixo. */
@Composable
fun ReplyBar(
    placeholder: String,
    sending: Boolean,
    replyingTo: String? = null,
    onCancelReply: () -> Unit = {},
    onSend: (String) -> Unit,
) {
    val p = AppTheme.palette
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(p.background).navigationBarsPadding().imePadding()) {
        ThinDivider()
        if (replyingTo != null) Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Respondendo a {0}", replyingTo), style = MaterialTheme.typography.labelMedium, color = p.textSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, t("Cancelar"), modifier = Modifier.size(16.dp)) }
        }
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, placeholder = { Text(placeholder) },
                maxLines = 5, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
            )
            IconButton(
                enabled = text.isNotBlank() && !sending,
                onClick = { onSend(text); text = "" },
            ) { Icon(Icons.AutoMirrored.Filled.Send, t("Enviar"), tint = if (text.isNotBlank()) p.accent else p.textTertiary) }
        }
    }
}

@Composable
fun UserRow(user: UserRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatar, 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(user.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Pequena etiqueta com borda (ex.: "Segue você", categoria do fórum). */
@Composable
fun Tag(text: String, accent: Boolean = false) {
    val p = AppTheme.palette
    Box(
        Modifier.border(0.75.dp, if (accent) p.accent else p.divider, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = if (accent) p.accent else p.textSecondary) }
}
