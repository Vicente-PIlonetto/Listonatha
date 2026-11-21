package dev.viko.listaanimes.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.FilterAlt
import dev.viko.listaanimes.i18n.t
import dev.viko.listaanimes.ui.components.pressable
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import dev.viko.listaanimes.ui.components.enterAnimation
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.viko.listaanimes.BuildConfig
import dev.viko.listaanimes.data.settings.ThemeMode
import dev.viko.listaanimes.data.sync.Notifier
import dev.viko.listaanimes.data.sync.WorkScheduler
import dev.viko.listaanimes.domain.TitleLanguage
import dev.viko.listaanimes.domain.score.ScoreSystem
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.ScoreSize
import dev.viko.listaanimes.ui.components.ScoreView
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.ThinDivider
import dev.viko.listaanimes.ui.components.TopBar
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Serviços comuns; os que aparecem nos detalhes abertos entram na lista também. */
private val commonServices = listOf(
    "Crunchyroll", "Netflix", "Amazon Prime Video", "Disney Plus", "HIDIVE", "Max", "Hulu", "YouTube",
    "Bilibili TV", "iQ", "Apple TV", "Globoplay", "MANGA Plus", "Webtoon", "K MANGA", "Comikey", "INKR",
)

@Composable
fun SettingsScreen(nav: Nav, section: String? = null) {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val known by c.settings.knownServices.collectAsStateWithLifecycle(initialValue = emptySet())
    var message by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    // Ação de notificação esperando a permissão.
    var pendingNotif by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingNotif
        pendingNotif = null
        if (granted && action != null) scope.launch { action() }
        if (!granted) message = t("Sem permissão de notificação.")
    }

    fun enableNotification(enable: Boolean, set: suspend (Boolean) -> Unit) {
        val anyOn = s.notifyEpisodes || s.notifyRelated || s.notifyDataChange || s.notifySocial
        if (enable && Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(ctx)) {
            pendingNotif = { set(true); WorkScheduler.runAiringCheckNow(ctx) }
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        scope.launch {
            set(enable)
            if (enable && !anyOn) WorkScheduler.runAiringCheckNow(ctx)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { c.backup.export(uri) }.fold({ t("Backup salvo.") }, { t("Não foi possível salvar o backup.") })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { c.backup.import(uri) }.fold(
                { t("Backup restaurado: {0} notas especiais, {1} rascunhos.", it.specialScores.size, it.reviewDrafts.size) },
                { t("Arquivo inválido.") },
            )
        }
    }

    val cat = SettingsCategory.entries.firstOrNull { it.route == section }
    Column(Modifier.fillMaxSize()) {
        TopBar(cat?.title ?: t("Configurações"), onBack = nav::back)
        message?.let { MessageBar(it, t("OK")) { message = null } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when (cat) {
                null -> {
                    // Categorias em grupos, cada uma com um resumo do que está escolhido.
                    var i = 0
                    SettingsCategory.groups().forEach { (title, cats) ->
                        Group(title) {
                            cats.forEach { k ->
                                CategoryRow(k, summary(k, s), Modifier.enterAnimation(i++)) {
                                    if (k == SettingsCategory.PROFILE) nav.customize() else nav.settings(k.route)
                                }
                            }
                        }
                    }
                }
                SettingsCategory.ACCOUNT -> AccountsSection(onMessage = { message = it }, onLogout = { confirmLogout = true })
                SettingsCategory.APPEARANCE -> {
                    Group(t("Tema")) {
                        Options(ThemeMode.entries.map { it to it.label }, s.theme) { scope.launch { c.settings.setTheme(it) } }
                        if (Build.VERSION.SDK_INT >= 31) {
                            SwitchRow(t("Material You (cores do papel de parede)"), s.materialYou) { v -> scope.launch { c.settings.setMaterialYou(v) } }
                        }
                    }
                    Group(t("Cor de destaque")) {
                        if (s.materialYou && Build.VERSION.SDK_INT >= 31) {
                            Text(t("A cor vem do papel de parede."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(16.dp))
                        } else Box(Modifier.padding(vertical = 12.dp)) { AccentPicker(s.accent) { a -> scope.launch { c.settings.setAccent(a) } } }
                    }
                    Group(t("Idioma")) {
                        val chosenLang = remember { dev.viko.listaanimes.i18n.I18n.chosen(ctx) }
                        Options(
                            dev.viko.listaanimes.i18n.AppLanguage.entries.map { it to (if (it == dev.viko.listaanimes.i18n.AppLanguage.SYSTEM) t("Sistema") else it.nativeName) },
                            chosenLang,
                        ) { l ->
                            if (l != chosenLang) {
                                dev.viko.listaanimes.i18n.I18n.save(ctx, l)
                                // Reabre o app no novo idioma.
                                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { i ->
                                    ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
                                }
                                Runtime.getRuntime().exit(0)
                            }
                        }
                    }
                    Group(t("Ícone do app")) {
                        Box(Modifier.padding(vertical = 12.dp)) { IconPicker(s.iconAccent, s.accent, s.iconDark) { a -> scope.launch { c.settings.setIconAccent(a) } } }
                        SwitchRow(t("Ícone escuro"), s.iconDark, sub = t("O ícone muda quando você sai do app.")) { v -> scope.launch { c.settings.setIconDark(v) } }
                    }
                }
                SettingsCategory.LISTS -> {
                    Group(t("Exibição")) {
                        SwitchRow(t("Mostrar listas em grade"), s.gridLayout) { v -> scope.launch { c.settings.setGridLayout(v) } }
                        SwitchRow(t("Dar nota direto na grade"), s.gridScore) { v -> scope.launch { c.settings.setGridScore(v) } }
                        SwitchRow(t("Pedir nota ao marcar como completo"), s.askScoreOnComplete) { v -> scope.launch { c.settings.setAskScoreOnComplete(v) } }
                    }
                    Group(t("Idioma dos títulos")) {
                        Options(TitleLanguage.entries.map { it to it.label }, s.titleLanguage) { scope.launch { c.settings.setTitleLanguage(it, fromAccount = false) } }
                    }
                }
                SettingsCategory.CONTENT -> ContentSection()
                SettingsCategory.SCORES -> {
                    Group(t("Sistema de nota principal")) {
                        Options(ScoreSystem.entries.map { it to it.label }, s.primaryScore) { scope.launch { c.settings.setPrimaryScore(it) } }
                    }
                    Group(t("Sistema de nota secundário")) {
                        Options(listOf<Pair<ScoreSystem?, String>>(null to t("Desligado")) + ScoreSystem.entries.map { it to it.label }, s.secondaryScore) {
                            scope.launch { c.settings.setSecondaryScore(it) }
                        }
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ScoreView(89, null, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
                            Spacer(Modifier.width(24.dp))
                            ScoreView(72, null, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
                        }
                    }
                }
                SettingsCategory.SOCIAL -> {
                    Group(t("Fórum")) {
                        SwitchRow(t("Separar o fórum por categoria"), s.forumByCategory) { v -> scope.launch { c.settings.setForumByCategory(v) } }
                    }
                }
                SettingsCategory.STREAMING -> {
                    Group(null) {
                        val services = (commonServices + known).distinct().sortedBy { it.lowercase() }
                        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            services.forEach { sv ->
                                Pill(sv, sv in s.myServices, {
                                    scope.launch { c.settings.setMyServices(if (sv in s.myServices) s.myServices - sv else s.myServices + sv) }
                                })
                            }
                        }
                    }
                }
                SettingsCategory.NOTIFICATIONS -> {
                    Group(null) {
                        SwitchRow(t("Episódio novo de anime que estou vendo"), s.notifyEpisodes) { enableNotification(it) { v -> c.settings.setNotifyEpisodes(v) } }
                        SwitchRow(t("Obra relacionada adicionada"), s.notifyRelated) { enableNotification(it) { v -> c.settings.setNotifyRelated(v) } }
                        SwitchRow(t("Mudança em obra da lista"), s.notifyDataChange) { enableNotification(it) { v -> c.settings.setNotifyDataChange(v) } }
                        SwitchRow(t("Social (curtidas, respostas, seguidores, menções)"), s.notifySocial) { enableNotification(it) { v -> c.settings.setNotifySocial(v) } }
                    }
                }
                SettingsCategory.BACKUP -> {
                    Group(null) {
                        Text(
                            t("Notas especiais, rascunhos e configurações. A lista fica no AniList."),
                            style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(16.dp),
                        )
                        LinkRow(t("Exportar"), icon = androidx.compose.material.icons.Icons.Outlined.Upload) { exportLauncher.launch("listonatha-backup-${LocalDate.now()}.json") }
                        LinkRow(t("Importar"), icon = androidx.compose.material.icons.Icons.Outlined.Download) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    }
                }
                SettingsCategory.ABOUT -> AboutSection()
                SettingsCategory.PROFILE -> {}
            }
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(t("Sair da conta?")) },
        text = { Text(t("Os dados locais ficam no aparelho até outra conta entrar.")) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; c.auth.logout() }) { Text(t("Sair")) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(t("Cancelar")) } },
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
private fun <T> Options(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> Pill(label, v == selected, { onSelect(v) }) }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val p = AppTheme.palette
    // A linha inteira liga e desliga, não só o switch.
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.background, uncheckedThumbColor = p.textTertiary))
    }
}

