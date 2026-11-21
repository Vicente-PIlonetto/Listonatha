package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.i18n.t
import com.apollographql.apollo.api.Optional
import dev.viko.listaanimes.data.api.AniListApi
import dev.viko.listaanimes.data.db.AppDatabase
import dev.viko.listaanimes.data.db.CacheBlob
import dev.viko.listaanimes.data.settings.SettingsRepository
import dev.viko.listaanimes.domain.FuzzyDate
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.domain.TextUtils
import dev.viko.listaanimes.domain.score.Scores
import dev.viko.listaanimes.graphql.BrowseQuery
import dev.viko.listaanimes.graphql.DiscoverAnimeQuery
import dev.viko.listaanimes.graphql.DiscoverMangaQuery
import dev.viko.listaanimes.graphql.GenresTagsQuery
import dev.viko.listaanimes.graphql.MediaDetailQuery
import dev.viko.listaanimes.graphql.RecommendationsQuery
import dev.viko.listaanimes.graphql.type.ExternalLinkType
import dev.viko.listaanimes.graphql.type.MediaFormat
import dev.viko.listaanimes.graphql.type.MediaSeason
import dev.viko.listaanimes.graphql.type.MediaSort
import dev.viko.listaanimes.graphql.type.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

data class SeasonRef(val season: String, val year: Int) {
    val label: String get() = "${dev.viko.listaanimes.domain.Labels.season(season)} $year"

    fun next(): SeasonRef = when (season) {
        "WINTER" -> SeasonRef("SPRING", year)
        "SPRING" -> SeasonRef("SUMMER", year)
        "SUMMER" -> SeasonRef("FALL", year)
        else -> SeasonRef("WINTER", year + 1)
    }

    fun previous(): SeasonRef = when (season) {
        "WINTER" -> SeasonRef("FALL", year - 1)
        "SPRING" -> SeasonRef("WINTER", year)
        "SUMMER" -> SeasonRef("SPRING", year)
        else -> SeasonRef("SUMMER", year)
    }

    companion object {
        /** Temporadas do AniList (hemisfério norte): dez–fev inverno etc. */
        fun current(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): SeasonRef = when (today.monthValue) {
            12 -> SeasonRef("WINTER", today.year + 1)
            1, 2 -> SeasonRef("WINTER", today.year)
            3, 4, 5 -> SeasonRef("SPRING", today.year)
            6, 7, 8 -> SeasonRef("SUMMER", today.year)
            else -> SeasonRef("FALL", today.year)
        }
    }
}

/** Parâmetros de uma busca/listagem no Descobrir. */
data class BrowseParams(
    val kind: MediaKind,
    val sort: List<String> = listOf("POPULARITY_DESC"),
    val search: String? = null,
    val season: SeasonRef? = null,
    val formats: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val country: String? = null,
    val hideOnList: Boolean = false,
)

data class BrowsePage(val items: List<Media>, val hasNext: Boolean)

