package dev.viko.listaanimes.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import dev.viko.listaanimes.i18n.t
import com.apollographql.apollo.api.Optional
import dev.viko.listaanimes.data.api.AniListApi
import dev.viko.listaanimes.data.db.AiringEntity
import dev.viko.listaanimes.data.db.AiringWithMedia
import dev.viko.listaanimes.data.db.AppDatabase
import dev.viko.listaanimes.data.db.CacheBlob
import dev.viko.listaanimes.data.db.ReviewDraft
import dev.viko.listaanimes.data.db.ReviewEntity
import dev.viko.listaanimes.data.db.ReviewWithMedia
import dev.viko.listaanimes.data.settings.SettingsRepository
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.SocialText
import dev.viko.listaanimes.graphql.AiringScheduleQuery
import dev.viko.listaanimes.graphql.DeleteReviewMutation
import dev.viko.listaanimes.graphql.MyReviewsQuery
import dev.viko.listaanimes.graphql.NotificationsQuery
import dev.viko.listaanimes.graphql.SaveReviewMutation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ScheduleRepository(private val db: AppDatabase, private val api: AniListApi) {
    private fun now() = System.currentTimeMillis() / 1000

    fun observe(from: Long, to: Long): Flow<List<AiringWithMedia>> = db.airing().observeRange(from, to)

    /**
     * Baixa os episódios entre [from] e [to]. [mediaIds] = só esses animes (null = todos em lançamento).
     * Agrupa em páginas de 50 para gastar poucas requisições.
     */
    suspend fun refresh(from: Long, to: Long, mediaIds: List<Int>?, maxPages: Int = 10): List<AiringWithMedia> {
        if (mediaIds != null && mediaIds.isEmpty()) return emptyList()
        val out = mutableListOf<AiringWithMedia>()
        suspend fun fetch(page: Int) = api.run(api.apollo.query(AiringScheduleQuery(
            from = from.toInt(), to = to.toInt(),
            mediaIds = if (mediaIds == null) Optional.Absent else Optional.present(mediaIds),
            page = page,
        ))).Page
        fun collect(p: AiringScheduleQuery.Page?) = p?.airingSchedules.orEmpty().filterNotNull().forEach { a ->
            val media = a.media?.mediaCore?.toMedia() ?: return@forEach
            out += AiringWithMedia(AiringEntity(a.id, media.id, a.episode, a.airingAt.toLong()), media)
        }
        // Páginas em lotes de 4 ao mesmo tempo (o "lastPage" do AniList não é confiável na 1ª página).
        var next = 1
        var more = true
        while (more && next <= maxPages) {
            val batch = (next until (next + 4).coerceAtMost(maxPages + 1)).toList()
            val pages = kotlinx.coroutines.coroutineScope { batch.map { pg -> async { fetch(pg) } }.awaitAll() }
            pages.forEach { collect(it) }
            more = pages.lastOrNull()?.pageInfo?.hasNextPage == true && pages.all { !it?.airingSchedules.isNullOrEmpty() }
            next += batch.size
        }
        // Não sobrescreve o favorito guardado das obras.
        val favs = db.media().getAll(out.mapNotNull { it.media?.id }).filter { it.isFavourite }.map { it.id }.toSet()
        db.media().upsert(out.mapNotNull { it.media }.distinctBy { it.id }.map { it.copy(isFavourite = it.isFavourite || it.id in favs) })
        // Guarda ~6 semanas para trás, para o calendário do mês.
        db.airing().deleteBefore(now() - 45 * 24 * 3600)
        db.airing().upsert(out.map { it.airing })
        return out
    }
}

class ReviewRepository(
    private val db: AppDatabase,
    private val api: AniListApi,
    private val settings: SettingsRepository,
) {
    private fun now() = System.currentTimeMillis() / 1000

    fun observeMine(): Flow<List<ReviewWithMedia>> = db.reviews().observeMine()
    fun observeMineFor(mediaId: Int): Flow<ReviewEntity?> = db.reviews().observeMineFor(mediaId)
    fun observeOthers(mediaId: Int): Flow<List<ReviewEntity>> = db.reviews().observeOthers(mediaId)
    fun observe(id: Int): Flow<ReviewEntity?> = db.reviews().observe(id)

    suspend fun mineFor(mediaId: Int) = db.reviews().mineFor(mediaId)
    suspend fun draft(mediaId: Int): ReviewDraft? = db.drafts().get(mediaId)
    suspend fun saveDraft(d: ReviewDraft) = db.drafts().upsert(d.copy(updatedAt = now()))
    suspend fun discardDraft(mediaId: Int) = db.drafts().delete(mediaId)

    suspend fun refreshMine() {
        val uid = settings.viewerIdNow() ?: return
        val all = mutableListOf<ReviewEntity>()
        val media = mutableListOf<Media>()
        var page = 1
        while (page <= 8) {
            val p = api.run(api.apollo.query(MyReviewsQuery(uid, page))).Page ?: break
            p.reviews.orEmpty().filterNotNull().forEach { r ->
                all += r.reviewFields.toEntity(uid)
                r.media?.mediaCore?.toMedia()?.let { media += it }
            }
            if (p.pageInfo?.hasNextPage != true) break
            page++
        }
        val favs = db.media().getAll(media.map { it.id }).filter { it.isFavourite }.map { it.id }.toSet()
        db.media().upsert(media.map { it.copy(isFavourite = it.isFavourite || it.id in favs) })
        db.reviews().clearMine()
        db.reviews().upsert(all)
    }

    suspend fun publish(d: ReviewDraft): ReviewEntity {
        val uid = settings.viewerIdNow()
        val r = api.run(api.apollo.mutation(SaveReviewMutation(
            id = Optional.presentIfNotNull(d.reviewId),
            mediaId = d.mediaId,
            summary = d.summary.trim(),
            body = d.body.trim(),
            score = d.score,
            private = d.isPrivate,
        ))).SaveReview ?: error(t("Resposta vazia"))
        val entity = r.reviewFields.toEntity(uid).copy(mine = true)
        db.reviews().upsert(entity)
        db.drafts().delete(d.mediaId)
        return entity
    }

    suspend fun delete(id: Int) {
        api.run(api.apollo.mutation(DeleteReviewMutation(id)))
        db.reviews().delete(id)
    }
}