/** Grupo de opções num cartão arredondado, com título pequeno acima. */
@Composable
fun Group(title: String?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = AppTheme.palette
    if (title != null) Text(
        title, style = MaterialTheme.typography.labelLarge, color = p.accent,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 6.dp),
    ) else Spacer(Modifier.height(10.dp))
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(vertical = 4.dp),
        content = content,
    )
}


/** Bolinhas com as cores de destaque; a escolhida ganha um anel. */
@Composable
private fun AccentPicker(selected: dev.viko.listaanimes.data.settings.Accent, onPick: (dev.viko.listaanimes.data.settings.Accent) -> Unit) {
    val dark = AppTheme.palette.background.luminance() < 0.5f
    val p = AppTheme.palette
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dev.viko.listaanimes.data.settings.Accent.entries.forEach { a ->
            val color = androidx.compose.ui.graphics.Color(if (dark) a.dark else a.light)
            val sel = a == selected
            val ring by androidx.compose.animation.core.animateDpAsState(if (sel) 2.dp else 0.dp, label = "ring")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(a) }) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(36.dp).border(ring, p.text, androidx.compose.foundation.shape.CircleShape).padding(5.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape),
                )
                Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (sel) p.text else p.textTertiary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}


enum class SettingsCategory(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Long,
) {
    ACCOUNT("conta", t("Contas"), androidx.compose.material.icons.Icons.Outlined.AccountCircle, 0xFF4FB3F0),
    PROFILE("perfil", t("Personalizar perfil"), androidx.compose.material.icons.Icons.Outlined.AutoAwesome, 0xFFC063FF),
    APPEARANCE("aparencia", t("Aparência"), androidx.compose.material.icons.Icons.Outlined.Palette, 0xFFF2A65E),
    LISTS("listas", t("Listas e títulos"), androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ViewList, 0xFF6FD07A),
    CONTENT("conteudo", t("Conteúdo e filtros"), androidx.compose.material.icons.Icons.Outlined.FilterAlt, 0xFFE0457B),
    SCORES("notas", t("Sistemas de nota"), androidx.compose.material.icons.Icons.Outlined.StarOutline, 0xFFF0CC4F),
    SOCIAL("social", t("Social e fórum"), androidx.compose.material.icons.Icons.Outlined.Forum, 0xFF5B8DEF),
    STREAMING("streaming", t("Meus serviços de streaming"), androidx.compose.material.icons.Icons.Outlined.LiveTv, 0xFFEC6F82),
    NOTIFICATIONS("notificacoes", t("Notificações"), androidx.compose.material.icons.Icons.Outlined.Notifications, 0xFF4FB3F0),
    BACKUP("backup", t("Backup"), androidx.compose.material.icons.Icons.Outlined.CloudSync, 0xFF8FA3B8),
    ABOUT("sobre", t("Sobre"), androidx.compose.material.icons.Icons.Outlined.Info, 0xFF8FA3B8);

    companion object {
        fun groups(): List<Pair<String, List<SettingsCategory>>> = listOf(
            t("Conta") to listOf(ACCOUNT, PROFILE),
            t("Geral") to listOf(APPEARANCE, LISTS, CONTENT, SCORES),
            t("Social e integrações") to listOf(SOCIAL, STREAMING, NOTIFICATIONS, BACKUP),
            t("App") to listOf(ABOUT),
        )
    }
}

