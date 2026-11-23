package dev.viko.listaanimes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.viko.listaanimes.BuildConfig
import dev.viko.listaanimes.data.auth.SavedAccount
import dev.viko.listaanimes.data.repo.Profile
import dev.viko.listaanimes.i18n.t
import dev.viko.listaanimes.ui.components.Avatar
import dev.viko.listaanimes.ui.components.LinkRow
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.nav.startLogin
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ---------------- Contas ----------------

@Composable
fun AccountsSection(onMessage: (String) -> Unit, onLogout: () -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    val profile by remember { c.media.observeCache("profile", Profile.serializer()) }.collectAsStateWithLifecycle(initialValue = null)
    val viewerId by c.settings.viewerId.collectAsStateWithLifecycle(initialValue = null)
    var version by remember { mutableStateOf(0) }
    val saved = remember(version, viewerId) { c.auth.savedAccounts().filter { it.id != viewerId } }
    var switching by remember { mutableStateOf<Int?>(null) }

    fun switchTo(a: SavedAccount) = scope.launch {
        switching = a.id
        try {
            // Alterações ainda não enviadas seriam perdidas na troca: envia antes.
            if (c.db.pending().all().isNotEmpty()) {
                runCatching { c.lists.fullSync() }
                if (c.db.pending().all().isNotEmpty()) {
                    onMessage(t("Há alterações não enviadas. Conecte-se e tente de novo."))
                    return@launch
                }
            }
            c.auth.switchTo(a, viewerId, profile?.name, profile?.avatar)
            version++
            runCatching { c.lists.fullSync() }.onFailure { onMessage(SYNC_ERROR) }
        } finally { switching = null }
    }

    Group(t("Conta ativa")) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile?.avatar, 48.dp, Modifier.clip(CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.name ?: c.auth.guestName ?: "–", style = MaterialTheme.typography.titleMedium)
                Text(t("{0} animes · {1} mangás", profile?.animeCount ?: 0, profile?.mangaCount ?: 0), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
        }
    }
    if (saved.isNotEmpty()) Group(t("Outras contas")) {
        saved.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = switching == null) { switchTo(a) }.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(a.avatar, 40.dp, Modifier.clip(CircleShape))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge)
                    Text(if (switching == a.id) t("Trocando…") else t("Toque para trocar"), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
                IconButton(onClick = { c.auth.removeSaved(a.id); version++ }) { Icon(Icons.Outlined.Close, t("Remover"), tint = p.textTertiary) }
            }
        }
    }
    Group(null) {
        LinkRow(t("Adicionar conta"), icon = Icons.Outlined.PersonAdd) {
            val pr = profile
            val id = viewerId
            if (c.auth.token() != null && pr != null && id != null) c.auth.addAccount(id, pr.name, pr.avatar)
            startLogin(ctx, c)
        }
        LinkRow(t("Sair desta conta"), icon = Icons.AutoMirrored.Outlined.Logout, iconColor = Color(0xFFE05C5C), onClick = onLogout)
    }
}

// ---------------- Conteúdo e filtros ----------------

@Composable
fun ContentSection() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    val gt by produceState<dev.viko.listaanimes.data.repo.GenresTags?>(null, s.showAdult) { value = runCatching { c.media.genresTags() }.getOrNull() }
    var q by remember { mutableStateOf("") }

    Group(t("Conteúdo adulto")) {
        ToggleRow(t("Mostrar conteúdo 18+"), t("Em buscas, Descobrir e recomendações."), s.showAdult) { v -> scope.launch { c.settings.setShowAdult(v) } }
        ToggleRow(t("Desfocar capas 18+"), t("Capas e banners de obras adultas ficam desfocados."), s.blurAdult) { v -> scope.launch { c.settings.setBlurAdult(v) } }
    }
    Group(t("Gêneros e tags bloqueados")) {
        val blocked = (s.blockedGenres.map { it to true } + s.blockedTags.map { it to false }).sortedBy { it.first }
        if (blocked.isEmpty()) Text(
            t("Nada bloqueado."),
            style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(16.dp),
        ) else FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocked.forEach { (name, isGenre) ->
                Pill(name, true, {
                    scope.launch {
                        if (isGenre) c.settings.setBlockedGenres(s.blockedGenres - name) else c.settings.setBlockedTags(s.blockedTags - name)
                    }
                }, icon = Icons.Outlined.Close)
            }
        }
        OutlinedTextField(
            q, { q = it }, singleLine = true, placeholder = { Text(t("Bloquear gênero ou tag")) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp),
        )
        val all = gt
        if (q.isNotBlank() && all != null) {
            val ql = q.trim().lowercase()
            val genres = all.genres.filter { it.lowercase().contains(ql) && it !in s.blockedGenres }.take(6)
            val tags = all.tags.map { it.name }.filter { it.lowercase().contains(ql) && it !in s.blockedTags }.take(12)
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.forEach { g -> Pill(g, false, { scope.launch { c.settings.setBlockedGenres(s.blockedGenres + g); q = "" } }) }
                tags.forEach { tg -> Pill("#$tg", false, { scope.launch { c.settings.setBlockedTags(s.blockedTags + tg); q = "" } }) }
            }
        }
    }
}

