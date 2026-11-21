package dev.viko.listaanimes.ui.components

import dev.viko.listaanimes.i18n.t
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.viko.listaanimes.data.settings.AnimatedBg
import dev.viko.listaanimes.data.settings.BannerSource
import dev.viko.listaanimes.data.settings.ProfileColor
import dev.viko.listaanimes.data.settings.ProfileStyle
import dev.viko.listaanimes.ui.theme.AppTheme
import java.io.File

fun ProfileColor.composeColors(): List<Color> = colors.map { Color(it) }

/** Arquivo do banner escolhido da galeria (copiado para o app). */
fun galleryBannerFile(context: Context) = File(context.filesDir, "profile_banner.jpg")

/** Arquivo da foto de perfil escolhida da galeria. */
fun galleryAvatarFile(context: Context) = File(context.filesDir, "profile_avatar.jpg")

private fun fileRequest(context: Context, f: File, prefix: String) =
    coil3.request.ImageRequest.Builder(context).data(f).memoryCacheKey("$prefix-${f.lastModified()}").diskCacheKey("$prefix-${f.lastModified()}").build()

/** Foto a mostrar no meu perfil: a da galeria (se escolhida) ou a do AniList. */
fun resolveAvatar(context: Context, style: ProfileStyle, anilistAvatar: String?): Any? =
    if (style.customAvatar) galleryAvatarFile(context).takeIf { it.exists() }?.let { fileRequest(context, it, "avatar") } ?: anilistAvatar
    else anilistAvatar

/** Banner a mostrar no meu perfil conforme a personalização. */
fun resolveBanner(context: Context, style: ProfileStyle, anilistBanner: String?): Any? = when (style.bannerSource) {
    BannerSource.ANILIST -> anilistBanner
    BannerSource.MEDIA -> style.mediaBanner ?: anilistBanner
    // A chave muda quando o arquivo muda, para o Coil não usar a imagem antiga.
    BannerSource.GALLERY -> galleryBannerFile(context).takeIf { it.exists() }?.let { fileRequest(context, it, "banner") }
    BannerSource.NONE -> null
}

/**
 * Cabeçalho de perfil: banner (ou o degradê da cor), fundo animado por cima,
 * avatar com anel colorido e nome com a cor/degradê do perfil.
 */
@Composable
fun ProfileHeader(
    name: String,
    avatar: Any?,
    banner: Any?,
    color: ProfileColor?,
    animated: AnimatedBg,
    subtitle: String?,
    modifier: Modifier = Modifier,
    top: @Composable RowScope.() -> Unit = {},
    trailing: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    val p = AppTheme.palette
    val cols = color?.composeColors() ?: listOf(p.accent)
    val brush = if (cols.size > 1) Brush.linearGradient(cols) else Brush.linearGradient(listOf(cols[0], cols[0]))
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            if (banner != null) {
                AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(p.surfaceAlt))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(cols.map { it.copy(alpha = 0.55f) } + p.background)))
            }
            AnimatedBackground(animated, cols, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to p.background)))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically, content = top)
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp).padding(top = 0.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.padding(top = 0.dp).size(92.dp).clip(RoundedCornerShape(22.dp)).background(brush).padding(3.dp)
                    .clip(RoundedCornerShape(19.dp)).background(p.background).padding(2.dp),
            ) { Avatar(avatar, 82.dp, Modifier.clip(RoundedCornerShape(17.dp))) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                Text(
                    name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall.merge(TextStyle(brush = brush)),
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                below()
            }
            Box(Modifier.padding(bottom = 6.dp)) { trailing() }
        }
    }
}

/** Bolinha de cor (sólida ou degradê) para escolher a cor do perfil. */
@Composable
fun ColorSwatch(color: ProfileColor, selected: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val cols = color.composeColors()
    val ring by androidx.compose.animation.core.animateDpAsState(if (selected) 3.dp else 0.dp, label = "ring")
    Box(
        modifier.size(44.dp).border(ring, p.text, RoundedCornerShape(22.dp)).padding(5.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (cols.size > 1) Brush.sweepGradient(cols + cols.first()) else Brush.linearGradient(listOf(cols[0], cols[0]))),
    )
}

/** "12 seguidores · 30 seguindo", cada parte abre a lista (como no Spotify). */
@Composable
fun FollowStats(followers: Int?, following: Int?, onFollowers: () -> Unit, onFollowing: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FollowPart(followers, if (followers == 1) t("seguidor") else t("seguidores"), onFollowers)
        Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
        FollowPart(following, t("seguindo"), onFollowing)
    }
}

@Composable
private fun FollowPart(n: Int?, label: String, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.viko.listaanimes.ui.components.AnimatedProgressText(n?.toString() ?: "–", dev.viko.listaanimes.ui.theme.Mono.style(14, androidx.compose.ui.text.font.FontWeight.SemiBold), p.text)
        Text(" $label", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
    }
}

/** Fileira de personagens favoritos (foto redonda + nome). */
@Composable
fun FavCharacterRow(list: List<dev.viko.listaanimes.data.repo.FavCharacter>, onClick: (Int) -> Unit) {
    val p = AppTheme.palette
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        items(list.size, key = { list[it].id }) { i ->
            val ch = list[i]
            Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).pressable(onClick = { onClick(ch.id) }), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(ch.image, ch.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(34.dp)).background(p.surfaceAlt))
                Text(ch.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