class NotificationRepository(
    private val db: AppDatabase,
    private val api: AniListApi,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val ser = ListSerializer(AppNotification.serializer())

    fun observe(): Flow<List<AppNotification>> =
        db.cache().observe("notifications").map { b -> b?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() }.orEmpty() }

    val seenAt: Flow<Long> = settings.notificationsSeenAt

    /** Busca as notificações (mídia e sociais). [markRead] = zera a contagem no AniList (ao abrir a tela). */
    suspend fun refresh(markRead: Boolean): List<AppNotification> {
        val p = api.run(api.apollo.query(NotificationsQuery(1, markRead))).Page
        val list = p?.notifications.orEmpty().filterNotNull().mapNotNull { n -> map(n) }
        db.cache().put(CacheBlob("notifications", json.encodeToString(ser, list), System.currentTimeMillis() / 1000))
        if (markRead) settings.setNotificationsSeenAt(list.maxOfOrNull { it.createdAt } ?: (System.currentTimeMillis() / 1000))
        return list
    }

    private fun social(type: String, id: Int, at: Int?, user: dev.viko.listaanimes.graphql.fragment.UserRef?, activityId: Int? = null, thread: Pair<Int, String?>? = null): AppNotification? {
        val u = user?.toRef() ?: return null
        return AppNotification(id, "SOCIAL", SocialText.notification(type, u.name, thread?.second), (at ?: 0).toLong(),
            user = u, activityId = activityId, threadId = thread?.first)
    }

    private fun map(n: NotificationsQuery.Notification): AppNotification? {
        n.onAiringNotification?.let { a ->
            return AppNotification(a.id, "AIRING", t("EP {0} disponível", a.episode), (a.createdAt ?: 0).toLong(), a.media?.mediaCore?.toMedia())
        }
        n.onRelatedMediaAdditionNotification?.let { r ->
            return AppNotification(r.id, "RELATED", t("Obra relacionada adicionada"), (r.createdAt ?: 0).toLong(), r.media?.mediaCore?.toMedia())
        }
        n.onMediaDataChangeNotification?.let { c ->
            val reason = c.reason?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            return AppNotification(c.id, "DATA_CHANGE", t("Dados alterados{0}", reason), (c.createdAt ?: 0).toLong(), c.media?.mediaCore?.toMedia())
        }
        n.onMediaMergeNotification?.let { c ->
            val titles = c.deletedMediaTitles.orEmpty().filterNotNull().joinToString(", ")
            return AppNotification(c.id, "DATA_CHANGE", t("Unida com {0}", titles), (c.createdAt ?: 0).toLong(), c.media?.mediaCore?.toMedia())
        }
        n.onMediaDeletionNotification?.let { c ->
            return AppNotification(c.id, "DATA_CHANGE", t("Removida do AniList: {0}", c.deletedMediaTitle.orEmpty()), (c.createdAt ?: 0).toLong())
        }
        val t = n.__typename
        n.onFollowingNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef) }
        n.onActivityMessageNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onActivityMentionNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onActivityReplyNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onActivityReplySubscribedNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onActivityLikeNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onActivityReplyLikeNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, it.activityId) }
        n.onThreadCommentMentionNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, thread = it.thread?.let { th -> th.id to th.title }) }
        n.onThreadCommentReplyNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, thread = it.thread?.let { th -> th.id to th.title }) }
        n.onThreadCommentSubscribedNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, thread = it.thread?.let { th -> th.id to th.title }) }
        n.onThreadCommentLikeNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, thread = it.thread?.let { th -> th.id to th.title }) }
        n.onThreadLikeNotification?.let { return social(t, it.id, it.createdAt, it.user?.userRef, thread = it.thread?.let { th -> th.id to th.title }) }
        return null
    }
}
