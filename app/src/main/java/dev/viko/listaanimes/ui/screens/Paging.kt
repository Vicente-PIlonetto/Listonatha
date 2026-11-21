package dev.viko.listaanimes.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.viko.listaanimes.data.repo.Paged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Lista paginada genérica, com atualização otimista de itens (curtir etc.). */
open class PagedViewModel<T>(private val loader: suspend (page: Int) -> Paged<T>) : ViewModel() {
    val items = MutableStateFlow<List<T>>(emptyList())
    val loading = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)
    private var page = 0
    private var job: Job? = null
    private var started = false

    fun start() { if (!started) { started = true; refresh() } }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            refreshing.value = true
            loading.value = true
            try {
                val r = loader(1)
                page = 1
                items.value = r.items
                done.value = !r.hasNext
                error.value = null
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error.value = e.toUserMessage()
            } finally { loading.value = false; refreshing.value = false }
        }
    }

    fun nextPage() {
        if (loading.value || done.value || page == 0) return
        job = viewModelScope.launch {
            loading.value = true
            try {
                val r = loader(page + 1)
                page++
                items.value = items.value + r.items
                done.value = !r.hasNext
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error.value = e.toUserMessage()
            } finally { loading.value = false }
        }
    }

    fun update(transform: (T) -> T) { items.value = items.value.map(transform) }
    fun remove(pred: (T) -> Boolean) { items.value = items.value.filterNot(pred) }
}

@Composable
fun OnEndReached(state: LazyListState, buffer: Int = 5, onEnd: () -> Unit) {
    val reached by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - buffer } }
    LaunchedEffect(state) { snapshotFlow { reached }.distinctUntilChanged().filter { it }.collect { onEnd() } }
}