class MediaRepository(
    private val db: AppDatabase,
    private val api: AniListApi,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private fun now() = System.currentTimeMillis() / 1000

    private suspend fun <T> putCache(key: String, ser: KSerializer<T>, value: T) =
        db.cache().put(CacheBlob(key, json.encodeToString(ser, value), now()))

    private suspend fun <T> getCache(key: String, ser: KSerializer<T>): T? =
        db.cache().get(key)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() }

    fun <T> observeCache(key: String, ser: KSerializer<T>): Flow<T?> =
        db.cache().observe(key).map { b -> b?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } }

    fun observeMedia(id: Int): Flow<Media?> = db.media().observe(id)

    /** Filtros de conteúdo das configurações: 18+ e gêneros/tags bloqueados. */
    private data class ContentFilter(val adult: Optional<Boolean?>, val genreNotIn: Optional<List<String?>?>, val tagNotIn: Optional<List<String?>?>)

    private suspend fun contentFilter(): ContentFilter {
        val s = settings.current()
        return ContentFilter(
            // Sem filtro de 18+ quando a pessoa permite; senão só obras não adultas.
            adult = if (s.showAdult) Optional.Absent else Optional.present(false),
            genreNotIn = if (s.blockedGenres.isEmpty()) Optional.Absent else Optional.present(s.blockedGenres.toList()),
            tagNotIn = if (s.blockedTags.isEmpty()) Optional.Absent else Optional.present(s.blockedTags.toList()),
        )
    }

    /** Aplica os bloqueios a listas que não passam pelo filtro do servidor (recomendações, relações). */
    suspend fun allowed(items: List<Media>): List<Media> {
        val s = settings.current()
        return items.filter { m -> (s.showAdult || !m.isAdult) && m.genres.none { it in s.blockedGenres } }
    }

    // ---------- Detalhe ----------

    fun observeDetail(id: Int): Flow<MediaDetail?> = observeCache("detail:$id", MediaDetail.serializer())

    suspend fun refreshDetail(id: Int, viewerId: Int?): MediaDetail {
        val m = api.run(api.apollo.query(MediaDetailQuery(id))).Media ?: error(t("Obra não encontrada"))
        val media = m.mediaCore.toMedia()
        val detail = MediaDetail(
            media = media,
            description = TextUtils.stripHtml(m.description),
            meanScore = m.meanScore,
            source = m.source?.rawValue,
            siteUrl = m.siteUrl,
            endDate = m.endDate?.let { FuzzyDate(it.year, it.month, it.day) },
            studios = m.studios?.edges.orEmpty().filterNotNull().filter { it.isMain }.mapNotNull { it.node?.name },
            staff = m.staff?.edges.orEmpty().filterNotNull().mapNotNull { e ->
                val n = e.node?.name?.full ?: return@mapNotNull null
                StaffCredit(n, e.role.orEmpty())
            },
            tags = m.tags.orEmpty().filterNotNull().map {
                TagRef(it.name, it.rank ?: 0, it.isMediaSpoiler == true || it.isGeneralSpoiler == true)
            },
            links = m.externalLinks.orEmpty().filterNotNull()
                .filter { it.type == ExternalLinkType.STREAMING && it.isDisabled != true && it.url != null }
                .map { StreamLink(it.site, it.url!!, it.language, it.color, it.icon) },
            relations = m.relations?.edges.orEmpty().filterNotNull().mapNotNull { e ->
                val node = e.node?.mediaCore?.toMedia() ?: return@mapNotNull null
                RelationRef(e.relationType?.rawValue ?: "OTHER", node)
            },
            characters = m.characters?.edges.orEmpty().filterNotNull().mapNotNull { e ->
                val n = e.node ?: return@mapNotNull null
                CharacterRef(n.name?.full ?: return@mapNotNull null, n.image?.medium, e.role?.rawValue, n.id)
            },
            recommendations = m.recommendations?.nodes.orEmpty().mapNotNull { it?.mediaRecommendation?.mediaCore?.toMedia() },
        )
        db.media().upsert(media)
        // A entrada da lista também vem no detalhe: atualiza se não houver mudança pendente.
        m.mediaListEntry?.entryFields?.let { ef ->
            if (ef.mediaId !in db.pending().pendingMediaIds()) {
                val entry = ef.toEntry(media.kind)
                db.entries().upsert(entry)
                db.specials().get(entry.mediaId)?.let { sp ->
                    if (dev.viko.listaanimes.domain.score.SpecialScores.reconcile(sp.position, entry.scoreRaw) == null) db.specials().delete(entry.mediaId)
                }
            }
        }
        val reviews = m.reviews?.nodes.orEmpty().mapNotNull { it?.reviewFields?.toEntity(viewerId) }
        db.reviews().upsert(reviews)
        settings.addKnownServices(detail.links.map { it.site })
        putCache("detail:$id", MediaDetail.serializer(), detail)
        return detail
    }

    // ---------- Descobrir ----------

    private val sectionsSer = ListSerializer(DiscoverSection.serializer())

    fun observeDiscover(key: String): Flow<List<DiscoverSection>?> = observeCache("discover:$key", sectionsSer)

    suspend fun refreshDiscoverAnime(): List<DiscoverSection> {
        val cur = SeasonRef.current()
        val next = cur.next()
        val f = contentFilter()
        val d = api.run(api.apollo.query(DiscoverAnimeQuery(
            MediaSeason.safeValueOf(cur.season), cur.year, MediaSeason.safeValueOf(next.season), next.year,
            adult = f.adult, genreNotIn = f.genreNotIn, tagNotIn = f.tagNotIn,
        )))
        val sections = listOf(
            DiscoverSection("trending", t("Em alta"), d.trending?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("season", t("Esta temporada · {0}", cur.label), d.season?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("nextSeason", t("Próxima temporada · {0}", next.label), d.nextSeason?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("recent", t("Recém-adicionados"), d.recent?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("popular", t("Mais populares"), d.popular?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("top", t("Top 100"), d.top?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
        )
        putCache("discover:anime", sectionsSer, sections)
        return sections
    }

    suspend fun refreshDiscoverManga(country: String?, formats: List<String>): List<DiscoverSection> {
        val d = api.run(api.apollo.query(DiscoverMangaQuery(
            country = Optional.presentIfNotNull(country),
            formats = if (formats.isEmpty()) Optional.Absent else Optional.present(formats.map { MediaFormat.safeValueOf(it) }),
            adult = contentFilter().adult, genreNotIn = contentFilter().genreNotIn, tagNotIn = contentFilter().tagNotIn,
        )))
        val sections = listOf(
            DiscoverSection("trending", t("Em alta"), d.trending?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("recent", t("Recém-adicionados"), d.recent?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("popular", t("Mais populares"), d.popular?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
            DiscoverSection("top", t("Top 100"), d.top?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }),
        )
        putCache("discover:manga:${country ?: "all"}:${formats.sorted().joinToString(",")}", sectionsSer, sections)
        return sections
    }

    fun mangaDiscoverKey(country: String?, formats: List<String>) = "manga:${country ?: "all"}:${formats.sorted().joinToString(",")}"

    suspend fun browse(p: BrowseParams, page: Int): BrowsePage {
        val f = contentFilter()
        val d = api.run(api.apollo.query(BrowseQuery(
            adult = f.adult, genreNotIn = f.genreNotIn, tagNotIn = f.tagNotIn,
            page = page,
            type = if (p.kind == MediaKind.ANIME) MediaType.ANIME else MediaType.MANGA,
            sort = Optional.present(p.sort.map { MediaSort.safeValueOf(it) }),
            search = Optional.presentIfNotNull(p.search?.takeIf { it.isNotBlank() }),
            season = Optional.presentIfNotNull(p.season?.let { MediaSeason.safeValueOf(it.season) }),
            seasonYear = Optional.presentIfNotNull(p.season?.year),
            formats = if (p.formats.isEmpty()) Optional.Absent else Optional.present(p.formats.map { MediaFormat.safeValueOf(it) }),
            genres = if (p.genres.isEmpty()) Optional.Absent else Optional.present(p.genres),
            tags = if (p.tags.isEmpty()) Optional.Absent else Optional.present(p.tags),
            country = Optional.presentIfNotNull(p.country),
            onList = if (p.hideOnList) Optional.present(false) else Optional.Absent,
        ))).Page
        return BrowsePage(d?.media.orEmpty().mapNotNull { it?.mediaCore?.toMedia() }, d?.pageInfo?.hasNextPage == true)
    }

    // ---------- Gêneros e tags ----------

    suspend fun character(id: Int): CharacterInfo {
        val c = api.run(api.apollo.query(dev.viko.listaanimes.graphql.CharacterDetailQuery(id))).Character ?: error(t("Personagem não encontrado"))
        val b = c.dateOfBirth
        val birthday = if (b?.day != null && b.month != null) t("{0} de {1}", b.day, java.time.Month.of(b.month.coerceIn(1, 12)).getDisplayName(java.time.format.TextStyle.SHORT, dev.viko.listaanimes.i18n.I18n.locale)) + (b.year?.let { t(" de {0}", it) } ?: "") else null
        return CharacterInfo(
            id = c.id, name = c.name?.full ?: "", native = c.name?.native, alternative = c.name?.alternative.orEmpty().filterNotNull(),
            image = c.image?.large, description = c.description.orEmpty(),
            gender = c.gender, age = c.age, birthday = birthday, bloodType = c.bloodType, favourites = c.favourites ?: 0, siteUrl = c.siteUrl,
            appearances = c.media?.edges.orEmpty().mapNotNull { e ->
                val m = e?.node?.mediaCore?.toMedia() ?: return@mapNotNull null
                val va = e.voiceActors.orEmpty().firstOrNull()
                CharacterAppearance(m, e.characterRole?.rawValue, va?.name?.full, va?.image?.medium)
            }.distinctBy { it.media.id },
            isFavourite = c.isFavourite,
        )
    }

    /** Uma obra aleatória entre as populares com esses filtros, de preferência fora de [exclude]. */
    suspend fun random(kind: MediaKind, genres: List<String>, tags: List<String>, exclude: Set<Int>): Media? {
        val params = BrowseParams(kind, listOf("POPULARITY_DESC"), genres = genres, tags = tags)
        // Primeiras páginas têm as obras mais conhecidas; sorteia uma página e depois uma obra.
        val first = browse(params, 1)
        val pages = if (first.hasNext) (1..8).random() else 1
        val items = if (pages == 1) first.items else browse(params, pages).items.ifEmpty { first.items }
        return items.filter { it.id !in exclude }.randomOrNull() ?: items.randomOrNull()
    }

    /** Favorita/desfavorita um personagem e atualiza a lista do meu perfil. */
    suspend fun toggleCharacterFavourite(info: CharacterInfo): Boolean {
        api.run(api.apollo.mutation(dev.viko.listaanimes.graphql.ToggleFavouriteCharacterMutation(info.id)))
        val fav = !info.isFavourite
        getCache("profile", Profile.serializer())?.let { pr ->
            val list = if (fav) (pr.favCharacters.filter { it.id != info.id } + FavCharacter(info.id, info.name, info.image))
            else pr.favCharacters.filter { it.id != info.id }
            putCache("profile", Profile.serializer(), pr.copy(favCharacters = list))
        }
        return fav
    }

    suspend fun genresTags(): GenresTags {
        val adult = settings.current().showAdult
        fun GenresTags.filtered() = if (adult) this else GenresTags(genres.filter { it != "Hentai" }, tags.filter { !it.isAdult })
        val cached = db.cache().get("genres_tags2")
        if (cached != null && now() - cached.fetchedAt < 7 * 24 * 3600) {
            runCatching { return json.decodeFromString(GenresTags.serializer(), cached.json).filtered() }
        }
        return try {
            val d = api.run(api.apollo.query(GenresTagsQuery()))
            val gt = GenresTags(
                d.GenreCollection.orEmpty().filterNotNull(),
                d.MediaTagCollection.orEmpty().filterNotNull().map { TagInfo(it.name, it.category, it.isAdult == true) },
            )
            putCache("genres_tags2", GenresTags.serializer(), gt)
            gt.filtered()
        } catch (e: Exception) {
            cached?.let { json.decodeFromString(GenresTags.serializer(), it.json).filtered() } ?: throw e
        }
    }

    // ---------- Recomendações ----------

    private val recSer = ListSerializer(RecommendationGroup.serializer())

    fun observeRecommendations(kind: MediaKind): Flow<List<RecommendationGroup>?> = observeCache("recs:${kind.name}", recSer)

    /** "Porque você deu [nota] para [obra]": usa minhas notas mais altas, numa consulta só. */
    suspend fun refreshRecommendations(kind: MediaKind, entries: List<Pair<ListEntry, Media>>, specials: Map<Int, Int>, onList: Set<Int>) {
        val top = entries.filter { it.first.scoreRaw > 0 }
            .sortedByDescending { Scores.sortKey(it.first.scoreRaw, specials[it.first.mediaId]) }
            .take(6)
        if (top.isEmpty()) { putCache("recs:${kind.name}", recSer, emptyList()); return }
        val d = api.run(api.apollo.query(RecommendationsQuery(Optional.present(top.map { it.second.id }))))
        val byId = d.Page?.media.orEmpty().filterNotNull().associateBy { it.id }
        val groups = top.mapNotNull { (e, m) ->
            val recs = byId[m.id]?.recommendations?.nodes.orEmpty()
                .mapNotNull { it?.mediaRecommendation?.mediaCore?.toMedia() }
                .filter { it.id !in onList }
            if (recs.isEmpty()) null else RecommendationGroup(m, e.scoreRaw, specials[m.id], recs)
        }
        putCache("recs:${kind.name}", recSer, groups)
    }
}