private fun summary(k: SettingsCategory, s: dev.viko.listaanimes.data.settings.Settings): String = when (k) {
    SettingsCategory.ACCOUNT -> t("Trocar, adicionar ou sair")
    SettingsCategory.PROFILE -> t("Cor, banner e fundo animado")
    SettingsCategory.APPEARANCE -> listOf(s.theme.label, if (s.materialYou) t("Material You") else s.accent.label).joinToString(" · ")
    SettingsCategory.LISTS -> listOf(if (s.gridLayout) t("Grade") else t("Lista"), s.titleLanguage.label).joinToString(" · ")
    SettingsCategory.CONTENT -> listOfNotNull(
        if (s.showAdult) t("18+ visível") else t("18+ oculto"),
        (s.blockedGenres.size + s.blockedTags.size).takeIf { it > 0 }?.let { t("{0} bloqueados", it) },
    ).joinToString(" · ")
    SettingsCategory.SCORES -> listOfNotNull(s.primaryScore.label, s.secondaryScore?.label).joinToString(" · ")
    SettingsCategory.SOCIAL -> if (s.forumByCategory) t("Fórum por categoria") else t("Fórum em lista única")
    SettingsCategory.STREAMING -> if (s.myServices.isEmpty()) t("Nenhum marcado") else s.myServices.sorted().take(3).joinToString(", ") + if (s.myServices.size > 3) "…" else ""
    SettingsCategory.NOTIFICATIONS -> listOf(s.notifyEpisodes, s.notifyRelated, s.notifyDataChange, s.notifySocial).count { it }.let { if (it == 0) t("Desligadas") else t("{0} de 4 ligadas", it) }
    SettingsCategory.BACKUP -> t("Exportar e importar")
    SettingsCategory.ABOUT -> t("Versão {0}", BuildConfig.VERSION_NAME)
}

