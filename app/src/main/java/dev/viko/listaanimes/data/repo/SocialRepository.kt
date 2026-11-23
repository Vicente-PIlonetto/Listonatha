package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.i18n.t
import com.apollographql.apollo.api.Optional
import dev.viko.listaanimes.data.api.AniListApi
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.TextUtils
import dev.viko.listaanimes.graphql.ActivityThreadQuery
import dev.viko.listaanimes.graphql.DeleteActivityMutation
import dev.viko.listaanimes.graphql.DeleteActivityReplyMutation
import dev.viko.listaanimes.graphql.DeleteThreadCommentMutation
import dev.viko.listaanimes.graphql.FeedQuery
import dev.viko.listaanimes.graphql.FollowersQuery
import dev.viko.listaanimes.graphql.FollowingOnMediaQuery
import dev.viko.listaanimes.graphql.FollowingQuery
import dev.viko.listaanimes.graphql.SaveActivityReplyMutation
import dev.viko.listaanimes.graphql.SaveTextActivityMutation
import dev.viko.listaanimes.graphql.SaveThreadCommentMutation
import dev.viko.listaanimes.graphql.SearchUsersQuery
import dev.viko.listaanimes.graphql.ThreadCommentsQuery
import dev.viko.listaanimes.graphql.ThreadDetailQuery
import dev.viko.listaanimes.graphql.ThreadsQuery
import dev.viko.listaanimes.graphql.ToggleFollowMutation
import dev.viko.listaanimes.graphql.ToggleLikeMutation
import dev.viko.listaanimes.graphql.UserListQuery
import dev.viko.listaanimes.graphql.UserProfileQuery
import dev.viko.listaanimes.graphql.fragment.ActivityFields
import dev.viko.listaanimes.graphql.fragment.ReplyFields
import dev.viko.listaanimes.graphql.fragment.ThreadRow
import dev.viko.listaanimes.graphql.type.LikeableType
import dev.viko.listaanimes.graphql.type.MediaType
import dev.viko.listaanimes.graphql.type.ThreadSort
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.Media

enum class LikeTarget { ACTIVITY, ACTIVITY_REPLY, THREAD, THREAD_COMMENT }

fun dev.viko.listaanimes.graphql.fragment.UserRef.toRef() = UserRef(id, name, avatar?.medium)

class SocialRepository(private val api: AniListApi) {

    // ---------- Feed e atividades ----------

    suspend fun feed(page: Int, following: Boolean, userId: Int? = null): Paged<ActivityItem> {
        val p = api.run(api.apollo.query(FeedQuery(
            page = page,
            following = if (userId == null && following) Optional.present(true) else Optional.Absent,
            global = if (userId == null && !following) Optional.present(true) else Optional.Absent,
            userId = Optional.presentIfNotNull(userId),
        ))).Page
        return Paged(p?.activities.orEmpty().mapNotNull { it?.activityFields?.toItem() }, p?.pageInfo?.hasNextPage == true)
    }

    suspend fun activity(id: Int): Pair<ActivityItem, List<ReplyItem>> {
        val a = api.run(api.apollo.query(ActivityThreadQuery(id))).Activity ?: error(t("Atividade não encontrada"))
        val item = a.activityFields.toItem() ?: error(t("Atividade não suportada"))
        val replies = (a.onListActivity?.replies?.map { it?.replyFields }
            ?: a.onTextActivity?.replies?.map { it?.replyFields }
            ?: a.onMessageActivity?.replies?.map { it?.replyFields }).orEmpty().mapNotNull { it?.toItem() }
        return item to replies
    }

    suspend fun toggleLike(id: Int, target: LikeTarget) {
        api.run(api.apollo.mutation(ToggleLikeMutation(id, LikeableType.safeValueOf(target.name))))
    }

    suspend fun postStatus(text: String) {
        api.run(api.apollo.mutation(SaveTextActivityMutation(text = text.trim())))
    }

    suspend fun deleteActivity(id: Int) { api.run(api.apollo.mutation(DeleteActivityMutation(id))) }

    suspend fun reply(activityId: Int, text: String): ReplyItem =
        api.run(api.apollo.mutation(SaveActivityReplyMutation(activityId, text.trim()))).SaveActivityReply?.replyFields?.toItem()
            ?: error(t("Resposta vazia"))

    suspend fun deleteReply(id: Int) { api.run(api.apollo.mutation(DeleteActivityReplyMutation(id))) }

