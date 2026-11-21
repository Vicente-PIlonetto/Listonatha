package dev.viko.listaanimes.domain

import dev.viko.listaanimes.i18n.t
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class MediaKind { ANIME, MANGA }

@Serializable
enum class ListStatus {
    CURRENT, PLANNING, COMPLETED, PAUSED, DROPPED, REPEATING;

    fun label(kind: MediaKind): String = when (this) {
        CURRENT -> if (kind == MediaKind.ANIME) t("Vendo") else t("Lendo")
        PLANNING -> if (kind == MediaKind.ANIME) t("Quero ver") else t("Quero ler")
        COMPLETED -> t("Completo")
        PAUSED -> t("Pausado")
        DROPPED -> t("Abandonado")
        REPEATING -> if (kind == MediaKind.ANIME) t("Revendo") else t("Relendo")
    }

    companion object {
        /** Ordem de exibição na Lista. */
        val displayOrder = listOf(CURRENT, REPEATING, PAUSED, PLANNING, COMPLETED, DROPPED)
        fun parse(name: String?): ListStatus? = entries.firstOrNull { it.name == name }
    }
}

@Serializable
enum class TitleLanguage(val label: String) {
    ROMAJI(t("Romaji")), ENGLISH(t("Inglês")), NATIVE(t("Nativo"));

    companion object {
        fun fromAniList(raw: String?): TitleLanguage = when {
            raw == null -> ROMAJI
            raw.startsWith("ENGLISH") -> ENGLISH
            raw.startsWith("NATIVE") -> NATIVE
            else -> ROMAJI
        }
    }
}

@Serializable
data class FuzzyDate(val year: Int? = null, val month: Int? = null, val day: Int? = null) {
    val isEmpty: Boolean get() = year == null && month == null && day == null

    fun toLocalDate(): LocalDate? {
        val y = year ?: return null
        return runCatching { LocalDate.of(y, month ?: 1, day ?: 1) }.getOrNull()
    }

    fun format(): String = when {
        year == null -> ""
        month == null -> "$year"
        day == null -> "%02d/%d".format(month, year)
        else -> "%02d/%02d/%d".format(day, month, year)
    }

    companion object {
        val EMPTY = FuzzyDate()
        fun of(date: LocalDate) = FuzzyDate(date.year, date.monthValue, date.dayOfMonth)
    }
}

/** Obra (anime ou mangá) em cache. */
@Serializable
@Entity(tableName = "media")
data class Media(
    @PrimaryKey val id: Int,
    val kind: MediaKind,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val synonyms: List<String> = emptyList(),
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val duration: Int? = null,
    val coverLarge: String? = null,
    val coverMedium: String? = null,
    val coverColor: String? = null,
    val banner: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val country: String? = null,
    val isFavourite: Boolean = false,
    val nextEpisode: Int? = null,
    /** Epoch em segundos. */
    val nextAiringAt: Long? = null,
    val startYear: Int? = null,
    val updatedLocally: Long = 0,
    /** Marcada como adulta (18+) no AniList. */
    @androidx.room.ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
) {
    fun title(lang: TitleLanguage): String = when (lang) {
        TitleLanguage.ROMAJI -> titleRomaji ?: titleEnglish ?: titleNative
        TitleLanguage.ENGLISH -> titleEnglish ?: titleRomaji ?: titleNative
        TitleLanguage.NATIVE -> titleNative ?: titleRomaji ?: titleEnglish
    } ?: "#$id"

    /** Total de episódios (anime) ou capítulos (mangá), se conhecido. */
    val total: Int? get() = if (kind == MediaKind.ANIME) episodes else chapters

    val isFinished: Boolean get() = status == "FINISHED"
    val isReleasing: Boolean get() = status == "RELEASING"
}

