package dev.viko.listaanimes.ui.components

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dev.viko.listaanimes.domain.MediaKind

/**
 * Leitores com extensões (família Tachiyomi): o Listonatha só abre a busca global deles com o título.
 * Nada de fontes aqui; quem lê/assiste é o outro app.
 */
object ReaderApps {
    data class App(val name: String, val pkg: String, val manga: Boolean, val anime: Boolean)

    val apps = listOf(
        App("Komikku", "app.komikku", manga = true, anime = false),
        App("Mihon", "app.mihon", manga = true, anime = false),
        App("Aniyomi", "xyz.jmir.tachiyomi.mi", manga = true, anime = true),
    )

    /** Ações e extras que esses apps aceitam (MainActivity/DeepLinkActivity do Mihon e forks). */
    private const val SEARCH = "eu.kanade.tachiyomi.SEARCH"
    private const val ANIME_SEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
    private const val EXTRA_QUERY = "query"

    // O Aniyomi tem uma tela de busca para anime e outra para mangá.
    private const val ANIYOMI_ANIME = "eu.kanade.tachiyomi.ui.deeplink.anime.DeepLinkAnimeActivity"
    private const val ANIYOMI_MANGA = "eu.kanade.tachiyomi.ui.deeplink.manga.DeepLinkMangaActivity"

    fun installed(context: Context, kind: MediaKind): List<App> =
        apps.filter { (if (kind == MediaKind.ANIME) it.anime else it.manga) && StreamingApps.isInstalled(context, it.pkg) }

    fun icon(context: Context, app: App): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getApplicationIcon(app.pkg) }.getOrNull()

    /** Abre a busca global do app com o título; se a versão não aceitar, só abre o app. */
    fun open(context: Context, app: App, kind: MediaKind, query: String) {
        fun start(i: Intent): Boolean = try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

        val attempts = buildList {
            if (app.pkg == "xyz.jmir.tachiyomi.mi") {
                add(Intent(Intent.ACTION_SEARCH).setClassName(app.pkg, if (kind == MediaKind.ANIME) ANIYOMI_ANIME else ANIYOMI_MANGA)
                    .putExtra(SearchManager.QUERY, query))
                add(Intent(if (kind == MediaKind.ANIME) ANIME_SEARCH else SEARCH).setPackage(app.pkg).putExtra(EXTRA_QUERY, query))
            } else {
                add(Intent(SEARCH).setPackage(app.pkg).putExtra(EXTRA_QUERY, query))
                add(Intent(Intent.ACTION_SEARCH).setPackage(app.pkg).putExtra(SearchManager.QUERY, query))
            }
        }
        if (attempts.any { start(it) }) return
        context.packageManager.getLaunchIntentForPackage(app.pkg)?.let { start(it) }
    }

    val all: Set<String> get() = apps.map { it.pkg }.toSet()
}
