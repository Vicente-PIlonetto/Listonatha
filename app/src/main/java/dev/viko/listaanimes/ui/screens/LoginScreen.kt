package dev.viko.listaanimes.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import dev.viko.listaanimes.i18n.t
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.BuildConfig
import dev.viko.listaanimes.data.repo.BrowseParams
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.ui.components.Cover
import dev.viko.listaanimes.ui.components.enterAnimation
import dev.viko.listaanimes.ui.nav.LocalContainer
import dev.viko.listaanimes.ui.nav.startLogin
import dev.viko.listaanimes.ui.theme.AppTheme

@Composable
fun LoginScreen() {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val p = AppTheme.palette
    var covers by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        // Capas populares (consulta pública, sem login) para o fundo.
        runCatching { c.media.browse(BrowseParams(MediaKind.ANIME, listOf("TRENDING_DESC")), 1) }
            .onSuccess { r -> covers = r.items.mapNotNull { it.coverLarge } }
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        if (covers.isNotEmpty()) CoverWall(covers)
        // Escurece de baixo para cima para o texto ler bem.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to p.background.copy(alpha = 0.25f), 0.45f to p.background.copy(alpha = 0.75f), 0.7f to p.background,
        )))
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("Listonatha", style = MaterialTheme.typography.displaySmall, color = p.text, modifier = Modifier.enterAnimation(0))
            Spacer(Modifier.height(8.dp))
            Text(
                t("Sua lista do AniList."),
                style = MaterialTheme.typography.bodyLarge, color = p.textSecondary, modifier = Modifier.enterAnimation(1),
            )
            Spacer(Modifier.height(28.dp))
            if (!c.auth.hasClientId) {
                Text(
                    t("Client ID não configurado. Coloque ANILIST_CLIENT_ID em local.properties e compile de novo (veja o README)."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = { startLogin(ctx, c) },
                enabled = c.auth.hasClientId,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                modifier = Modifier.fillMaxWidth().height(54.dp).enterAnimation(2),
            ) { Text(t("Entrar com AniList"), style = MaterialTheme.typography.titleMedium) }
            // Contas já usadas neste aparelho: volta para elas sem novo login.
            val saved = remember { c.auth.savedAccounts() }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            saved.forEach { a ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt)
                        .clickable { c.auth.switchTo(a, null, null, null); scope.launch { runCatching { c.lists.fullSync() } } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dev.viko.listaanimes.ui.components.Avatar(a.avatar, 36.dp, Modifier.clip(androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(t("Entrar como {0}", a.name), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (BuildConfig.DEBUG) {
                // Só no APK de debug: abre a lista pública de alguém, sem login, para testar telas.
                var name by remember { mutableStateOf("") }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(t("Visitante (debug)")) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    TextButton(enabled = name.isNotBlank(), onClick = { c.auth.enterGuest(name) }) { Text(t("Abrir")) }
                }
            }
        }
    }
}

/** Três fileiras de capas deslizando devagar, em sentidos alternados. */
@Composable
private fun CoverWall(covers: List<String>) {
    val t = rememberInfiniteTransition(label = "wall")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).graphicsLayer { rotationZ = -8f; scaleX = 1.25f; scaleY = 1.25f },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { row ->
            val dir = if (row % 2 == 0) 1f else -1f
            Row(
                Modifier.wrapContentWidth(unbounded = true).graphicsLayer { translationX = dir * (shift - 0.5f) * 600.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val slice = (covers + covers + covers).drop(row * 10).take(10)
                slice.forEach { url -> Cover(url, 104.dp, Modifier.graphicsLayer { alpha = 0.9f }) }
            }
        }
    }
}

@Suppress("unused")
private val unusedColor = Color.Transparent
