package dev.viko.listaanimes

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.viko.listaanimes.data.settings.Settings
import dev.viko.listaanimes.data.settings.ThemeMode
import dev.viko.listaanimes.ui.components.LocalSettings
import dev.viko.listaanimes.ui.nav.AppRoot
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.theme.ListaAnimesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Obra a abrir (vinda de uma notificação). */
    private val openMedia = mutableStateOf<Int?>(null)
    private val container get() = (application as App).container
    private var latest: Settings? = null

    /** O ícone só é trocado ao sair do app: trocar com o app aberto pode fechá-lo em alguns lançadores. */
    override fun onStop() {
        super.onStop()
        latest?.let { s -> runCatching { dev.viko.listaanimes.ui.AppIcon.apply(this, s.iconAccent ?: s.accent, s.iconDark) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // O splash do sistema some rápido; a animação de abertura (Compose) continua dali.
        // Segura o splash até ler as configurações (evita piscar claro → escuro).
        val firstSettings = mutableStateOf<Settings?>(null)
        splash.setKeepOnScreenCondition { firstSettings.value == null }
        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                if (firstSettings.value == null) firstSettings.value = s
                latest = s
                applySystemNightMode(s.theme)
            }
        }
        splash.setOnExitAnimationListener { v ->
            v.view.animate().alpha(0f).setDuration(120).withEndAction { v.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val first = firstSettings.value ?: return@setContent
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = first)
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.OLED -> true
            }
            androidx.compose.runtime.LaunchedEffect(dark, settings.oled, settings.theme) {
                val bg = if (dark) (if (settings.oled || settings.theme == ThemeMode.OLED) 0xFF000000.toInt() else 0xFF161616.toInt()) else 0xFFF6F4EF.toInt()
                val style = if (dark) SystemBarStyle.dark(bg) else SystemBarStyle.light(bg, bg)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ListaAnimesTheme(settings.theme, settings.accent, settings.materialYou, settings.oled) {
                CompositionLocalProvider(LocalSettings provides settings, LocalContainer provides container) {
                    androidx.compose.foundation.layout.Box {
                        AppRoot(openMedia = openMedia.value, onMediaOpened = { openMedia.value = null })
                        var intro by androidx.compose.runtime.remember { mutableStateOf(!dev.viko.listaanimes.ui.components.IntroState.played) }
                        if (intro) dev.viko.listaanimes.ui.components.IntroOverlay(onDone = { intro = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.data?.let { uri ->
            if (container.auth.handleRedirect(uri)) {
                lifecycleScope.launch { runCatching { container.lists.fullSync() } }
            }
        }
        val id = intent.getIntExtra(EXTRA_MEDIA_ID, -1)
        if (id > 0) openMedia.value = id
    }

    /** Android 12+: o splash do sistema segue o tema escolhido no app. */
    private fun applySystemNightMode(mode: ThemeMode) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val ui = getSystemService(android.app.UiModeManager::class.java) ?: return
        // Só avisa o sistema quando muda: aplicar de novo recria a tela e o splash volta.
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getString("night_mode", null) == mode.name) return
        prefs.edit().putString("night_mode", mode.name).apply()
        val want = when (mode) {
            ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK, ThemeMode.OLED -> android.app.UiModeManager.MODE_NIGHT_YES
        }
        runCatching { ui.setApplicationNightMode(want) }
    }

    companion object {
        const val EXTRA_MEDIA_ID = "media_id"
    }
}
