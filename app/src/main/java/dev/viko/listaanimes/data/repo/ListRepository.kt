package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.i18n.t
import androidx.room.withTransaction
import com.apollographql.apollo.api.Optional
import dev.viko.listaanimes.data.api.AniListApi
import dev.viko.listaanimes.data.api.ApiErrorKind
import dev.viko.listaanimes.data.api.ApiException
import dev.viko.listaanimes.data.db.AppDatabase
import dev.viko.listaanimes.data.db.ChangeType
import dev.viko.listaanimes.data.db.EntryWithMedia
import dev.viko.listaanimes.data.db.PendingChange
import dev.viko.listaanimes.data.db.SpecialScore
import dev.viko.listaanimes.data.settings.SettingsRepository
import dev.viko.listaanimes.data.sync.ChangeQueue
import dev.viko.listaanimes.data.sync.FlushResult
import dev.viko.listaanimes.data.sync.QueueStore
import dev.viko.listaanimes.data.sync.SendResult
import dev.viko.listaanimes.domain.EntryPatch
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.ProgressRules
import dev.viko.listaanimes.domain.TitleLanguage
import dev.viko.listaanimes.domain.score.SpecialGrade
import dev.viko.listaanimes.domain.score.SpecialScores
import dev.viko.listaanimes.graphql.DeleteEntryMutation
import dev.viko.listaanimes.graphql.ListCollectionQuery
import dev.viko.listaanimes.graphql.SaveEntryMutation
import dev.viko.listaanimes.graphql.ToggleFavouriteMutation
import dev.viko.listaanimes.graphql.ViewerQuery
import dev.viko.listaanimes.graphql.type.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