/** Item da minha lista. A chave é o id da obra (uma entrada por obra). */
@Serializable
@Entity(tableName = "list_entry")
data class ListEntry(
    @PrimaryKey val mediaId: Int,
    /** Id da entrada no AniList; null enquanto foi criada só localmente. */
    val entryId: Int? = null,
    val kind: MediaKind,
    val status: ListStatus,
    /** Sempre 0–100 (scoreRaw). 0 = sem nota. */
    val scoreRaw: Int = 0,
    val progress: Int = 0,
    val progressVolumes: Int = 0,
    val repeat: Int = 0,
    val notes: String? = null,
    val isPrivate: Boolean = false,
    @Embedded(prefix = "started_") val startedAt: FuzzyDate = FuzzyDate.EMPTY,
    @Embedded(prefix = "completed_") val completedAt: FuzzyDate = FuzzyDate.EMPTY,
    /** Epoch em segundos da última alteração (remota ou local). */
    val updatedAt: Long = 0,
    val createdAt: Long = 0,
)

/** Os campos editáveis de uma entrada, enviados ao AniList (snapshot completo). */
@Serializable
data class EntryPatch(
    val mediaId: Int,
    val status: ListStatus,
    val scoreRaw: Int,
    val progress: Int,
    val progressVolumes: Int,
    val repeat: Int,
    val notes: String?,
    val isPrivate: Boolean,
    val startedAt: FuzzyDate,
    val completedAt: FuzzyDate,
) {
    companion object {
        fun of(e: ListEntry) = EntryPatch(
            e.mediaId, e.status, e.scoreRaw, e.progress, e.progressVolumes, e.repeat,
            e.notes, e.isPrivate, e.startedAt, e.completedAt,
        )
    }
}

object Labels {
    fun format(f: String?): String = when (f) {
        "TV" -> "TV"
        "TV_SHORT" -> t("TV curta")
        "MOVIE" -> t("Filme")
        "SPECIAL" -> t("Especial")
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> t("Música")
        "MANGA" -> t("Mangá")
        "NOVEL" -> t("Light novel")
        "ONE_SHOT" -> t("One shot")
        null -> ""
        else -> f
    }

    fun releaseStatus(s: String?): String = when (s) {
        "FINISHED" -> t("Encerrado")
        "RELEASING" -> t("Em lançamento")
        "NOT_YET_RELEASED" -> t("Não lançado")
        "CANCELLED" -> t("Cancelado")
        "HIATUS" -> t("Em hiato")
        else -> ""
    }

    fun season(s: String?): String = when (s) {
        "WINTER" -> t("Inverno")
        "SPRING" -> t("Primavera")
        "SUMMER" -> t("Verão")
        "FALL" -> t("Outono")
        else -> ""
    }

    fun relation(r: String?): String = when (r) {
        "ADAPTATION" -> t("Adaptação")
        "PREQUEL" -> t("Prequel")
        "SEQUEL" -> t("Sequência")
        "PARENT" -> t("Principal")
        "SIDE_STORY" -> t("História paralela")
        "CHARACTER" -> t("Personagem")
        "SUMMARY" -> t("Resumo")
        "ALTERNATIVE" -> t("Alternativa")
        "SPIN_OFF" -> t("Spin-off")
        "SOURCE" -> t("Original")
        "COMPILATION" -> t("Compilação")
        "CONTAINS" -> t("Contém")
        else -> t("Outro")
    }

    fun country(c: String?): String = when (c) {
        "JP" -> t("Japão")
        "KR" -> t("Coreia")
        "CN" -> t("China")
        "TW" -> t("Taiwan")
        else -> c ?: ""
    }

    fun source(s: String?): String = when (s) {
        "ORIGINAL" -> t("Original")
        "MANGA" -> t("Mangá")
        "LIGHT_NOVEL" -> t("Light novel")
        "VISUAL_NOVEL" -> t("Visual novel")
        "VIDEO_GAME" -> t("Jogo")
        "NOVEL" -> t("Novel")
        "WEB_NOVEL" -> t("Web novel")
        "DOUJINSHI" -> t("Doujinshi")
        "ANIME" -> t("Anime")
        "MULTIMEDIA_PROJECT" -> t("Projeto multimídia")
        "PICTURE_BOOK" -> t("Livro ilustrado")
        "COMIC" -> t("HQ")
        "GAME" -> t("Jogo")
        "LIVE_ACTION" -> t("Live action")
        else -> t("Outro")
    }

    val animeFormats = listOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL")
    val mangaFormats = listOf("MANGA", "ONE_SHOT", "NOVEL")
}
