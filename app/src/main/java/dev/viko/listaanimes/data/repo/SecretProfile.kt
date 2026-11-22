package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.BuildConfig
import dev.viko.listaanimes.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Easter egg: o perfil "chups chups" só existe dentro do app (não é uma conta do AniList).
 * Aparece ao buscar "chups chups" em Buscar usuários. Favoritos: Imaizumin (anime e mangá) e os personagens.
 */
object SecretProfile {
    const val NAME = "chups chups"
    /** Id negativo: nunca bate com um usuário de verdade. */
    const val ID = -7777

    private const val ANIME_ID = 132203
    private const val MANGA_ID = 123760

    /** A foto fica fora do Git (assets/secret, em .git/info/exclude); sem ela o avatar só não aparece. */
    const val avatar = "file:///android_asset/secret/chups_avatar.jpg"
    val banner get() = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.chups_banner}"

    fun matches(text: String?): Boolean =
        text?.trim()?.lowercase()?.replace(Regex("[\\s_-]+"), " ") == NAME

    val ref get() = UserRef(ID, NAME, avatar)

    /** Monta o perfil; anime, mangá e personagens vêm do AniList (sem rede, fica só a foto e o banner). */
    suspend fun build(c: AppContainer): UserProfileData = coroutineScope {
        val anime = async { runCatching { c.media.refreshDetail(ANIME_ID, null) }.getOrNull() }
        val manga = async { runCatching { c.media.refreshDetail(MANGA_ID, null) }.getOrNull() }
        val a = anime.await()
        val m = manga.await()
        val characters = (a?.characters.orEmpty() + m?.characters.orEmpty())
            .filter { it.id > 0 }
            .distinctBy { it.id }
            .map { FavCharacter(it.id, it.name, it.image) }
        UserProfileData(
            id = ID, name = NAME, avatar = avatar, banner = banner,
            about = "🍭",
            isFollowing = false, isFollower = false,
            animeCount = if (a != null) 1 else 0, animeMean = 0.0, minutesWatched = 0, episodesWatched = 0,
            mangaCount = if (m != null) 1 else 0, mangaMean = 0.0, chaptersRead = 0,
            favAnime = listOfNotNull(a?.media),
            favManga = listOfNotNull(m?.media),
            favCharacters = characters,
            profileColor = "red",
        )
    }
}
