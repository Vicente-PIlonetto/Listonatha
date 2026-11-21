package dev.viko.listaanimes.data.settings

import dev.viko.listaanimes.i18n.t
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.viko.listaanimes.domain.TitleLanguage
import dev.viko.listaanimes.domain.score.ScoreSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) { SYSTEM(t("Sistema")), LIGHT(t("Claro")), DARK(t("Escuro")), OLED(t("Preto (OLED)")) }

/** Cor de destaque: (claro, escuro). */
@Serializable
enum class Accent(val label: String, val light: Long, val dark: Long) {
    TERRACOTA(t("Terracota"), 0xFFA4502F, 0xFFD98A66),
    AZUL(t("Azul"), 0xFF2F6CA8, 0xFF6FAEE8),
    VERDE(t("Verde"), 0xFF3B7A4C, 0xFF7CC08E),
    ROSA(t("Rosa"), 0xFFAD4470, 0xFFE58AAE),
    ROXO(t("Roxo"), 0xFF6A4CA6, 0xFFAD96E6),
    AMBAR(t("Âmbar"), 0xFF94650F, 0xFFE2B252),
    VERMELHO(t("Vermelho"), 0xFFB23A3A, 0xFFE88080),
}

@Serializable
data class Settings(
    val titleLanguage: TitleLanguage = TitleLanguage.ROMAJI,
    /** Se o idioma ainda segue o da conta (até eu mudar nas Configurações). */
    val titleLanguageFromAccount: Boolean = true,
    val primaryScore: ScoreSystem = ScoreSystem.LETTERS,
    val secondaryScore: ScoreSystem? = ScoreSystem.POINT_100,
    val myServices: Set<String> = emptySet(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.TERRACOTA,
    val materialYou: Boolean = false,
    val oled: Boolean = false,
    val gridLayout: Boolean = false,
    /** Mostrar a nota na grade e permitir dar nota tocando nela. */
    val gridScore: Boolean = false,
    /** Ao marcar Completo pelo status rápido, abrir o seletor de nota. */
    val askScoreOnComplete: Boolean = true,
    /** Cor do ícone do app; null = segue a cor de destaque. */
    val iconAccent: Accent? = null,
    /** Ícone com fundo escuro. */
    val iconDark: Boolean = false,
    /** Mostrar obras 18+ nas buscas e no Descobrir. */
    val showAdult: Boolean = false,
    /** Desfocar capas de obras 18+ (vem ligado). */
    val blurAdult: Boolean = true,
    /** Gêneros e tags que nunca aparecem em buscas e no Descobrir. */
    val blockedGenres: Set<String> = emptySet(),
    val blockedTags: Set<String> = emptySet(),
    /** Fórum separado por categoria. */
    val forumByCategory: Boolean = false,
    val notifySocial: Boolean = false,
    val notifyEpisodes: Boolean = false,
    val notifyRelated: Boolean = false,
    val notifyDataChange: Boolean = false,
    val scheduleShowAll: Boolean = false,
    val homeCollapsed: Set<String> = emptySet(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val titleLang = stringPreferencesKey("title_language")
        val titleFromAccount = booleanPreferencesKey("title_from_account")
        val primary = stringPreferencesKey("primary_score")
        val secondary = stringPreferencesKey("secondary_score")
        val services = stringSetPreferencesKey("my_services")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val materialYou = booleanPreferencesKey("material_you")
        val oled = booleanPreferencesKey("oled")
        val grid = booleanPreferencesKey("grid_layout")
        val gridScore = booleanPreferencesKey("grid_score")
        val askScore = booleanPreferencesKey("ask_score_complete")
        val iconAccent = stringPreferencesKey("icon_accent")
        val iconDark = booleanPreferencesKey("icon_dark")
        val showAdult = booleanPreferencesKey("show_adult")
        val blurAdult = booleanPreferencesKey("blur_adult")
        val blockedGenres = stringSetPreferencesKey("blocked_genres")
        val blockedTags = stringSetPreferencesKey("blocked_tags")
        val forumByCategory = booleanPreferencesKey("forum_by_category")
        val nSocial = booleanPreferencesKey("notify_social")
        val nEpisodes = booleanPreferencesKey("notify_episodes")
        val nRelated = booleanPreferencesKey("notify_related")
        val nData = booleanPreferencesKey("notify_data_change")
        val scheduleAll = booleanPreferencesKey("schedule_show_all")
        val collapsed = stringSetPreferencesKey("home_collapsed")
        val viewerId = intPreferencesKey("viewer_id")
        val lastSync = longPreferencesKey("last_sync")
        val notifSeen = longPreferencesKey("notifications_seen_at")
        val notifPushed = longPreferencesKey("notifications_pushed_at")
        val knownServices = stringSetPreferencesKey("known_services")
        val profileStyle = stringPreferencesKey("profile_style")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            titleLanguage = p[K.titleLang]?.let { runCatching { TitleLanguage.valueOf(it) }.getOrNull() } ?: TitleLanguage.ROMAJI,
            titleLanguageFromAccount = p[K.titleFromAccount] ?: true,
            primaryScore = ScoreSystem.parse(p[K.primary]) ?: ScoreSystem.LETTERS,
            secondaryScore = if (p[K.secondary] == "NONE") null else ScoreSystem.parse(p[K.secondary]) ?: ScoreSystem.POINT_100,
            myServices = p[K.services] ?: emptySet(),
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = p[K.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.TERRACOTA,
            materialYou = p[K.materialYou] ?: false,
            oled = p[K.oled] ?: false,
            gridLayout = p[K.grid] ?: false,
            gridScore = p[K.gridScore] ?: false,
            askScoreOnComplete = p[K.askScore] ?: true,
            iconAccent = p[K.iconAccent]?.let { v -> Accent.entries.firstOrNull { it.name == v } },
            iconDark = p[K.iconDark] ?: false,
            showAdult = p[K.showAdult] ?: false,
            blurAdult = p[K.blurAdult] ?: true,
            blockedGenres = p[K.blockedGenres] ?: emptySet(),
            blockedTags = p[K.blockedTags] ?: emptySet(),
            forumByCategory = p[K.forumByCategory] ?: false,
            notifySocial = p[K.nSocial] ?: false,
            notifyEpisodes = p[K.nEpisodes] ?: false,
            notifyRelated = p[K.nRelated] ?: false,
            notifyDataChange = p[K.nData] ?: false,
            scheduleShowAll = p[K.scheduleAll] ?: false,
            homeCollapsed = p[K.collapsed] ?: emptySet(),
        )
    }

    suspend fun current(): Settings = settings.first()

    private val styleJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val profileStyle: Flow<ProfileStyle> = context.dataStore.data.map { p ->
        p[K.profileStyle]?.let { runCatching { styleJson.decodeFromString(ProfileStyle.serializer(), it) }.getOrNull() } ?: ProfileStyle()
    }

    suspend fun setProfileStyle(s: ProfileStyle) = context.dataStore.edit { it[K.profileStyle] = styleJson.encodeToString(ProfileStyle.serializer(), s) }

    val viewerId: Flow<Int?> = context.dataStore.data.map { it[K.viewerId] }
    val lastSync: Flow<Long> = context.dataStore.data.map { it[K.lastSync] ?: 0L }
    val notificationsSeenAt: Flow<Long> = context.dataStore.data.map { it[K.notifSeen] ?: 0L }
    val knownServices: Flow<Set<String>> = context.dataStore.data.map { it[K.knownServices] ?: emptySet() }

    suspend fun viewerIdNow(): Int? = viewerId.first()
    suspend fun setViewerId(id: Int) = context.dataStore.edit { it[K.viewerId] = id }
    suspend fun setLastSync(at: Long) = context.dataStore.edit { it[K.lastSync] = at }
    suspend fun setNotificationsSeenAt(at: Long) = context.dataStore.edit { it[K.notifSeen] = at }
    suspend fun notificationsPushedAt(): Long = context.dataStore.data.first()[K.notifPushed] ?: 0L
    suspend fun setNotificationsPushedAt(at: Long) = context.dataStore.edit { it[K.notifPushed] = at }
    suspend fun addKnownServices(sites: Collection<String>) = context.dataStore.edit {
        it[K.knownServices] = (it[K.knownServices] ?: emptySet()) + sites
    }

    suspend fun setTitleLanguage(lang: TitleLanguage, fromAccount: Boolean) = context.dataStore.edit {
        it[K.titleLang] = lang.name
        it[K.titleFromAccount] = fromAccount
    }
    suspend fun setPrimaryScore(s: ScoreSystem) = context.dataStore.edit { it[K.primary] = s.name }
    suspend fun setSecondaryScore(s: ScoreSystem?) = context.dataStore.edit { it[K.secondary] = s?.name ?: "NONE" }
    suspend fun setMyServices(s: Set<String>) = context.dataStore.edit { it[K.services] = s }
    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[K.theme] = t.name }
    suspend fun setAccent(a: Accent) = context.dataStore.edit { it[K.accent] = a.name }
    suspend fun setMaterialYou(v: Boolean) = context.dataStore.edit { it[K.materialYou] = v }
    suspend fun setOled(v: Boolean) = context.dataStore.edit { it[K.oled] = v }
    suspend fun setGridLayout(v: Boolean) = context.dataStore.edit { it[K.grid] = v }
    suspend fun setGridScore(v: Boolean) = context.dataStore.edit { it[K.gridScore] = v }
    suspend fun setAskScoreOnComplete(v: Boolean) = context.dataStore.edit { it[K.askScore] = v }
    suspend fun setIconAccent(v: Accent?) = context.dataStore.edit { if (v == null) it.remove(K.iconAccent) else it[K.iconAccent] = v.name }
    suspend fun setIconDark(v: Boolean) = context.dataStore.edit { it[K.iconDark] = v }
    suspend fun setShowAdult(v: Boolean) = context.dataStore.edit { it[K.showAdult] = v }
    suspend fun setBlurAdult(v: Boolean) = context.dataStore.edit { it[K.blurAdult] = v }
    suspend fun setBlockedGenres(v: Set<String>) = context.dataStore.edit { it[K.blockedGenres] = v }
    suspend fun setBlockedTags(v: Set<String>) = context.dataStore.edit { it[K.blockedTags] = v }
    suspend fun setForumByCategory(v: Boolean) = context.dataStore.edit { it[K.forumByCategory] = v }
    suspend fun setNotifySocial(v: Boolean) = context.dataStore.edit { it[K.nSocial] = v }
    suspend fun setNotifyEpisodes(v: Boolean) = context.dataStore.edit { it[K.nEpisodes] = v }
    suspend fun setNotifyRelated(v: Boolean) = context.dataStore.edit { it[K.nRelated] = v }
    suspend fun setNotifyDataChange(v: Boolean) = context.dataStore.edit { it[K.nData] = v }
    suspend fun setScheduleShowAll(v: Boolean) = context.dataStore.edit { it[K.scheduleAll] = v }
    suspend fun toggleCollapsed(key: String) = context.dataStore.edit {
        val cur = it[K.collapsed] ?: emptySet()
        it[K.collapsed] = if (key in cur) cur - key else cur + key
    }

    /** Troca de conta: zera o que é da conta anterior (sincronização, notificações vistas, idioma da conta). */
    suspend fun resetAccountState() = context.dataStore.edit {
        it.remove(K.lastSync)
        it.remove(K.notifSeen)
        it.remove(K.notifPushed)
        it[K.titleFromAccount] = true
    }

    /** Restaura tudo de um backup. */
    suspend fun restore(s: Settings) = context.dataStore.edit {
        it[K.titleLang] = s.titleLanguage.name
        it[K.titleFromAccount] = s.titleLanguageFromAccount
        it[K.primary] = s.primaryScore.name
        it[K.secondary] = s.secondaryScore?.name ?: "NONE"
        it[K.services] = s.myServices
        it[K.theme] = s.theme.name
        it[K.accent] = s.accent.name
        it[K.materialYou] = s.materialYou
        it[K.oled] = s.oled
        it[K.grid] = s.gridLayout
        it[K.gridScore] = s.gridScore
        it[K.askScore] = s.askScoreOnComplete
        if (s.iconAccent == null) it.remove(K.iconAccent) else it[K.iconAccent] = s.iconAccent.name
        it[K.iconDark] = s.iconDark
        it[K.showAdult] = s.showAdult
        it[K.blurAdult] = s.blurAdult
        it[K.blockedGenres] = s.blockedGenres
        it[K.blockedTags] = s.blockedTags
        it[K.forumByCategory] = s.forumByCategory
        it[K.nSocial] = s.notifySocial
        it[K.nEpisodes] = s.notifyEpisodes
        it[K.nRelated] = s.notifyRelated
        it[K.nData] = s.notifyDataChange
        it[K.scheduleAll] = s.scheduleShowAll
        it[K.collapsed] = s.homeCollapsed
    }
}
