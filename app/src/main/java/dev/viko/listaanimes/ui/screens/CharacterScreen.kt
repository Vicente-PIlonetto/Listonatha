package dev.viko.listaanimes.ui.screens

import androidx.compose.material.icons.outlined.FavoriteBorder
import dev.viko.listaanimes.ui.components.bounceOn
import dev.viko.listaanimes.i18n.t
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.repo.CharacterInfo
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.components.MarkdownView
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.PosterCard
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.coverKey
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.components.shimmer
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import dev.viko.listaanimes.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CharacterViewModel(private val c: AppContainer, private val id: Int) : ViewModel() {
    val info = MutableStateFlow<CharacterInfo?>(null)
    val error = MutableStateFlow<String?>(null)

    init { load() }

    fun load() = viewModelScope.launch {
        try { info.value = c.media.character(id); error.value = null } catch (e: Exception) { error.value = e.toUserMessage() }
    }

    /** Favoritar com resposta imediata; desfaz se o AniList recusar. */
    fun toggleFavourite() = viewModelScope.launch {
        val cur = info.value ?: return@launch
        info.value = cur.copy(isFavourite = !cur.isFavourite)
        try { info.value = cur.copy(isFavourite = c.media.toggleCharacterFavourite(cur)) } catch (e: Exception) {
            info.value = cur; error.value = t("Não foi possível alterar o favorito.")
        }
    }
}

/** Ficha do personagem: dados do AniList, descrição (com spoilers escondidos) e onde aparece. */
@Composable
fun CharacterScreen(nav: Nav, id: Int) {
    val vm = appViewModel(key = "character-$id") { CharacterViewModel(it, id) }
    val info by vm.info.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val p = AppTheme.palette
    val s = LocalSettings.current
    val uri = LocalUriHandler.current
    val ch = info

    LazyColumn(Modifier.fillMaxSize().background(p.background)) {
        item(key = "head") {
            Box(Modifier.fillMaxWidth().height(300.dp).clipToBounds()) {
                AsyncImage(
                    ch?.image, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier)
                        .background(p.surfaceAlt),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to p.background.copy(alpha = 0.2f), 1f to p.background)))
                IconButton(
                    onClick = nav::back, modifier = Modifier.statusBarsPadding().padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f)),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
                if (ch != null && dev.viko.listaanimes.ui.nav.LocalContainer.current.auth.token() != null) IconButton(
                    onClick = vm::toggleFavourite, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        if (ch.isFavourite) Icons.Filled.Favorite else androidx.compose.material.icons.Icons.Outlined.FavoriteBorder,
                        if (ch.isFavourite) t("Favorito") else t("Favoritar"),
                        tint = if (ch.isFavourite) p.accent else p.text,
                        modifier = Modifier.bounceOn(ch.isFavourite),
                    )
                }
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    if (ch == null) Box(Modifier.size(120.dp, 170.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                    else AsyncImage(
                        ch.image, ch.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp, 170.dp).clip(RoundedCornerShape(12.dp)).background(p.surfaceAlt),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).enterAnimation(1)) {
                        Text(ch?.name ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        ch?.native?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary) }
                        if (ch != null && ch.favourites > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(Icons.Filled.Favorite, null, tint = p.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(java.text.NumberFormat.getIntegerInstance(dev.viko.listaanimes.i18n.I18n.locale).format(ch.favourites), style = Mono.style(13), color = p.textSecondary)
                        }
                    }
                }
            }
            error?.let { MessageBar(it, t("Tentar de novo")) { vm.load() } }
        }
        if (ch == null) return@LazyColumn
        item(key = "facts") {
            val facts = listOfNotNull(
                ch.gender?.let { t("Gênero") to genderLabel(it) },
                ch.age?.let { t("Idade") to it },
                ch.birthday?.let { t("Aniversário") to it },
                ch.bloodType?.let { t("Tipo sanguíneo") to it },
            )
            if (facts.isNotEmpty()) FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).enterAnimation(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                facts.forEach { (k, v) ->
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(p.surfaceAlt).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(k, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (ch.alternative.isNotEmpty()) Text(
                t("Também chamado de ") + ch.alternative.take(6).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val anime = ch.appearances.firstOrNull()?.media?.title(s.titleLanguage).orEmpty()
                LinkChip(t("Procurar na wiki"), Icons.Outlined.TravelExplore) {
                    runCatching { uri.openUri("https://www.google.com/search?q=" + Uri.encode("${ch.name} $anime wiki fandom")) }
                }
                ch.siteUrl?.let { url -> LinkChip(t("Ver no AniList"), Icons.AutoMirrored.Outlined.OpenInNew) { runCatching { uri.openUri(url) } } }
            }
        }
        if (ch.description.isNotBlank()) item(key = "desc") {
            DescriptionBlock(ch.description)
        }
        if (ch.appearances.isNotEmpty()) item(key = "media") {
            SectionHeader(t("Aparece em"), ch.appearances.size)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(ch.appearances, key = { it.media.id }) { a ->
                    Column(Modifier.width(112.dp)) {
                        val k = coverKey("ch$id", a.media.id)
                        PosterCard(a.media, { nav.detail(a.media.id, k) }, sharedKey = k)
                        a.role?.let { Text(roleLabel(it), style = MaterialTheme.typography.labelSmall, color = p.accent) }
                    }
                }
            }
            // Dubladores (japonês), sem repetir.
            val vas = ch.appearances.mapNotNull { a -> a.voiceActor?.let { it to a.voiceActorImage } }.distinctBy { it.first }
            if (vas.isNotEmpty()) {
                SectionHeader(t("Voz japonesa"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vas, key = { it.first }) { (name, img) ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(img, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(p.surfaceAlt))
                            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DescriptionBlock(text: String) {
    val p = AppTheme.palette
    var expanded by remember { mutableStateOf(false) }
    var shown by remember(text) { mutableStateOf(text) }
    val long = text.length > 700
    SectionHeader(t("Sobre"))
    dev.viko.listaanimes.ui.components.TranslateChip(text, { shown = it }, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
    Column(Modifier.padding(horizontal = 20.dp).enterAnimation(3)) {
        Box(Modifier.then(if (long && !expanded) Modifier.height(260.dp).clipToBounds() else Modifier)) {
            MarkdownView(shown)
            if (long && !expanded) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                    .background(Brush.verticalGradient(listOf(p.background.copy(alpha = 0f), p.background))),
            )
        }
        if (long) Text(
            if (expanded) t("Mostrar menos") else t("Ler tudo"),
            style = MaterialTheme.typography.labelLarge, color = p.accent,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).pressable(onClick = { expanded = !expanded }).padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun LinkChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.15f)).pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.accent)
    }
}

private fun genderLabel(g: String) = when (g.lowercase()) {
    "male" -> t("Masculino")
    "female" -> t("Feminino")
    "non-binary" -> t("Não binário")
    else -> g
}

private fun roleLabel(r: String) = when (r) {
    "MAIN" -> dev.viko.listaanimes.i18n.tc("role", "Principal")
    "SUPPORTING" -> t("Secundário")
    else -> t("Figurante")
}
