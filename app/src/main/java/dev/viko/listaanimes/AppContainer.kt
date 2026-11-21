package dev.viko.listaanimes

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.viko.listaanimes.data.api.AniListApi
import dev.viko.listaanimes.data.auth.AuthManager
import dev.viko.listaanimes.data.auth.TokenStore
import dev.viko.listaanimes.data.backup.BackupManager
import dev.viko.listaanimes.data.db.AppDatabase
import dev.viko.listaanimes.data.repo.ListRepository
import dev.viko.listaanimes.data.repo.MediaRepository
import dev.viko.listaanimes.data.repo.NotificationRepository
import dev.viko.listaanimes.data.repo.ReviewRepository
import dev.viko.listaanimes.data.repo.ScheduleRepository
import dev.viko.listaanimes.data.settings.SettingsRepository
import dev.viko.listaanimes.data.sync.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val auth = AuthManager(TokenStore(context))
    val api = AniListApi(tokenProvider = { auth.token() }, onUnauthorized = { auth.markExpired() })

    val lists = ListRepository(db, api, settings, json, requestFlush = { flushSoon() }, auth = auth)
    val media = MediaRepository(db, api, settings, json)
    val schedule = ScheduleRepository(db, api)
    val reviews = ReviewRepository(db, api, settings)
    val notifications = NotificationRepository(db, api, settings, json)
    val backup = BackupManager(context, db, settings)
    val social = dev.viko.listaanimes.data.repo.SocialRepository(api)

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Tenta enviar agora; se não der, o WorkManager envia quando a conexão voltar. */
    private fun flushSoon() {
        appScope.launch {
            val done = isOnline() && auth.token() != null && runCatching { lists.flush().complete }.getOrDefault(false)
            if (!done) WorkScheduler.enqueueFlush(context)
        }
    }
}
