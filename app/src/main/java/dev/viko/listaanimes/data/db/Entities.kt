package dev.viko.listaanimes.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.Media
import kotlinx.serialization.Serializable

/** Nota especial guardada só no app, com a posição exata (1 = S ... 12 = SSS+++). */
@Serializable
@Entity(tableName = "special_score")
data class SpecialScore(@PrimaryKey val mediaId: Int, val position: Int)

/** Mudança feita no app que ainda não foi enviada ao AniList. Enviadas em ordem de [seq]. */
@Entity(tableName = "pending_change")
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val mediaId: Int,
    val type: ChangeType,
    /** JSON de EntryPatch para SAVE; id da entrada para DELETE. */
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
)

enum class ChangeType { SAVE, DELETE }

@Serializable
@Entity(tableName = "review_draft")
data class ReviewDraft(
    @PrimaryKey val mediaId: Int,
    val reviewId: Int? = null,
    val summary: String = "",
    val body: String = "",
    val score: Int = 0,
    val isPrivate: Boolean = false,
    val updatedAt: Long = 0,
)

@Entity(tableName = "review")
data class ReviewEntity(
    @PrimaryKey val id: Int,
    val mediaId: Int,
    val userId: Int,
    val userName: String?,
    val userAvatar: String?,
    val summary: String,
    val body: String,
    val score: Int,
    val isPrivate: Boolean,
    val rating: Int,
    val ratingAmount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val mine: Boolean,
)

@Entity(tableName = "airing")
data class AiringEntity(
    @PrimaryKey val id: Int,
    val mediaId: Int,
    val episode: Int,
    val airingAt: Long,
)

/** Cache genérico de respostas (detalhe, Descobrir, perfil, notificações...). */
@Entity(tableName = "cache_blob")
data class CacheBlob(@PrimaryKey val key: String, val json: String, val fetchedAt: Long)

@Entity(tableName = "notified_episode")
data class NotifiedEpisode(@PrimaryKey val key: String, val at: Long)

data class EntryWithMedia(
    @Embedded val entry: ListEntry,
    @Relation(parentColumn = "mediaId", entityColumn = "id") val media: Media?,
    @Relation(parentColumn = "mediaId", entityColumn = "mediaId") val special: SpecialScore?,
)

data class AiringWithMedia(
    @Embedded val airing: AiringEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "id") val media: Media?,
)

data class ReviewWithMedia(
    @Embedded val review: ReviewEntity,
    @Relation(parentColumn = "mediaId", entityColumn = "id") val media: Media?,
)
