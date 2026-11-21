package dev.viko.listaanimes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.viko.listaanimes.ui.theme.AppTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A animação de abertura só toca quando o app é carregado na memória. */
object IntroState { var played = false }

/**
 * Abertura: o logo (três barras e o ponto) se monta, o nome aparece
 * e tudo se expande revelando o app. Fica por cima do conteúdo, que já carrega embaixo.
 */
@Composable
fun IntroOverlay(onDone: () -> Unit) {
    val p = AppTheme.palette
    val bars = remember { List(3) { Animatable(0f) } }
    val dot = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Começa igual ao splash do sistema (logo completo) e ganha vida a partir dali.
        bars.forEach { it.snapTo(1f) }
        dot.snapTo(1f)
        coroutineScope {
            // Onda nas barras: encolhem e voltam, uma depois da outra.
            bars.forEachIndexed { i, a ->
                launch {
                    delay(i * 80L)
                    a.animateTo(0.35f, tween(180, easing = FastOutSlowInEasing))
                    a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
                }
            }
            // O ponto pula e cai quicando.
            launch { delay(120); dot.animateTo(2.2f, tween(220, easing = FastOutSlowInEasing)); dot.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f)) }
            launch { delay(380); title.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        }
        delay(220)
        exit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        IntroState.played = true
        onDone()
    }

    val splashAccent = androidx.compose.ui.res.colorResource(dev.viko.listaanimes.R.color.accent)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(p.background),
    ) {
        // Logo exatamente no centro, como no splash do sistema.
        Canvas(
            Modifier.align(Alignment.Center).size(288.dp).graphicsLayer {
                val s = 1f + exit.value * 2.2f
                scaleX = s; scaleY = s
            },
        ) {
            val k = size.minDimension / 108f
            val r = CornerRadius(2f * k)
            bars.forEachIndexed { i, a ->
                val y = (40f + i * 12f) * k
                drawRoundRect(p.text, Offset(36f * k, y), Size(28f * k * a.value, 4f * k), r)
            }
            // dot: 1 = no lugar; >1 = acima (pulo). Troca da cor do splash para a do app durante o pulo.
            val lift = (dot.value - 1f).coerceAtLeast(0f)
            val colorK = (title.value * 1.5f).coerceIn(0f, 1f)
            drawCircle(androidx.compose.ui.graphics.lerp(splashAccent, p.accent, maxOf(colorK, (lift / 1.2f).coerceIn(0f, 1f))), radius = 4f * k, center = Offset(74f * k, (42f - lift * 14f) * k))
        }
        Text(
            "Listonatha",
            style = MaterialTheme.typography.headlineSmall,
            color = p.text,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                alpha = title.value * (1f - exit.value)
                translationY = 70.dp.toPx() + (1f - title.value) * 24.dp.toPx()
            },
        )
    }
}
