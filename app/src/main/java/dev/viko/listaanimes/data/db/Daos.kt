package dev.viko.listaanimes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Upsert suspend fun upsert(media: List<Media>)
    @Upsert suspend fun upsert(media: Media)
    @Query("SELECT * FROM media WHERE id = :id") suspend fun get(id: Int): Media?
    @Query("SELECT * FROM media WHERE id = :id") fun observe(id: Int): Flow<Media?>
    @Query("SELECT * FROM media WHERE id IN (:ids)") suspend fun getAll(ids: List<Int>): List<Media>
    @Query("SELECT * FROM media WHERE isFavourite = 1 AND kind = :kind ORDER BY titleRomaji")
    fun observeFavourites(kind: MediaKind): Flow<List<Media>>
    @Query("UPDATE media SET isFavourite = 0 WHERE kind = :kind") suspend fun clearFavourites(kind: MediaKind)
    @Query("UPDATE media SET isFavourite = :fav WHERE id = :id") suspend fun setFavourite(id: Int, fav: Boolean)
}

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM list_entry WHERE kind = :kind ORDER BY updatedAt DESC")
    fun observeAll(kind: MediaKind): Flow<List<EntryWithMedia>>

    @Transaction
    @Query("SELECT * FROM list_entry ORDER BY updatedAt DESC")
    fun observeEverything(): Flow<List<EntryWithMedia>>

    @Transaction
    @Query("SELECT * FROM list_entry WHERE mediaId = :mediaId")
    fun observe(mediaId: Int): Flow<EntryWithMedia?>

    @Query("SELECT * FROM list_entry WHERE mediaId = :mediaId") suspend fun get(mediaId: Int): ListEntry?
    @Query("SELECT * FROM list_entry WHERE kind = :kind") suspend fun getAll(kind: MediaKind): List<ListEntry>
    @Query("SELECT mediaId FROM list_entry") fun observeIds(): Flow<List<Int>>
    @Query("SELECT mediaId FROM list_entry WHERE kind = 'ANIME' AND status IN ('CURRENT','REPEATING')")
    suspend fun watchingAnimeIds(): List<Int>
    @Query("SELECT mediaId FROM list_entry WHERE kind = 'ANIME'") suspend fun animeIds(): List<Int>
    @Upsert suspend fun upsert(entry: ListEntry)
    @Upsert suspend fun upsert(entries: List<ListEntry>)
    @Query("DELETE FROM list_entry WHERE mediaId = :mediaId") suspend fun delete(mediaId: Int)
    @Query("DELETE FROM list_entry WHERE kind = :kind AND mediaId NOT IN (:keep) AND mediaId NOT IN (SELECT mediaId FROM pending_change)")
    suspend fun deleteMissing(kind: MediaKind, keep: List<Int>)
    @Query("SELECT COUNT(*) FROM list_entry") suspend fun count(): Int
}

@Dao
interface SpecialDao {
    @Upsert suspend fun upsert(s: SpecialScore)
    @Query("DELETE FROM special_score WHERE mediaId = :mediaId") suspend fun delete(mediaId: Int)
    @Query("SELECT * FROM special_score") suspend fun all(): List<SpecialScore>
    @Query("SELECT * FROM special_score WHERE mediaId = :mediaId") suspend fun get(mediaId: Int): SpecialScore?
    @Query("DELETE FROM special_score") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<SpecialScore>)
}

@Dao
interface PendingDao {
    @Insert suspend fun insert(c: PendingChange): Long
    @Query("SELECT * FROM pending_change ORDER BY seq ASC") suspend fun all(): List<PendingChange>
    @Query("SELECT * FROM pending_change ORDER BY seq DESC LIMIT 1") suspend fun last(): PendingChange?
    @Query("DELETE FROM pending_change WHERE seq = :seq") suspend fun delete(seq: Long)
    @Query("UPDATE pending_change SET payload = :payload, createdAt = :at WHERE seq = :seq")
    suspend fun replacePayload(seq: Long, payload: String, at: Long)
    @Query("UPDATE pending_change SET attempts = attempts + 1 WHERE seq = :seq") suspend fun bumpAttempts(seq: Long)
    @Query("SELECT COUNT(*) FROM pending_change") fun observeCount(): Flow<Int>
    @Query("SELECT mediaId FROM pending_change") suspend fun pendingMediaIds(): List<Int>
}

@Dao
interface ReviewDao {
    @Upsert suspend fun upsert(r: ReviewEntity)
    @Upsert suspend fun upsert(r: List<ReviewEntity>)
    @Query("DELETE FROM review WHERE id = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM review WHERE mine = 1") suspend fun clearMine()
    @Transaction
    @Query("SELECT * FROM review WHERE mine = 1 ORDER BY updatedAt DESC") fun observeMine(): Flow<List<ReviewWithMedia>>
    @Query("SELECT * FROM review WHERE mine = 1 AND mediaId = :mediaId LIMIT 1") fun observeMineFor(mediaId: Int): Flow<ReviewEntity?>
    @Query("SELECT * FROM review WHERE mine = 1 AND mediaId = :mediaId LIMIT 1") suspend fun mineFor(mediaId: Int): ReviewEntity?
    @Query("SELECT * FROM review WHERE mediaId = :mediaId AND mine = 0 ORDER BY rating DESC") fun observeOthers(mediaId: Int): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM review WHERE id = :id") fun observe(id: Int): Flow<ReviewEntity?>
}

@Dao
interface DraftDao {
    @Upsert suspend fun upsert(d: ReviewDraft)
    @Query("SELECT * FROM review_draft WHERE mediaId = :mediaId") suspend fun get(mediaId: Int): ReviewDraft?
    @Query("DELETE FROM review_draft WHERE mediaId = :mediaId") suspend fun delete(mediaId: Int)
    @Query("SELECT * FROM review_draft") suspend fun all(): List<ReviewDraft>
    @Query("DELETE FROM review_draft") suspend fun clear()
}

@Dao
interface AiringDao {
    @Upsert suspend fun upsert(list: List<AiringEntity>)
    @Query("DELETE FROM airing WHERE airingAt < :before") suspend fun deleteBefore(before: Long)
    @Query("DELETE FROM airing WHERE airingAt BETWEEN :from AND :to") suspend fun deleteRange(from: Long, to: Long)
    @Transaction
    @Query("SELECT * FROM airing WHERE airingAt BETWEEN :from AND :to ORDER BY airingAt")
    fun observeRange(from: Long, to: Long): Flow<List<AiringWithMedia>>
}

@Dao
interface CacheDao {
    @Upsert suspend fun put(b: CacheBlob)
    @Query("SELECT * FROM cache_blob WHERE `key` = :key") suspend fun get(key: String): CacheBlob?
    @Query("SELECT * FROM cache_blob WHERE `key` = :key") fun observe(key: String): Flow<CacheBlob?>
}

@Dao
interface NotifiedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(n: NotifiedEpisode): Long
    @Query("DELETE FROM notified_episode WHERE at < :before") suspend fun prune(before: Long)
}
