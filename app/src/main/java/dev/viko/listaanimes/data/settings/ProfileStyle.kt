package dev.viko.listaanimes.data.settings

import dev.viko.listaanimes.i18n.t
import kotlinx.serialization.Serializable

/** De onde vem o banner do meu perfil. */
@Serializable
enum class BannerSource(val label: String) { ANILIST(t("Do AniList")), MEDIA(t("De um anime ou mangá")), GALLERY(t("Da galeria")), NONE(t("Nenhum")) }

/** Fundos animados do cabeçalho do perfil. */
@Serializable
enum class AnimatedBg(val label: String) {
    NONE(t("Nenhum")), AURORA(t("Aurora")), PETALS(t("Pétalas")), STARS(t("Estrelas")), WAVES(t("Ondas")), BUBBLES(t("Bolhas")), EMBERS(t("Brasas"))
}

/**
 * Uma cor de perfil. As 7 primeiras são as do AniList (podem ir para o site);
 * as especiais são degradês que só existem no app.
 */
@Serializable
data class ProfileColor(val id: String, val label: String, val colors: List<Long>, val anilist: Boolean) {
    val isGradient: Boolean get() = colors.size > 1

    companion object {
        val all = listOf(
            ProfileColor("blue", t("Azul"), listOf(0xFF3DB4F2), true),
            ProfileColor("purple", t("Roxo"), listOf(0xFFC063FF), true),
            ProfileColor("pink", t("Rosa"), listOf(0xFFFC9DD6), true),
            ProfileColor("orange", t("Laranja"), listOf(0xFFEF881A), true),
            ProfileColor("red", t("Vermelho"), listOf(0xFFE13333), true),
            ProfileColor("green", t("Verde"), listOf(0xFF4CCA51), true),
            ProfileColor("gray", t("Cinza"), listOf(0xFF677B94), true),
            ProfileColor("sunset", t("Pôr do sol"), listOf(0xFFFF7E5F, 0xFFFEB47B), false),
            ProfileColor("aurora", t("Aurora"), listOf(0xFF00C9FF, 0xFF92FE9D), false),
            ProfileColor("sakura", t("Sakura"), listOf(0xFFF7A8C4, 0xFFC77DFF), false),
            ProfileColor("ocean", t("Oceano"), listOf(0xFF2E5BFF, 0xFF1BFFE4), false),
            ProfileColor("fire", t("Fogo"), listOf(0xFFF12711, 0xFFF5AF19), false),
            ProfileColor("galaxy", t("Galáxia"), listOf(0xFF7F00FF, 0xFFE100FF), false),
            ProfileColor("gold", t("Ouro"), listOf(0xFFB8860B, 0xFFFFE08A), false),
            ProfileColor("mint", t("Menta"), listOf(0xFF11998E, 0xFF38EF7D), false),
            ProfileColor("rainbow", t("Arco-íris"), listOf(0xFFFF5F6D, 0xFFFFC371, 0xFF47E891, 0xFF4FACFE, 0xFFB06AB3), false),
        )

        fun byId(id: String?): ProfileColor? = all.firstOrNull { it.id == id }

        /** Cor que o AniList devolve em options.profileColor (nome ou #hex de doador). */
        fun fromAniList(value: String?): ProfileColor? = when {
            value == null -> null
            value.startsWith("#") -> runCatching {
                ProfileColor("hex", value, listOf(0xFF000000 or android.graphics.Color.parseColor(value).toLong().and(0xFFFFFF)), true)
            }.getOrNull()
            else -> byId(value)
        }
    }
}

/** Personalização do meu perfil (guardada no app; só a cor pode ir para o AniList). */
@Serializable
data class ProfileStyle(
    val colorId: String? = null,
    val bannerSource: BannerSource = BannerSource.ANILIST,
    /** URL do banner escolhido de uma obra. */
    val mediaBanner: String? = null,
    val animatedBg: AnimatedBg = AnimatedBg.NONE,
    /** Aplica a cor também no perfil do AniList (só cores do AniList). */
    val syncColorToAniList: Boolean = false,
    /** Foto de perfil escolhida da galeria (só no app). */
    val customAvatar: Boolean = false,
    /** Seção de personagens favoritos no perfil, e quais ficam escondidos. */
    val showFavCharacters: Boolean = true,
    val hiddenCharacters: Set<Int> = emptySet(),
)
