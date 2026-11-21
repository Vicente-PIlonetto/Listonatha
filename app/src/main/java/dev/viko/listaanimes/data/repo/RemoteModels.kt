package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.domain.FuzzyDate
import dev.viko.listaanimes.domain.Media
import kotlinx.serialization.Serializable

@Serializable data class StaffCredit(val name: String, val role: String)
@Serializable data class TagRef(val name: String, val rank: Int, val spoiler: Boolean)
@Serializable data class StreamLink(val site: String, val url: String, val language: String?, val color: String?, val icon: String? = null)
@Serializable data class RelationRef(val type: String, val media: Media)
@Serializable data class CharacterRef(val name: String, val image: String?, val role: String?, val id: Int = 0)

/** Página de personagem (ficha do AniList). */
data class CharacterInfo(
    val id: Int,
    val name: String,
    val native: String?,
    val alternative: List<String>,
    val image: String?,
    val description: String,
    val gender: String?,
    val age: String?,
    val birthday: String?,
    val bloodType: String?,
    val favourites: Int,
    val siteUrl: String?,
    val appearances: List<CharacterAppearance>,
    val isFavourite: Boolean = false,
)

data class CharacterAppearance(val media: Media, val role: String?, val voiceActor: String?, val voiceActorImage: String?)

@Serializable
data class MediaDetail(
    val media: Media,
    val description: String = "",
    val meanScore: Int? = null,
    val source: String? = null,
    val siteUrl: String? = null,
    val endDate: FuzzyDate? = null,
    val studios: List<String> = emptyList(),
    val staff: List<StaffCredit> = emptyList(),
    val tags: List<TagRef> = emptyList(),
    val links: List<StreamLink> = emptyList(),
    val relations: List<RelationRef> = emptyList(),
    val characters: List<CharacterRef> = emptyList(),
    val recommendations: List<Media> = emptyList(),
)

@Serializable
data class Profile(
    val id: Int,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val animeCount: Int,
    val animeMean: Double,
    val minutesWatched: Int,
    val episodesWatched: Int,
    val mangaCount: Int,
    val mangaMean: Double,
    val chaptersRead: Int,
    val volumesRead: Int,
    val about: String = "",
    val favCharacters: List<FavCharacter> = emptyList(),
)

/** Personagem favorito (perfil). */
@Serializable data class FavCharacter(val id: Int, val name: String, val image: String?)

@Serializable
data class AppNotification(
    val id: Int,
    /** AIRING, RELATED, DATA_CHANGE ou SOCIAL. */
    val kind: String,
    val text: String,
    val createdAt: Long,
    val media: Media? = null,
    val user: UserRef? = null,
    val activityId: Int? = null,
    val threadId: Int? = null,
)

@Serializable
data class DiscoverSection(val key: String, val title: String, val items: List<Media>)

@Serializable
data class TagInfo(val name: String, val category: String?, val isAdult: Boolean = false)

@Serializable
data class GenresTags(val genres: List<String>, val tags: List<TagInfo>)

@Serializable
data class RecommendationGroup(val because: Media, val scoreRaw: Int, val special: Int?, val items: List<Media>)
