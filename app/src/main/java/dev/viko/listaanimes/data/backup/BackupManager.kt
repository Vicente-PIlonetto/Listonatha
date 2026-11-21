package dev.viko.listaanimes.data.backup

import dev.viko.listaanimes.i18n.t
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.viko.listaanimes.data.db.AppDatabase
import dev.viko.listaanimes.data.db.ReviewDraft
import dev.viko.listaanimes.data.db.SpecialScore
import dev.viko.listaanimes.data.settings.Settings
import dev.viko.listaanimes.data.settings.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

/** O que só existe no app (o resto o AniList guarda). */
@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Settings,
    val specialScores: List<SpecialScore>,
    val reviewDrafts: List<ReviewDraft>,
    val profileStyle: dev.viko.listaanimes.data.settings.ProfileStyle = dev.viko.listaanimes.data.settings.ProfileStyle(),
)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun build(): Backup = Backup(
        exportedAt = System.currentTimeMillis() / 1000,
        settings = settings.current(),
        specialScores = db.specials().all(),
        reviewDrafts = db.drafts().all(),
        profileStyle = settings.profileStyle.first(),
    )

    fun encode(b: Backup): String = json.encodeToString(Backup.serializer(), b)
    fun decode(text: String): Backup = json.decodeFromString(Backup.serializer(), text)

    suspend fun export(uri: Uri) {
        val text = encode(build())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error(t("Não foi possível abrir o arquivo"))
    }

    /** Substitui notas especiais, rascunhos e configurações pelo conteúdo do arquivo. */
    suspend fun import(uri: Uri): Backup {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error(t("Não foi possível ler o arquivo"))
        val b = decode(text)
        db.withTransaction {
            db.specials().clear()
            db.specials().insertAll(b.specialScores)
            db.drafts().clear()
            b.reviewDrafts.forEach { db.drafts().upsert(it) }
        }
        settings.restore(b.settings)
        settings.setProfileStyle(b.profileStyle)
        return b
    }
}