class ListRepository(
    private val db: AppDatabase,
    private val api: AniListApi,
    private val settings: SettingsRepository,
    private val json: Json,
    private val requestFlush: () -> Unit,
    private val auth: dev.viko.listaanimes.data.auth.AuthManager? = null,
) {
    private val zone = ZoneId.systemDefault()
    private fun now() = System.currentTimeMillis() / 1000
    private fun today() = LocalDate.now(zone)

    val queue = ChangeQueue(object : QueueStore {
        override suspend fun all() = db.pending().all()
        override suspend fun last() = db.pending().last()
        override suspend fun insert(change: PendingChange) = db.pending().insert(change)
        override suspend fun replacePayload(seq: Long, payload: String, at: Long) = db.pending().replacePayload(seq, payload, at)
        override suspend fun delete(seq: Long) = db.pending().delete(seq)
        override suspend fun bumpAttempts(seq: Long) = db.pending().bumpAttempts(seq)
    })

    fun observe(kind: MediaKind): Flow<List<EntryWithMedia>> = db.entries().observeAll(kind)
    fun observeAll(): Flow<List<EntryWithMedia>> = db.entries().observeEverything()
    fun observeEntry(mediaId: Int): Flow<EntryWithMedia?> = db.entries().observe(mediaId)
    fun observeIds(): Flow<List<Int>> = db.entries().observeIds()
    fun pendingCount(): Flow<Int> = db.pending().observeCount()

    // ---------- Sincronização ----------

    private val syncMutex = Mutex()

    /** Envia a fila e baixa as listas completas. O AniList é a fonte da verdade. */
    suspend fun fullSync(minIntervalSec: Long = 0): Unit = syncMutex.withLock {
        // Outra sincronização acabou de rodar (ex.: login + abertura do Início): não repete.
        if (minIntervalSec > 0 && now() - settings.lastSync.first() < minIntervalSec) return@withLock
        auth?.guestName?.let { guest -> return@withLock syncGuest(guest) }
        // Descobre de quem é o token ANTES de enviar a fila: se outra pessoa entrou
        // neste aparelho, os dados da conta anterior não podem ir para a conta nova.
        val viewer = api.run(api.apollo.query(ViewerQuery())).Viewer
            ?: throw ApiException(ApiErrorKind.AUTH, t("Sem usuário"))
        val previous = settings.viewerIdNow()
        if (previous != null && previous != viewer.id) {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            settings.resetAccountState()
        }
        settings.setViewerId(viewer.id)
        flush()
        val s = settings.current()
        if (s.titleLanguageFromAccount) {
            settings.setTitleLanguage(TitleLanguage.fromAniList(viewer.options?.titleLanguage?.rawValue), fromAccount = true)
        }
        pullList(viewer.id, MediaKind.ANIME)
        pullList(viewer.id, MediaKind.MANGA)

        val favAnime = viewer.favourites?.anime?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }
        val favManga = viewer.favourites?.manga?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }
        db.withTransaction {
            db.media().clearFavourites(MediaKind.ANIME)
            db.media().clearFavourites(MediaKind.MANGA)
            db.media().upsert((favAnime + favManga).map { it.copy(isFavourite = true) })
        }
        val profile = Profile(
            id = viewer.id, name = viewer.name, avatar = viewer.avatar?.large, banner = viewer.bannerImage, about = viewer.about.orEmpty(),
            favCharacters = viewer.favourites?.characters?.nodes.orEmpty().mapNotNull { n -> n?.let { FavCharacter(it.id, it.name?.full ?: return@mapNotNull null, it.image?.medium) } },
            animeCount = viewer.statistics?.anime?.count ?: 0,
            animeMean = viewer.statistics?.anime?.meanScore ?: 0.0,
            minutesWatched = viewer.statistics?.anime?.minutesWatched ?: 0,
            episodesWatched = viewer.statistics?.anime?.episodesWatched ?: 0,
            mangaCount = viewer.statistics?.manga?.count ?: 0,
            mangaMean = viewer.statistics?.manga?.meanScore ?: 0.0,
            chaptersRead = viewer.statistics?.manga?.chaptersRead ?: 0,
            volumesRead = viewer.statistics?.manga?.volumesRead ?: 0,
        )
        db.cache().put(dev.viko.listaanimes.data.db.CacheBlob("profile", json.encodeToString(Profile.serializer(), profile), now()))
        settings.setLastSync(now())
    }

    /** Modo visitante (debug): baixa a lista pública de outra pessoa, só leitura. */
    private suspend fun syncGuest(name: String) {
        val u = api.run(api.apollo.query(dev.viko.listaanimes.graphql.UserProfileQuery(name = com.apollographql.apollo.api.Optional.present(name)))).User
            ?: throw ApiException(ApiErrorKind.REJECTED, t("Usuário não encontrado"))
        val previous = settings.viewerIdNow()
        if (previous != null && previous != u.id) {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            settings.resetAccountState()
        }
        settings.setViewerId(u.id)
        pullList(u.id, MediaKind.ANIME)
        pullList(u.id, MediaKind.MANGA)
        val favs = u.favourites?.anime?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() } +
            u.favourites?.manga?.nodes.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }
        db.media().upsert(favs.map { it.copy(isFavourite = true) })
        val profile = Profile(
            id = u.id, name = u.name, avatar = u.avatar?.large, banner = u.bannerImage,
            animeCount = u.statistics?.anime?.count ?: 0, animeMean = u.statistics?.anime?.meanScore ?: 0.0,
            minutesWatched = u.statistics?.anime?.minutesWatched ?: 0, episodesWatched = u.statistics?.anime?.episodesWatched ?: 0,
            mangaCount = u.statistics?.manga?.count ?: 0, mangaMean = u.statistics?.manga?.meanScore ?: 0.0,
            chaptersRead = u.statistics?.manga?.chaptersRead ?: 0, volumesRead = u.statistics?.manga?.volumesRead ?: 0,
            about = u.about.orEmpty(),
            favCharacters = u.favourites?.characters?.nodes.orEmpty().mapNotNull { n -> n?.let { FavCharacter(it.id, it.name?.full ?: return@mapNotNull null, it.image?.medium) } },
        )
        db.cache().put(dev.viko.listaanimes.data.db.CacheBlob("profile", json.encodeToString(Profile.serializer(), profile), now()))
        settings.setLastSync(now())
    }

    private suspend fun pullList(userId: Int, kind: MediaKind) {
        val type = if (kind == MediaKind.ANIME) MediaType.ANIME else MediaType.MANGA
        val entries = mutableListOf<ListEntry>()
        val media = mutableListOf<Media>()
        var chunk = 1
        while (true) {
            val col = api.run(api.apollo.query(ListCollectionQuery(userId, type, chunk))).MediaListCollection ?: break
            col.lists.orEmpty().filterNotNull().flatMap { it.entries.orEmpty().filterNotNull() }.forEach { e ->
                entries += e.entryFields.toEntry(kind)
                e.media?.mediaCore?.toMedia()?.let { media += it }
            }
            if (col.hasNextChunk != true) break
            chunk++
        }
        val pending = db.pending().pendingMediaIds().toSet()
        val specials = db.specials().all().associateBy { it.mediaId }
        db.withTransaction {
            // Preserva o favorito, que vem do Viewer.
            val favs = db.media().getAll(media.map { it.id }).filter { it.isFavourite }.map { it.id }.toSet()
            db.media().upsert(media.map { it.copy(isFavourite = it.isFavourite || it.id in favs) })
            val fresh = entries.filter { it.mediaId !in pending }
            db.entries().upsert(fresh)
            // Nota mudada fora do app: a especial guardada deixa de valer.
            fresh.forEach { e ->
                val sp = specials[e.mediaId] ?: return@forEach
                if (SpecialScores.reconcile(sp.position, e.scoreRaw) == null) db.specials().delete(e.mediaId)
            }
            db.entries().deleteMissing(kind, entries.map { it.mediaId })
        }
    }

    // ---------- Edição local + fila ----------

    /** Grava localmente, põe na fila e tenta enviar. [special] = posição 1–12, ou null para remover a especial. */
    suspend fun save(entry: ListEntry, special: Int? = null) {
        val e = if (special != null) entry.copy(scoreRaw = SpecialScores.compress(special)) else entry
        db.withTransaction {
            db.entries().upsert(e.copy(updatedAt = now()))
            if (special != null) db.specials().upsert(SpecialScore(e.mediaId, special))
            else db.specials().get(e.mediaId)?.let { sp ->
                if (SpecialGrade.fromPosition(sp.position)?.compressed != e.scoreRaw) db.specials().delete(e.mediaId)
            }
            queue.enqueueSave(EntryPatch.of(e))
        }
        requestFlush()
    }

    suspend fun increment(mediaId: Int): ListEntry? {
        val entry = db.entries().get(mediaId) ?: return null
        val media = db.media().get(mediaId) ?: return null
        if (!ProgressRules.canIncrement(entry, media)) return entry
        val updated = ProgressRules.increment(entry, media, today(), now())
        saveKeepingSpecial(updated)
        return updated
    }

    suspend fun setProgress(mediaId: Int, value: Int): ListEntry? {
        val entry = db.entries().get(mediaId) ?: return null
        val media = db.media().get(mediaId) ?: return null
        val updated = ProgressRules.applyProgress(entry, media, value, today(), now())
        saveKeepingSpecial(updated)
        return updated
    }

    /** Mudança de progresso/status não mexe na nota especial. */
    private suspend fun saveKeepingSpecial(entry: ListEntry) {
        val sp = db.specials().get(entry.mediaId)?.position
        save(entry, sp?.takeIf { SpecialGrade.fromPosition(it)?.compressed == entry.scoreRaw })
    }

    fun applyProgressPreview(entry: ListEntry, media: Media, value: Int): ListEntry =
        ProgressRules.applyProgress(entry, media, value, today(), now())

    /** Adiciona à lista ou muda o status, com as datas e o progresso automáticos. */
    suspend fun add(media: Media, status: ListStatus) {
        val existing = db.media().get(media.id)
        db.media().upsert(media.copy(isFavourite = media.isFavourite || existing?.isFavourite == true))
        val base = db.entries().get(media.id) ?: ListEntry(mediaId = media.id, kind = media.kind, status = status, createdAt = now())
        saveKeepingSpecial(ProgressRules.applyStatus(base, media, status, today(), now()))
    }

    suspend fun remove(mediaId: Int) {
        val e = db.entries().get(mediaId) ?: return
        db.withTransaction {
            db.entries().delete(mediaId)
            db.specials().delete(mediaId)
            if (e.entryId != null) queue.enqueueDelete(mediaId, e.entryId)
            else {
                // Criada só offline: some da fila também.
                db.pending().all().filter { it.mediaId == mediaId }.forEach { db.pending().delete(it.seq) }
            }
        }
        requestFlush()
    }

    suspend fun setSpecial(mediaId: Int, position: Int?) {
        val e = db.entries().get(mediaId) ?: return
        save(e, position)
    }

    /** Favorito só online (o AniList só oferece "alternar"). */
    suspend fun toggleFavourite(media: Media): Boolean {
        val m = if (media.kind == MediaKind.ANIME) ToggleFavouriteMutation(animeId = Optional.present(media.id))
        else ToggleFavouriteMutation(mangaId = Optional.present(media.id))
        api.run(api.apollo.mutation(m))
        val fav = !media.isFavourite
        db.media().upsert(media.copy(isFavourite = fav))
        db.media().setFavourite(media.id, fav)
        return fav
    }

    // ---------- Envio da fila ----------

    suspend fun flush(): FlushResult = queue.flush(sender = { change ->
        try {
            when (change.type) {
                ChangeType.SAVE -> {
                    val p = queue.decodePatch(change)
                    val res = api.run(api.apollo.mutation(SaveEntryMutation(
                        mediaId = Optional.present(p.mediaId),
                        status = Optional.present(p.status.toApi()),
                        scoreRaw = Optional.present(p.scoreRaw),
                        progress = Optional.present(p.progress),
                        progressVolumes = Optional.present(p.progressVolumes),
                        repeat = Optional.present(p.repeat),
                        notes = Optional.present(p.notes ?: ""),
                        private = Optional.present(p.isPrivate),
                        startedAt = Optional.present(p.startedAt.toInput()),
                        completedAt = Optional.present(p.completedAt.toInput()),
                    )))
                    SendResult.Sent(res.SaveMediaListEntry?.entryFields?.id)
                }
                ChangeType.DELETE -> {
                    api.run(api.apollo.mutation(DeleteEntryMutation(change.payload.toInt())))
                    SendResult.Sent()
                }
            }
        } catch (e: ApiException) {
            if (e.kind == ApiErrorKind.REJECTED) SendResult.Rejected(e.message ?: "") else SendResult.RetryLater
        }
    }, onSent = { change, sent ->
        if (change.type == ChangeType.SAVE && sent.entryId != null) {
            db.entries().get(change.mediaId)?.let { if (it.entryId == null) db.entries().upsert(it.copy(entryId = sent.entryId)) }
        }
    })
}
