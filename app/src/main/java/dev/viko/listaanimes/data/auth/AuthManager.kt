package dev.viko.listaanimes.data.auth

import android.net.Uri
import dev.viko.listaanimes.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionState { LOGGED_OUT, LOGGED_IN, EXPIRED }

class AuthManager(private val store: TokenStore, private val nowEpoch: () -> Long = { System.currentTimeMillis() / 1000 }) {
    @Volatile private var cached: String? = store.load()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SessionState> = _state

    private fun initialState(): SessionState = when {
        BuildConfig.DEBUG && store.guest != null -> SessionState.LOGGED_IN
        cached == null -> if (store.expiresAt > 0) SessionState.EXPIRED else SessionState.LOGGED_OUT
        store.expiresAt in 1..nowEpoch() -> SessionState.EXPIRED
        else -> SessionState.LOGGED_IN
    }

    /** Token só é usado enquanto a sessão está válida. */
    fun token(): String? = if (_state.value == SessionState.LOGGED_IN) cached else null

    val hasClientId: Boolean get() = BuildConfig.ANILIST_CLIENT_ID.isNotBlank()

    /** Perfil público aberto em modo visitante (debug), ou null. */
    val guestName: String? get() = if (BuildConfig.DEBUG) store.guest else null

    fun enterGuest(name: String) {
        if (!BuildConfig.DEBUG) return
        store.guest = name.trim()
        _state.value = SessionState.LOGGED_IN
    }

    fun authorizeUrl(): Uri = Uri.parse("https://anilist.co/api/v2/oauth/authorize").buildUpon()
        .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
        .appendQueryParameter("response_type", "token")
        .build()

    /** Trata o retorno listaanimes://auth#access_token=...&expires_in=... */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != REDIRECT_SCHEME) return false
        val params = (uri.fragment ?: uri.query ?: return false).split("&").mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        val token = params["access_token"] ?: return false
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: (365L * 24 * 3600)
        store.save(token, nowEpoch() + expiresIn)
        store.guest = null
        cached = token
        _state.value = SessionState.LOGGED_IN
        return true
    }

    /** O AniList recusou o token: pede login de novo, sem apagar o cache. */
    fun markExpired() {
        if (_state.value == SessionState.LOGGED_IN) _state.value = SessionState.EXPIRED
    }

    // ---------- Várias contas ----------

    fun savedAccounts(): List<SavedAccount> = store.saved()

    /** Guarda a conta atual e abre o login para entrar com outra. */
    fun addAccount(currentId: Int, name: String, avatar: String?) {
        store.stashActive(currentId, name, avatar)
        cached = null
        _state.value = SessionState.LOGGED_OUT
    }

    /** Troca para uma conta salva; a atual (se houver) vai para a lista. */
    fun switchTo(target: SavedAccount, currentId: Int?, name: String?, avatar: String?) {
        if (currentId != null && name != null) store.stashActive(currentId, name, avatar)
        store.activate(target)
        cached = store.load()
        _state.value = initialState()
    }

    fun removeSaved(id: Int) = store.removeSaved(id)

    fun logout() {
        store.clear()
        cached = null
        _state.value = SessionState.LOGGED_OUT
    }

    companion object {
        const val REDIRECT_SCHEME = "listaanimes"
        const val REDIRECT_URI = "listaanimes://auth"
    }
}
