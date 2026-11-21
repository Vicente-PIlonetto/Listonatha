package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.domain.FuzzyDate
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.ProgressRules
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val SP = ZoneId.systemDefault()

/** Editor completo de uma entrada da lista. */
@Composable
fun EntryEditorSheet(
    media: Media,
    initial: ListEntry,
    initialSpecial: Int?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ListEntry, Int?) -> Unit,
    onRemove: () -> Unit,
) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    var e by remember { mutableStateOf(initial) }
    var special by remember { mutableStateOf(initialSpecial) }
    var scorePicker by remember { mutableStateOf(false) }
    var dateField by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unit = if (media.kind == MediaKind.ANIME) t("Episódios") else t("Capítulos")

    fun setProgress(v: Int) {
        e = ProgressRules.applyProgress(e, media, v, LocalDate.now(SP), System.currentTimeMillis() / 1000)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = p.background,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(media.title(s.titleLanguage), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(16.dp))

            Label(t("Status"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ListStatus.entries.forEach { st ->
                    Pill(st.label(media.kind), e.status == st, icon = st.icon(media.kind), onClick = {
                        e = ProgressRules.applyStatus(e, media, st, LocalDate.now(SP), System.currentTimeMillis() / 1000)
                    })
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(unit)
                    Stepper(e.progress, media.total) { setProgress(it) }
                }
                if (media.kind == MediaKind.MANGA) {
                    Column(Modifier.weight(1f)) {
                        Label(t("Volumes"))
                        Stepper(e.progressVolumes, media.volumes) { e = e.copy(progressVolumes = it) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Label(t("Nota"))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { scorePicker = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (e.scoreRaw > 0) ScoreView(e.scoreRaw, special, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
                else Text(t("Sem nota"), style = MaterialTheme.typography.bodyLarge, color = p.textSecondary)
            }

            Spacer(Modifier.height(14.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Label(t("Início"))
                    DateText(e.startedAt) { dateField = "start" }
                }
                Column(Modifier.weight(1f)) {
                    Label(t("Fim"))
                    DateText(e.completedAt) { dateField = "end" }
                }
            }

            Spacer(Modifier.height(18.dp))
            Label(if (media.kind == MediaKind.ANIME) t("Vezes que revi") else t("Vezes que reli"))
            Stepper(e.repeat, null) { e = e.copy(repeat = it) }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = e.notes.orEmpty(),
                onValueChange = { e = e.copy(notes = it.take(6000)) },
                label = { Text(t("Anotação")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(4.dp),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Privado"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = e.isPrivate,
                    onCheckedChange = { e = e.copy(isPrivate = it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.surfaceAlt, uncheckedThumbColor = p.textTertiary),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) TextButton(onClick = { confirmRemove = true }) { Text(t("Remover da lista"), color = p.textSecondary) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(e, special) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.text, contentColor = p.background),
                ) { Text(t("Salvar")) }
            }
        }
    }

    if (scorePicker) ScorePickerSheet(
        initialRaw = e.scoreRaw,
        initialSpecial = special,
        system = s.primaryScore,
        onDismiss = { scorePicker = false },
        onSave = { picked -> e = e.copy(scoreRaw = picked.raw); special = picked.special; scorePicker = false },
    )

    dateField?.let { field ->
        val current = if (field == "start") e.startedAt else e.completedAt
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (current.toLocalDate() ?: LocalDate.now(SP)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(onClick = {
                    val d = state.selectedDateMillis?.let { FuzzyDate.of(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: FuzzyDate.EMPTY
                    e = if (field == "start") e.copy(startedAt = d) else e.copy(completedAt = d)
                    dateField = null
                }) { Text(t("Salvar")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    e = if (field == "start") e.copy(startedAt = FuzzyDate.EMPTY) else e.copy(completedAt = FuzzyDate.EMPTY)
                    dateField = null
                }) { Text(t("Limpar")) }
            },
        ) { DatePicker(state, showModeToggle = false, title = null, headline = null) }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(t("Remover da lista?")) },
        text = { Text(t("A obra sai da sua lista no AniList, com progresso e nota.")) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(t("Remover")) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(t("Cancelar")) } },
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = AppTheme.palette.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DateText(d: FuzzyDate, onClick: () -> Unit) {
    Text(
        if (d.isEmpty) "–" else d.format(),
        style = Mono.style(15),
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun Stepper(value: Int, max: Int?, onChange: (Int) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) { Text("−", style = Mono.style(18), color = p.textSecondary) }
        Text(
            if (max != null && max > 0) "$value/$max" else "$value",
            style = Mono.style(16),
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { dialog = true }.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        TextButton(onClick = { onChange(if (max != null && max > 0) (value + 1).coerceAtMost(max) else value + 1) }) {
            Text("+", style = Mono.style(18), color = p.accent)
        }
        Spacer(Modifier.width(4.dp))
    }
    if (dialog) NumberDialog(t("Valor"), value, max?.takeIf { it > 0 }, { dialog = false }) { onChange(it); dialog = false }
}