@Composable
private fun CategoryRow(k: SettingsCategory, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    val col = androidx.compose.ui.graphics.Color(k.color)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(k.icon, null, tint = col, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(k.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1)
        }
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary)
    }
}

/** Escolha do ícone: seguir a cor de destaque ou uma cor fixa. */
@Composable
private fun IconPicker(selected: dev.viko.listaanimes.data.settings.Accent?, accent: dev.viko.listaanimes.data.settings.Accent, dark: Boolean, onPick: (dev.viko.listaanimes.data.settings.Accent?) -> Unit) {
    val p = AppTheme.palette
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val options = listOf<dev.viko.listaanimes.data.settings.Accent?>(null) + dev.viko.listaanimes.data.settings.Accent.entries
        options.forEach { a ->
            val sel = a == selected
            Column(
                Modifier.width(64.dp).pressable(onClick = { onPick(a) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniIcon(a ?: accent, dark, sel)
                Text(a?.label ?: t("Destaque"), style = MaterialTheme.typography.labelSmall, color = if (sel) p.accent else p.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MiniIcon(a: dev.viko.listaanimes.data.settings.Accent, dark: Boolean, selected: Boolean) {
    val p = AppTheme.palette
    val bg = androidx.compose.ui.graphics.Color(if (dark) 0xFF1B1A18 else 0xFFF6F4EF)
    val bars = androidx.compose.ui.graphics.Color(if (dark) 0xFFEDE8DF else 0xFF2A2723)
    val dot = androidx.compose.ui.graphics.Color(if (dark) a.dark else a.light)
    val ring by androidx.compose.animation.core.animateDpAsState(if (selected) 3.dp else 0.dp, label = "ring")
    androidx.compose.foundation.Canvas(
        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(bg).border(ring, p.accent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
    ) {
        val u = size.width / 108f
        for (y in listOf(40f, 52f, 64f)) drawRect(bars, androidx.compose.ui.geometry.Offset(36f * u, y * u), androidx.compose.ui.geometry.Size(28f * u, 4f * u))
        drawCircle(dot, 4f * u, androidx.compose.ui.geometry.Offset(74f * u, 42f * u))
    }
}
