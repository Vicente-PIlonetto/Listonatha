package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.domain.Labels
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.ui.theme.AppTheme

/** Mudar o status sem abrir a obra: "já vi", "quero ver", etc. */
@Composable
fun QuickStatusSheet(
    media: Media,
    current: ListStatus?,
    onDismiss: () -> Unit,
    onPick: (ListStatus) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onScore: (() -> Unit)? = null,
) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = p.background,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(media.coverMedium ?: media.coverLarge, 40.dp, placeholder = media.coverColor, adult = media.isAdult)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(media.title(s.titleLanguage), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(Labels.format(media.format).takeIf { it.isNotEmpty() }, media.total?.let { "${it}" + if (media.kind.name == "ANIME") t(" ep.") else t(" cap.") }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val order = listOf(ListStatus.COMPLETED, ListStatus.CURRENT, ListStatus.PLANNING, ListStatus.PAUSED, ListStatus.DROPPED, ListStatus.REPEATING)
            order.forEach { st ->
                val sel = st == current
                Option(
                    icon = st.icon(media.kind),
                    text = st.label(media.kind),
                    color = if (sel) dev.viko.listaanimes.ui.theme.statusColor(st) else p.text,
                    selected = sel,
                    iconColor = dev.viko.listaanimes.ui.theme.statusColor(st),
                ) { onPick(st) }
            }
            ThinDivider(Modifier.padding(vertical = 6.dp))
            if (current != null && onScore != null) Option(Icons.Outlined.StarOutline, t("Dar nota"), p.accent, false, onClick = onScore)
            Option(Icons.AutoMirrored.Outlined.OpenInNew, t("Abrir"), p.textSecondary, false, onClick = onOpen)
            if (current != null) Option(Icons.Outlined.Delete, t("Remover da lista"), p.textSecondary, false, onClick = onRemove)
        }
    }
}

@Composable
private fun Option(icon: ImageVector, text: String, color: Color, selected: Boolean, iconColor: Color = color, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .then(if (selected) Modifier.background(iconColor.copy(alpha = 0.14f)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), color = color)
    }
}
