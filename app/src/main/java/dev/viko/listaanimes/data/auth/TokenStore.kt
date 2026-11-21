package dev.viko.listaanimes.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Uma conta do AniList guardada no aparelho (token cifrado). */
data class SavedAccount(val id: Int, val name: String, val avatar: String?, val iv: String, val token: String, val expiresAt: Long)

/** Guarda o token do AniList cifrado com AES-GCM, com a chave no Android Keystore. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(token: String, expiresAtEpoch: Long) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(token.toByteArray())
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(enc, Base64.NO_WRAP))
            .putLong("expiresAt", expiresAtEpoch)
            .apply()
    }

    fun load(): String? = runCatching {
        val iv = prefs.getString("iv", null) ?: return null
        val enc = prefs.getString("token", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)))
    }.getOrNull()

    val expiresAt: Long get() = prefs.getLong("expiresAt", 0)

    fun clear() = prefs.edit().remove("iv").remove("token").remove("expiresAt").remove("guest").apply()

    // ---------- Várias contas ----------
    // A conta ativa fica nas chaves iv/token/expiresAt; as outras ficam em "accounts" (JSON),
    // com o token cifrado do mesmo jeito (mesma chave do Keystore).

    fun saved(): List<SavedAccount> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("accounts", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedAccount(o.getInt("id"), o.getString("name"), o.optString("avatar").ifBlank { null },
                o.getString("iv"), o.getString("token"), o.getLong("expiresAt"))
        }
    }.getOrDefault(emptyList())

    private fun putSaved(list: List<SavedAccount>) {
        val arr = org.json.JSONArray()
        list.forEach { a ->
            arr.put(org.json.JSONObject().put("id", a.id).put("name", a.name).put("avatar", a.avatar ?: "")
                .put("iv", a.iv).put("token", a.token).put("expiresAt", a.expiresAt))
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    /** Guarda a conta ativa na lista de contas salvas e deixa a ativa vazia. */
    fun stashActive(id: Int, name: String, avatar: String?) {
        val iv = prefs.getString("iv", null) ?: return
        val token = prefs.getString("token", null) ?: return
        putSaved(saved().filter { it.id != id } + SavedAccount(id, name, avatar, iv, token, expiresAt))
        prefs.edit().remove("iv").remove("token").remove("expiresAt").apply()
    }

    /** Torna ativa uma conta salva (e a tira da lista). */
    fun activate(a: SavedAccount) {
        putSaved(saved().filter { it.id != a.id })
        prefs.edit().putString("iv", a.iv).putString("token", a.token).putLong("expiresAt", a.expiresAt).remove("guest").apply()
    }

    fun removeSaved(id: Int) = putSaved(saved().filter { it.id != id })

    /** Modo visitante (só no APK de debug): nome de um perfil público, sem token. */
    var guest: String?
        get() = prefs.getString("guest", null)
        set(v) { prefs.edit().putString("guest", v).apply() }

    private companion object {
        const val ALIAS = "anilist_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
