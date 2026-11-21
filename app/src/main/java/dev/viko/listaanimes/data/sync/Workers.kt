package dev.viko.listaanimes.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.viko.listaanimes.App
import dev.viko.listaanimes.data.api.ApiErrorKind
import dev.viko.listaanimes.data.api.ApiException
import dev.viko.listaanimes.data.db.NotifiedEpisode
import dev.viko.listaanimes.domain.ListStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Envia a fila quando houver conexão. */
    fun enqueueFlush(context: Context) {
        val req = OneTimeWorkRequestBuilder<FlushWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("flush", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** A cada 6 h, só com internet: sincroniza e agenda os avisos de episódio das próximas 24 h. */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<AiringCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("airing-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun runAiringCheckNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<AiringCheckWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork("airing-check-now", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWork()

    /**
     * Agrupa episódios próximos (até 30 min de diferença) em um aviso só,
     * agendado para o horário do último do grupo.
     */
    fun scheduleEpisodeAlerts(context: Context, episodes: List<Triple<Int, Int, Long>>, nowEpoch: Long) {
        val groups = groupByTime(episodes.filter { it.third > nowEpoch - 60 }.sortedBy { it.third }, 30 * 60)
        val wm = WorkManager.getInstance(context)
        groups.forEach { g ->
            val at = g.maxOf { it.third }
            val delay = ((at - nowEpoch) + 60).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<EpisodeAlertWorker>()
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .setInputData(workDataOf(
                    "media" to g.map { it.first }.toIntArray(),
                    "episodes" to g.map { it.second }.toIntArray(),
                ))
                .build()
            wm.enqueueUniqueWork("ep-alert-$at", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun <T> groupByTime(sorted: List<T>, windowSec: Long, time: (T) -> Long): List<List<T>> {
        val out = mutableListOf<MutableList<T>>()
        for (item in sorted) {
            val cur = out.lastOrNull()
            if (cur != null && time(item) - time(cur.first()) <= windowSec) cur += item else out += mutableListOf(item)
        }
        return out
    }

    private fun groupByTime(sorted: List<Triple<Int, Int, Long>>, windowSec: Long) =
        groupByTime(sorted, windowSec) { it.third }
}

class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as App).container
        // Sem sessão a fila espera: o login roda uma sincronização que envia tudo.
        if (c.auth.token() == null) return Result.success()
        return runCatching { c.lists.flush() }.fold(
            onSuccess = { if (it.complete) Result.success() else Result.retry() },
            onFailure = { Result.retry() },
        )
    }
}

class AiringCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as App).container
        if (c.auth.token() == null) return Result.success()
        return try {
            c.lists.fullSync(minIntervalSec = 30 * 60)
            val s = c.settings.current()
            val now = System.currentTimeMillis() / 1000
            if (s.notifyEpisodes) {
                val ids = c.db.entries().watchingAnimeIds()
                val airing = c.schedule.refresh(now, now + 24 * 3600, ids)
                WorkScheduler.scheduleEpisodeAlerts(applicationContext, airing.map { Triple(it.airing.mediaId, it.airing.episode, it.airing.airingAt) }, now)
            }
            if (s.notifyRelated || s.notifyDataChange || s.notifySocial) {
                val pushedAt = c.settings.notificationsPushedAt()
                val seenAt = c.settings.notificationsSeenAt.first()
                val list = c.notifications.refresh(markRead = false)
                val lang = s.titleLanguage
                val fresh = list.filter { it.createdAt > maxOf(pushedAt, seenAt) }.filter {
                    (it.kind == "RELATED" && s.notifyRelated) || (it.kind == "DATA_CHANGE" && s.notifyDataChange) ||
                        (it.kind == "SOCIAL" && s.notifySocial)
                }
                Notifier.postAniList(applicationContext, fresh.map { n -> if (n.kind == "SOCIAL") n.text else listOfNotNull(n.media?.title(lang), n.text).joinToString(" · ") })
                list.maxOfOrNull { it.createdAt }?.let { c.settings.setNotificationsPushedAt(it) }
            }
            Result.success()
        } catch (e: ApiException) {
            if (e.kind == ApiErrorKind.AUTH) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class EpisodeAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as App).container
        val s = c.settings.current()
        if (!s.notifyEpisodes) return Result.success()
        val ids = inputData.getIntArray("media") ?: return Result.success()
        val eps = inputData.getIntArray("episodes") ?: return Result.success()
        val now = System.currentTimeMillis() / 1000
        c.db.notified().prune(now - 30L * 24 * 3600)
        val items = ids.indices.mapNotNull { i ->
            val mediaId = ids[i]
            val ep = eps[i]
            val entry = c.db.entries().get(mediaId) ?: return@mapNotNull null
            if (entry.status != ListStatus.CURRENT && entry.status != ListStatus.REPEATING) return@mapNotNull null
            if (entry.progress >= ep) return@mapNotNull null
            if (c.db.notified().insert(NotifiedEpisode("$mediaId:$ep", now)) == -1L) return@mapNotNull null
            val media = c.db.media().get(mediaId) ?: return@mapNotNull null
            // Atualiza o cache: o episódio já saiu.
            if (media.nextEpisode == ep) c.db.media().upsert(media.copy(nextAiringAt = minOf(media.nextAiringAt ?: now, now)))
            Triple(mediaId, media.title(s.titleLanguage), ep)
        }
        Notifier.postEpisodes(applicationContext, items)
        return Result.success()
    }
}