@Composable
private fun ToggleRow(text: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.background, uncheckedThumbColor = p.textTertiary))
    }
}

// ---------------- Sobre ----------------

private const val DEV_NAME = "Vicente Pilonetto"
private const val DEV_URL = "https://github.com/Vicente-PIlonetto"
private const val DEV_ANILIST = "VyKTorCorher"

private data class Lib(val name: String, val license: String, val url: String)

private val LIBS = listOf(
    Lib("Jetpack Compose, AndroidX, Room, WorkManager", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    Lib("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    Lib("Apollo Kotlin", "MIT", "https://github.com/apollographql/apollo-kotlin/blob/main/LICENSE"),
    Lib("OkHttp", "Apache 2.0", "https://square.github.io/okhttp/#license"),
    Lib("Coil", "Apache 2.0", "https://github.com/coil-kt/coil/blob/main/LICENSE.txt"),
    Lib("ML Kit Translation", "Google APIs Terms of Service", "https://developers.google.com/ml-kit/terms"),
    Lib("IBM Plex Sans, IBM Plex Mono", "SIL Open Font License 1.1", "https://openfontlicense.org"),
    Lib("Material Symbols", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
)

@Composable
fun AboutSection() {
    val p = AppTheme.palette
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(p.surfaceAlt), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp, 4.dp).background(p.text))
                        if (i == 0) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Listonatha", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
        Text(
            t("Versão {0}", BuildConfig.VERSION_NAME) + " (" + BuildConfig.VERSION_CODE + ") · " + (if (BuildConfig.DEBUG) "debug" else "release"),
            style = Mono.style(12), color = p.textSecondary,
        )
        Text(
            t("Compilado em {0}", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, dev.viko.listaanimes.i18n.I18n.locale).format(Date(BuildConfig.BUILD_TIME))),
            style = Mono.style(12), color = p.textTertiary,
        )
    }
    Group(t("Desenvolvido por")) {
        Row(
            Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(DEV_URL) } }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar("$DEV_URL.png?size=160", 44.dp, Modifier.clip(CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(DEV_NAME, style = MaterialTheme.typography.titleMedium)
                Text("github.com/Vicente-PIlonetto", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = p.textTertiary, modifier = Modifier.size(18.dp))
        }
        // Perfil no AniList: abre dentro do próprio app.
        LinkRow(t("Perfil no AniList"), detail = DEV_ANILIST, icon = Icons.Outlined.AccountCircle, iconColor = Color(0xFF3DB4F2)) {
            runCatching { uri.openUri("https://anilist.co/user/$DEV_ANILIST") }
        }
    }
    Group(null) {
        if (BuildConfig.REPO_URL.isNotBlank()) LinkRow(t("Código-fonte"), icon = Icons.Outlined.Code) { runCatching { uri.openUri(BuildConfig.REPO_URL) } }
        if (BuildConfig.DONATE_URL.isNotBlank()) LinkRow(t("Apoiar o projeto"), icon = Icons.Outlined.Favorite, iconColor = Color(0xFFE0457B)) { runCatching { uri.openUri(BuildConfig.DONATE_URL) } }
        dev.viko.listaanimes.ui.components.PixDonateRow()
        LinkRow("AniList", icon = Icons.AutoMirrored.Outlined.OpenInNew) { runCatching { uri.openUri("https://anilist.co") } }
    }
    Group(t("Licenças")) {
        LIBS.forEach { l ->
            Row(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(l.url) } }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = MaterialTheme.typography.bodyMedium)
                    Text(l.license, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }
    }
    Text(
        t("Dados do AniList, pela API pública. Este app não é afiliado ao AniList."),
        style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
    )
}
