package dev.viko.listaanimes.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Abre um link de streaming no app do serviço. Se o app não estiver instalado,
 * abre a página dele na Play Store; serviços sem app conhecido vão para o navegador.
 */
object StreamingApps {
    /** Nome do site no AniList (minúsculo) → pacote do app Android. */
    private val packages = mapOf(
        "crunchyroll" to "com.crunchyroll.crunchyroid",
        "netflix" to "com.netflix.mediaclient",
        "disney plus" to "com.disney.disneyplus",
        "amazon prime video" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.avod.thirdpartyclient",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "max" to "com.wbd.stream",
        "hbo max" to "com.wbd.stream",
        "hulu" to "com.hulu.plus",
        "youtube" to "com.google.android.youtube",
        "bilibili tv" to "com.bstar.intl",
        "bilibili" to "com.bstar.intl",
        "tubi tv" to "com.tubitv",
        "tubi" to "com.tubitv",
        "iqiyi" to "com.iqiyi.i18n",
        "viki" to "com.viki.android",
        "globoplay" to "com.globo.globotv",
        "pluto tv" to "tv.pluto.android",
        "ani-one asia" to "com.google.android.youtube",
        "muse asia" to "com.google.android.youtube",
        "webtoon" to "com.naver.linewebtoon",
        "manga plus" to "jp.co.shueisha.mangaplus",
        "mangaplus" to "jp.co.shueisha.mangaplus",
        "tapas" to "com.tapastic",
        "kakaopage" to "com.kakao.page",
        "piccoma" to "jp.kakao.piccoma",
        "line manga" to "jp.naver.linemanga.android",
        "shonen jump" to "com.viz.wsj.android",
        "viz" to "com.viz.wsj.android",
    )

    fun packageFor(site: String): String? = packages[site.trim().lowercase()]

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** Situação do app para mostrar ao lado do serviço. */
    enum class State { INSTALLED, STORE, WEB }

    fun state(context: Context, site: String): State {
        val pkg = packageFor(site) ?: return State.WEB
        return if (isInstalled(context, pkg)) State.INSTALLED else State.STORE
    }

    fun open(context: Context, site: String, url: String) {
        val pkg = packageFor(site)
        fun start(i: Intent) = context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (pkg == null) { runCatching { start(view) }; return }
        if (isInstalled(context, pkg)) {
            // O link no próprio app (abre a obra); se o app não aceitar o link, abre o app.
            try { start(Intent(view).setPackage(pkg)); return } catch (_: ActivityNotFoundException) {}
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { start(it) }; return }
            runCatching { start(view) }
            return
        }
        try { start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
        catch (_: ActivityNotFoundException) { runCatching { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) } }
    }

    /** Ícone do app instalado (o mesmo do lançador), ou null. */
    fun installedIcon(context: Context, site: String): android.graphics.drawable.Drawable? {
        val pkg = packageFor(site) ?: return null
        return runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    /** Todos os pacotes, para declarar no manifesto (visibilidade de pacotes no Android 11+). */
    val all: Set<String> get() = packages.values.toSet()
}
