package dev.viko.listaanimes.data.sync

import dev.viko.listaanimes.i18n.t
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.viko.listaanimes.MainActivity
import dev.viko.listaanimes.R

object Notifier {
    const val CH_EPISODES = "episodes"
    const val CH_ANILIST = "anilist"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_EPISODES, t("Episódios novos"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ANILIST, t("Avisos do AniList"), NotificationManager.IMPORTANCE_LOW))
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(context: Context, mediaId: Int?): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (mediaId != null) putExtra(MainActivity.EXTRA_MEDIA_ID, mediaId)
        }
        return PendingIntent.getActivity(context, mediaId ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Uma notificação só, mesmo com vários episódios no mesmo horário. */
    fun postEpisodes(context: Context, items: List<Triple<Int, String, Int>>) {
        if (items.isEmpty() || !hasPermission(context)) return
        val b = NotificationCompat.Builder(context, CH_EPISODES)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        if (items.size == 1) {
            val (mediaId, title, ep) = items.first()
            b.setContentTitle(t("{0} · EP {1} disponível", title, ep)).setContentIntent(openIntent(context, mediaId))
        } else {
            val style = NotificationCompat.InboxStyle()
            items.forEach { (_, title, ep) -> style.addLine(t("{0} · EP {1}", title, ep)) }
            b.setContentTitle(t("{0} episódios disponíveis", items.size))
                .setContentText(items.joinToString(", ") { it.second })
                .setStyle(style)
                .setContentIntent(openIntent(context, null))
        }
        val id = 1000 + (items.first().first * 31 + items.first().third) % 100000
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    fun postAniList(context: Context, lines: List<String>) {
        if (lines.isEmpty() || !hasPermission(context)) return
        val b = NotificationCompat.Builder(context, CH_ANILIST)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
        if (lines.size == 1) b.setContentTitle(lines.first())
        else {
            val style = NotificationCompat.InboxStyle()
            lines.take(6).forEach { style.addLine(it) }
            b.setContentTitle(t("{0} avisos do AniList", lines.size)).setContentText(lines.first()).setStyle(style)
        }
        runCatching { NotificationManagerCompat.from(context).notify(42, b.build()) }
    }
}
