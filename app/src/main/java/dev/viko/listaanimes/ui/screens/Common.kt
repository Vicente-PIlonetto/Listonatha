package dev.viko.listaanimes.ui.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.viko.listaanimes.AppContainer
import dev.viko.listaanimes.data.api.ApiException
import dev.viko.listaanimes.ui.nav.LocalContainer

@Composable
inline fun <reified T : ViewModel> appViewModel(key: String? = null, noinline create: (AppContainer) -> T): T {
    val c = LocalContainer.current
    return viewModel(key = key) { create(c) }
}

val SYNC_ERROR get() = dev.viko.listaanimes.i18n.t("Não foi possível sincronizar.")
val LOAD_ERROR get() = dev.viko.listaanimes.i18n.t("Não foi possível carregar. Tente de novo.")

fun Throwable.toUserMessage(fallback: String = LOAD_ERROR): String =
    (this as? ApiException)?.userMessage ?: fallback
