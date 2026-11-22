package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.i18n.t
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import kotlinx.serialization.Serializable

@Serializable data class UserRef(val id: Int, val name: String, val avatar: String?)

enum class ActivityKind { LIST, TEXT, MESSAGE }

data class ActivityItem(
    val id: Int,
    val kind: ActivityKind,
    val user: UserRef?,
    val recipient: UserRef? = null,
    val text: String? = null,
    val status: String? = null,
    val progress: String? = null,
    val media: Media? = null,
    val createdAt: Long,
    val replyCount: Int,
    val likeCount: Int,
    val isLiked: Boolean,
    val isPrivate: Boolean = false,
)

data class ReplyItem(val id: Int, val user: UserRef?, val text: String, val createdAt: Long, val likeCount: Int, val isLiked: Boolean)

data class UserProfileData(
    val id: Int,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val about: String,
    val isFollowing: Boolean,
    val isFollower: Boolean,
    val animeCount: Int,
    val animeMean: Double,
    val minutesWatched: Int,
    val episodesWatched: Int,
    val mangaCount: Int,
    val mangaMean: Double,
    val chaptersRead: Int,
    val favAnime: List<Media>,
    val favManga: List<Media>,
    val favCharacters: List<FavCharacter> = emptyList(),
    /** options.profileColor do AniList (nome ou #hex). */
    val profileColor: String? = null,
)

data class FriendEntry(val user: UserRef, val status: ListStatus?, val scoreRaw: Int, val progress: Int)

data class ThreadItem(
    val id: Int,
    val title: String,
    val user: UserRef?,
    val replyUser: String?,
    val replyCount: Int,
    val viewCount: Int,
    val likeCount: Int,
    val isLiked: Boolean,
    val isSticky: Boolean,
    val isLocked: Boolean,
    val repliedAt: Long?,
    val createdAt: Long,
    val categories: List<String>,
    val mediaTitle: String?,
    val mediaId: Int?,
    val mediaCover: String?,
    val body: String? = null,
    val isSubscribed: Boolean = false,
)

data class CommentNode(
    val id: Int,
    val user: UserRef?,
    val text: String,
    val likeCount: Int,
    val isLiked: Boolean,
    val createdAt: Long,
    val depth: Int,
    val children: List<CommentNode>,
)

data class Paged<T>(val items: List<T>, val hasNext: Boolean)

object ForumCategories {
    val all = listOf(
        7 to t("Geral"), 1 to t("Anime"), 2 to t("Mangá"), 5 to t("Lançamentos"), 15 to t("Recomendações"), 3 to t("Light novels"),
        4 to t("Visual novels"), 8 to t("Notícias"), 9 to t("Música"), 10 to t("Games"), 16 to t("Jogos do fórum"), 19 to t("Eventos"),
        11 to t("Sugestões para o site"), 12 to t("Bugs"), 13 to t("Avisos do site"), 14 to t("Personalizar lista"), 18 to t("Apps do AniList"),
    )
    fun name(id: Int, fallback: String) = all.firstOrNull { it.first == id }?.second ?: fallback
}

/** Estatísticas de um tipo (anime ou mangá), no formato da tela Estatísticas. */
data class KindStats(
    val count: Int,
    val meanScore: Double,
    val standardDeviation: Double,
    /** Anime: minutos assistidos. Mangá: capítulos lidos. */
    val progress: Int,
    /** Anime: episódios. Mangá: volumes. */
    val secondary: Int,
    /** Anime: minutos planejados. Mangá: capítulos planejados. */
    val planned: Int,
    /** Faixas de tamanho (episódios ou capítulos), em ordem crescente. */
    val lengths: List<Pair<String, Int>>,
    val formats: List<Pair<String, Int>>,
    val statuses: List<Pair<ListStatus, Int>>,
    val genres: List<Triple<String, Int, Double>>,
)

data class UserStats(val id: Int, val name: String, val anime: KindStats, val manga: KindStats)
