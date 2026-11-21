package dev.viko.listaanimes.ui.screens

import dev.viko.listaanimes.i18n.t
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.viko.listaanimes.data.repo.Profile
import dev.viko.listaanimes.data.settings.AnimatedBg
import dev.viko.listaanimes.data.settings.BannerSource
import dev.viko.listaanimes.data.settings.ProfileColor
import dev.viko.listaanimes.data.settings.ProfileStyle
import dev.viko.listaanimes.ui.components.AnimatedBackground
import dev.viko.listaanimes.ui.components.ColorSwatch
import dev.viko.listaanimes.ui.components.MessageBar
import dev.viko.listaanimes.ui.components.Pill
import dev.viko.listaanimes.ui.components.ProfileHeader
import dev.viko.listaanimes.ui.components.SectionHeader
import dev.viko.listaanimes.ui.components.composeColors
import dev.viko.listaanimes.ui.components.galleryBannerFile
import dev.viko.listaanimes.ui.components.pressable
import dev.viko.listaanimes.ui.components.resolveBanner
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.nav.Nav
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Personalizar o meu perfil: cor (incl. degradês), banner e fundo animado. Prévia ao vivo no topo. */
@Composable
fun CustomizeProfileScreen(nav: Nav) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    val style by c.settings.profileStyle.collectAsStateWithLifecycle(initialValue = ProfileStyle())
    val profile by remember { c.media.observeCache("profile", Profile.serializer()) }.collectAsStateWithLifecycle(initialValue = null)
    val all by remember { c.lists.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    // Muda a chave do banner da galeria quando o arquivo é trocado.
    var galleryVersion by remember { mutableStateOf(0) }

    fun update(s: ProfileStyle) = scope.launch { c.settings.setProfileStyle(s) }

    fun pushColor(color: ProfileColor) = scope.launch {
        if (c.auth.token() == null) { message = t("Entre com sua conta para mudar a cor no AniList."); return@launch }
        message = try { c.social.setProfileColor(color.id); t("Cor aplicada no seu perfil do AniList.") }
        catch (e: Exception) { t("Não foi possível mudar a cor no AniList. Tente de novo.") }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.use { input -> galleryBannerFile(ctx).outputStream().use { input.copyTo(it) } }
                }.isSuccess
            }
            if (ok) { galleryVersion++; update(style.copy(bannerSource = BannerSource.GALLERY)) }
            else message = t("Não foi possível usar essa imagem. Escolha outra.")
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        dev.viko.listaanimes.ui.components.galleryAvatarFile(ctx).outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
            }
            if (ok) { galleryVersion++; update(style.copy(customAvatar = true)) }
            else message = t("Não foi possível usar essa imagem. Escolha outra.")
        }
    }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    fun openAniListSettings() = runCatching { uri.openUri("https://anilist.co/settings") }

    val color = ProfileColor.byId(style.colorId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Prévia ao vivo.
        androidx.compose.runtime.key(galleryVersion) {
            ProfileHeader(
                name = profile?.name ?: t("Você"),
                avatar = dev.viko.listaanimes.ui.components.resolveAvatar(ctx, style, profile?.avatar),
                banner = resolveBanner(ctx, style, profile?.banner),
                color = color,
                animated = style.animatedBg,
                subtitle = t("Prévia"),
                top = {
                    IconButton(onClick = nav::back, colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar"))
                    }
                    Text(t("Personalizar perfil"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                },
            )
        }
        message?.let { MessageBar(it, t("OK")) { message = null } }

        SectionHeader(t("Foto de perfil"))
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(t("Do AniList"), !style.customAvatar, { update(style.copy(customAvatar = false)) })
            Pill(t("Da galeria"), style.customAvatar, { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }
        AniListUploadHint(t("Trocar a foto no AniList"), ::openAniListSettings)

        SectionHeader(t("Cor do perfil"))
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileColor.all.forEach { pc ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp).pressable(onClick = {
                    update(style.copy(colorId = pc.id))
                    if (style.syncColorToAniList && pc.anilist) pushColor(pc)
                })) {
                    ColorSwatch(pc, pc.id == style.colorId)
                    Text(pc.label, style = MaterialTheme.typography.labelSmall, color = if (pc.id == style.colorId) p.text else p.textTertiary, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Usar a cor também no AniList"), style = MaterialTheme.typography.bodyMedium)
                Text(t("Só as 7 cores do AniList."),
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
            Switch(
                checked = style.syncColorToAniList,
                onCheckedChange = { on -> update(style.copy(syncColorToAniList = on)); if (on && color?.anilist == true) pushColor(color) },
                colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.surfaceAlt, uncheckedThumbColor = p.textTertiary),
            )
        }

        SectionHeader(t("Banner"))
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(BannerSource.ANILIST.label, style.bannerSource == BannerSource.ANILIST, { update(style.copy(bannerSource = BannerSource.ANILIST)) })
            Pill(BannerSource.GALLERY.label, style.bannerSource == BannerSource.GALLERY, {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            Pill(BannerSource.MEDIA.label, style.bannerSource == BannerSource.MEDIA, { update(style.copy(bannerSource = BannerSource.MEDIA)) })
            Pill(BannerSource.NONE.label, style.bannerSource == BannerSource.NONE, { update(style.copy(bannerSource = BannerSource.NONE)) })
        }
        if (style.bannerSource == BannerSource.MEDIA) {
            // Banners das obras da minha lista (favoritos primeiro).
            val banners = remember(all) {
                all.mapNotNull { it.media }.filter { it.banner != null }
                    .sortedWith(compareByDescending<dev.viko.listaanimes.domain.Media> { it.isFavourite }.thenByDescending { it.popularity ?: 0 })
                    .take(40)
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(banners, key = { it.id }) { m ->
                    val sel = style.mediaBanner == m.banner
                    AsyncImage(
                        m.banner, m.title(dev.viko.listaanimes.ui.components.LocalSettings.current.titleLanguage), contentScale = ContentScale.Crop,
                        modifier = Modifier.width(180.dp).height(64.dp).clip(RoundedCornerShape(12.dp))
                            .border(if (sel) 3.dp else 0.dp, p.accent, RoundedCornerShape(12.dp))
                            .background(p.surfaceAlt).clickable { update(style.copy(bannerSource = BannerSource.MEDIA, mediaBanner = m.banner)) },
                    )
                }
            }
        }

        AniListUploadHint(t("Trocar o banner no AniList"), ::openAniListSettings)

        SectionHeader(t("Personagens favoritos"))
        Row(Modifier.fillMaxWidth().clickable { update(style.copy(showFavCharacters = !style.showFavCharacters)) }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Mostrar no perfil"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = style.showFavCharacters, onCheckedChange = { update(style.copy(showFavCharacters = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.surfaceAlt, uncheckedThumbColor = p.textTertiary),
            )
        }

        SectionHeader(t("Fundo animado"))
        val cols = color?.composeColors() ?: listOf(p.accent)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedBg.entries.forEach { bg ->
                val sel = bg == style.animatedBg
                Column(Modifier.width(104.dp).pressable(onClick = { update(style.copy(animatedBg = bg)) })) {
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF14161C))
                            .border(if (sel) 3.dp else 0.dp, p.accent, RoundedCornerShape(12.dp)),
                    ) { AnimatedBackground(bg, cols, Modifier.fillMaxSize()) }
                    Text(bg.label, style = MaterialTheme.typography.labelMedium, color = if (sel) p.accent else p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            t("Imagens da galeria e fundo animado ficam só neste aparelho."),
            style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** O AniList não aceita envio de imagens por apps: o botão abre a página de configurações do site. */
@Composable
private fun AniListUploadHint(text: String, open: () -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surfaceAlt)
            .clickable(onClick = open).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = p.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = p.accent)
        }
    }
}