    // ---------- Usuários ----------

    suspend fun profile(id: Int?, name: String? = null): UserProfileData {
        val u = api.run(api.apollo.query(UserProfileQuery(Optional.presentIfNotNull(id), Optional.presentIfNotNull(name)))).User
            ?: error(t("Usuário não encontrado"))
        return UserProfileData(
            id = u.id, name = u.name, avatar = u.avatar?.large, banner = u.bannerImage,
            about = u.about.orEmpty(), isFollowing = u.isFollowing == true, isFollower = u.isFollower == true,
            animeCount = u.statistics?.anime?.count ?: 0, animeMean = u.statistics?.anime?.meanScore ?: 0.0,
            minutesWatched = u.statistics?.anime?.minutesWatched ?: 0, episodesWatched = u.statistics?.anime?.episodesWatched ?: 0,
            mangaCount = u.statistics?.manga?.count ?: 0, mangaMean = u.statistics?.manga?.meanScore ?: 0.0,
            chaptersRead = u.statistics?.manga?.chaptersRead ?: 0,
            favAnime = u.favourites?.anime?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() },
            favManga = u.favourites?.manga?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() },
            favCharacters = u.favourites?.characters?.nodes.orEmpty().mapNotNull { n -> n?.let { FavCharacter(it.id, it.name?.full ?: return@mapNotNull null, it.image?.medium) } },
            profileColor = u.options?.profileColor,
            scoreSystem = dev.viko.listaanimes.domain.score.ScoreSystem.fromAniList(u.mediaListOptions?.scoreFormat?.rawValue),
        )
    }

    /** Números da tela Estatísticas (o gráfico de notas sai da lista, no sistema de nota do app). */
    suspend fun stats(userId: Int): UserStats {
        val u = api.run(api.apollo.query(dev.viko.listaanimes.graphql.UserStatsQuery(userId))).User ?: error(t("Usuário não encontrado"))
        // "7-16" -> 7, "101+" -> 101, "1" -> 1; faixa sem tamanho conhecido fica de fora.
        fun order(l: String) = l.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
        fun status(s: String?) = ListStatus.parse(s)
        val a = u.statistics?.anime
        val m = u.statistics?.manga
        val anime = KindStats(
            count = a?.count ?: 0, meanScore = a?.meanScore ?: 0.0, standardDeviation = a?.standardDeviation ?: 0.0,
            progress = a?.minutesWatched ?: 0, secondary = a?.episodesWatched ?: 0,
            planned = a?.statuses.orEmpty().firstOrNull { it?.status?.rawValue == "PLANNING" }?.minutesWatched ?: 0,
            lengths = a?.lengths.orEmpty().mapNotNull { l -> l?.length?.let { it to l.count } }.sortedBy { order(it.first) },
            formats = a?.formats.orEmpty().mapNotNull { f -> f?.format?.rawValue?.let { it to f.count } },
            statuses = a?.statuses.orEmpty().mapNotNull { x -> status(x?.status?.rawValue)?.let { it to x!!.count } },
            genres = a?.genres.orEmpty().mapNotNull { g -> g?.genre?.let { Triple(it, g.count, g.meanScore) } },
        )
        val manga = KindStats(
            count = m?.count ?: 0, meanScore = m?.meanScore ?: 0.0, standardDeviation = m?.standardDeviation ?: 0.0,
            progress = m?.chaptersRead ?: 0, secondary = m?.volumesRead ?: 0,
            planned = m?.statuses.orEmpty().firstOrNull { it?.status?.rawValue == "PLANNING" }?.chaptersRead ?: 0,
            lengths = m?.lengths.orEmpty().mapNotNull { l -> l?.length?.let { it to l.count } }.sortedBy { order(it.first) },
            formats = m?.formats.orEmpty().mapNotNull { f -> f?.format?.rawValue?.let { it to f.count } },
            statuses = m?.statuses.orEmpty().mapNotNull { x -> status(x?.status?.rawValue)?.let { it to x!!.count } },
            genres = m?.genres.orEmpty().mapNotNull { g -> g?.genre?.let { Triple(it, g.count, g.meanScore) } },
        )
        return UserStats(u.id, u.name, anime, manga)
    }

    /** Muda a cor do meu perfil no AniList (blue, purple, pink, orange, red, green, gray). */
    suspend fun setProfileColor(color: String) {
        api.run(api.apollo.mutation(dev.viko.listaanimes.graphql.UpdateProfileColorMutation(color)))
    }

    /** Lista de outra pessoa (só leitura). */
    suspend fun userList(userId: Int, kind: MediaKind): List<EntryWithMedia> {
        val col = api.run(api.apollo.query(UserListQuery(userId, if (kind == MediaKind.ANIME) MediaType.ANIME else MediaType.MANGA))).MediaListCollection
        return col?.lists.orEmpty().filterNotNull().flatMap { it.entries.orEmpty().filterNotNull() }.mapNotNull { e ->
            val m = e.media?.mediaCore?.toMedia() ?: return@mapNotNull null
            EntryWithMedia(e.entryFields.toEntry(kind), m, null)
        }
    }

    suspend fun toggleFollow(userId: Int): Boolean =
        api.run(api.apollo.mutation(ToggleFollowMutation(userId))).ToggleFollow?.isFollowing == true

    suspend fun follows(userId: Int, followers: Boolean, page: Int): Paged<UserRef> = if (followers) {
        val p = api.run(api.apollo.query(FollowersQuery(userId, page))).Page
        Paged(p?.followers.orEmpty().mapNotNull { it?.userRef?.toRef() }, p?.pageInfo?.hasNextPage == true)
    } else {
        val p = api.run(api.apollo.query(FollowingQuery(userId, page))).Page
        Paged(p?.following.orEmpty().mapNotNull { it?.userRef?.toRef() }, p?.pageInfo?.hasNextPage == true)
    }

    /** Atualiza a bio no AniList e no perfil em cache. */
    suspend fun setAbout(text: String) {
        api.run(api.apollo.mutation(dev.viko.listaanimes.graphql.UpdateAboutMutation(text)))
    }

    /** Número de seguidores e seguindo (o total da página). */
    suspend fun followCounts(userId: Int): Pair<Int, Int> {
        val d = api.run(api.apollo.query(dev.viko.listaanimes.graphql.FollowCountsQuery(userId)))
        return (d.followers?.pageInfo?.total ?: 0) to (d.following?.pageInfo?.total ?: 0)
    }

    suspend fun searchUsers(q: String, page: Int): Paged<UserRef> {
        val p = api.run(api.apollo.query(SearchUsersQuery(q, page))).Page
        return Paged(p?.users.orEmpty().mapNotNull { it?.userRef?.toRef() }, p?.pageInfo?.hasNextPage == true)
    }

    /** Quem eu sigo e tem esta obra na lista. */
    suspend fun friendsOnMedia(mediaId: Int): List<FriendEntry> =
        api.run(api.apollo.query(FollowingOnMediaQuery(mediaId))).Page?.mediaList.orEmpty().mapNotNull { e ->
            val u = e?.user?.userRef?.toRef() ?: return@mapNotNull null
            FriendEntry(u, ListStatus.parse(e.status?.rawValue), (e.score ?: 0.0).toInt(), e.progress ?: 0, dev.viko.listaanimes.domain.score.ScoreSystem.fromAniList(e.user?.mediaListOptions?.scoreFormat?.rawValue))
        }

    // ---------- Fórum ----------

    suspend fun threads(page: Int, categoryId: Int?, mediaId: Int?, search: String?, subscribed: Boolean): Paged<ThreadItem> {
        val sort = when {
            !search.isNullOrBlank() -> listOf(ThreadSort.SEARCH_MATCH)
            else -> listOf(ThreadSort.IS_STICKY, ThreadSort.REPLIED_AT_DESC)
        }
        val p = api.run(api.apollo.query(ThreadsQuery(
            page = page,
            categoryId = Optional.presentIfNotNull(categoryId),
            mediaCategoryId = Optional.presentIfNotNull(mediaId),
            search = Optional.presentIfNotNull(search?.takeIf { it.isNotBlank() }),
            subscribed = if (subscribed) Optional.present(true) else Optional.Absent,
            sort = Optional.present(sort),
        ))).Page
        return Paged(p?.threads.orEmpty().mapNotNull { it?.threadRow?.toItem() }, p?.pageInfo?.hasNextPage == true)
    }

    suspend fun thread(id: Int): ThreadItem {
        val t = api.run(api.apollo.query(ThreadDetailQuery(id))).Thread ?: error(t("Tópico não encontrado"))
        return t.threadRow.toItem().copy(body = t.body.orEmpty(), isSubscribed = t.isSubscribed == true)
    }

    suspend fun comments(threadId: Int, page: Int): Paged<CommentNode> {
        val p = api.run(api.apollo.query(ThreadCommentsQuery(threadId, page))).Page
        val list = p?.threadComments.orEmpty().filterNotNull().map { c ->
            CommentNode(
                id = c.id, user = c.user?.let { UserRef(it.id, it.name, it.avatar?.medium) }, text = c.comment.orEmpty(),
                likeCount = c.likeCount, isLiked = c.isLiked == true, createdAt = c.createdAt.toLong(), depth = 0,
                children = CommentJson.parseChildren(c.childComments, 1),
            )
        }
        return Paged(list, p?.pageInfo?.hasNextPage == true)
    }

    suspend fun comment(threadId: Int, parentId: Int?, text: String) {
        api.run(api.apollo.mutation(SaveThreadCommentMutation(threadId, Optional.presentIfNotNull(parentId), text.trim())))
    }

    suspend fun deleteComment(id: Int) { api.run(api.apollo.mutation(DeleteThreadCommentMutation(id))) }
}

