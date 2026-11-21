package dev.viko.listaanimes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind

fun ListStatus.icon(kind: MediaKind): ImageVector = when (this) {
    ListStatus.CURRENT -> if (kind == MediaKind.ANIME) Icons.Outlined.PlayCircle else Icons.AutoMirrored.Outlined.MenuBook
    ListStatus.PLANNING -> Icons.Outlined.BookmarkBorder
    ListStatus.COMPLETED -> Icons.Outlined.CheckCircle
    ListStatus.PAUSED -> Icons.Outlined.PauseCircle
    ListStatus.DROPPED -> Icons.Outlined.DoNotDisturbOn
    ListStatus.REPEATING -> Icons.Outlined.Replay
}

@Composable
fun StatusIcon(status: ListStatus, kind: MediaKind, tint: Color, size: Dp = 16.dp) {
    AnimatedContent(status, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() }, label = "status") { st ->
        Icon(st.icon(kind), null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Ícone + nome do status. */
@Composable
fun StatusLabel(status: ListStatus, kind: MediaKind, color: Color? = null, style: TextStyle = MaterialTheme.typography.labelSmall, iconSize: Dp = 14.dp) {
    val c = color ?: dev.viko.listaanimes.ui.theme.statusColor(status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(status, kind, c, iconSize)
        Spacer(Modifier.width(4.dp))
        Text(status.label(kind), style = style, color = c, maxLines = 1)
    }
}