/** O AniList devolve os comentários filhos como JSON solto (mesmos campos do pai, recursivo). */
object CommentJson {
    fun parseChildren(raw: Any?, depth: Int): List<CommentNode> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toInt() ?: return@mapNotNull null
            val u = m["user"] as? Map<*, *>
            val user = u?.let {
                val uid = (it["id"] as? Number)?.toInt() ?: return@let null
                UserRef(uid, it["name"] as? String ?: "", (it["avatar"] as? Map<*, *>)?.get("medium") as? String)
            }
            CommentNode(
                id = id, user = user, text = m["comment"] as? String ?: "",
                likeCount = (m["likeCount"] as? Number)?.toInt() ?: 0,
                isLiked = m["isLiked"] as? Boolean ?: false,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0,
                depth = depth,
                children = parseChildren(m["childComments"], depth + 1),
            )
        }
    }

    /** Achata a árvore na ordem de leitura. */
    fun flatten(nodes: List<CommentNode>): List<CommentNode> = nodes.flatMap { listOf(it) + flatten(it.children) }
}

private fun ActivityFields.toItem(): ActivityItem? {
    onListActivity?.let { a ->
        return ActivityItem(a.id, ActivityKind.LIST, a.user?.userRef?.toRef(), status = a.status, progress = a.progress,
            media = a.media?.mediaCore?.toMedia(), createdAt = a.createdAt.toLong(), replyCount = a.replyCount,
            likeCount = a.likeCount, isLiked = a.isLiked == true)
    }
    onTextActivity?.let { a ->
        return ActivityItem(a.id, ActivityKind.TEXT, a.user?.userRef?.toRef(), text = a.text.orEmpty(),
            createdAt = a.createdAt.toLong(), replyCount = a.replyCount, likeCount = a.likeCount, isLiked = a.isLiked == true)
    }
    onMessageActivity?.let { a ->
        return ActivityItem(a.id, ActivityKind.MESSAGE, a.messenger?.userRef?.toRef(), recipient = a.recipient?.userRef?.toRef(),
            text = a.message.orEmpty(), createdAt = a.createdAt.toLong(), replyCount = a.replyCount, likeCount = a.likeCount,
            isLiked = a.isLiked == true, isPrivate = a.isPrivate == true)
    }
    return null
}

private fun ReplyFields.toItem() = ReplyItem(id, user?.userRef?.toRef(), text.orEmpty(), createdAt.toLong(), likeCount, isLiked == true)

private fun ThreadRow.toItem(): ThreadItem {
    val mc = mediaCategories.orEmpty().filterNotNull().firstOrNull()
    return ThreadItem(
        id = id, title = title.orEmpty(), user = user?.userRef?.toRef(), replyUser = replyUser?.name,
        replyCount = replyCount ?: 0, viewCount = viewCount ?: 0, likeCount = likeCount, isLiked = isLiked == true,
        isSticky = isSticky == true, isLocked = isLocked == true, repliedAt = repliedAt?.toLong(), createdAt = createdAt.toLong(),
        categories = categories.orEmpty().filterNotNull().map { ForumCategories.name(it.id, it.name) },
        mediaTitle = mc?.title?.let { it.romaji ?: it.english ?: it.native }, mediaId = mc?.id, mediaCover = mc?.coverImage?.medium,
    )
}
