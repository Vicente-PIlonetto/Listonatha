package dev.viko.listaanimes.data.repo

import dev.viko.listaanimes.data.db.ReviewEntity
import dev.viko.listaanimes.domain.EntryPatch
import dev.viko.listaanimes.domain.FuzzyDate
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.Media
import dev.viko.listaanimes.domain.MediaKind
import dev.viko.listaanimes.graphql.fragment.EntryFields
import dev.viko.listaanimes.graphql.fragment.MediaCore
import dev.viko.listaanimes.graphql.fragment.ReviewFields
import dev.viko.listaanimes.graphql.type.FuzzyDateInput
import dev.viko.listaanimes.graphql.type.MediaListStatus
import dev.viko.listaanimes.graphql.type.MediaType
import com.apollographql.apollo.api.Optional

fun MediaCore.toMedia(): Media = Media(
    id = id,
    kind = if (type == MediaType.MANGA) MediaKind.MANGA else MediaKind.ANIME,
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    synonyms = synonyms.orEmpty().filterNotNull(),
    format = format?.rawValue,
    status = status?.rawValue,
    episodes = episodes,
    chapters = chapters,
    volumes = volumes,
    duration = duration,
    coverLarge = coverImage?.large,
    coverMedium = coverImage?.medium,
    coverColor = coverImage?.color,
    banner = bannerImage,
    season = season?.rawValue,
    seasonYear = seasonYear,
    averageScore = weightedScore,
    popularity = popularity,
    genres = genres.orEmpty().filterNotNull(),
    country = countryOfOrigin,
    isFavourite = isFavourite,
    nextEpisode = nextAiringEpisode?.episode,
    nextAiringAt = nextAiringEpisode?.airingAt?.toLong(),
    startYear = startDate?.year,
    isAdult = isAdult == true,
)

fun EntryFields.toEntry(kind: MediaKind): ListEntry = ListEntry(
    mediaId = mediaId,
    entryId = id,
    kind = kind,
    status = ListStatus.parse(status?.rawValue) ?: ListStatus.PLANNING,
    scoreRaw = (score ?: 0.0).toInt(),
    progress = progress ?: 0,
    progressVolumes = progressVolumes ?: 0,
    repeat = repeat ?: 0,
    notes = notes,
    isPrivate = private ?: false,
    startedAt = FuzzyDate(startedAt?.year, startedAt?.month, startedAt?.day),
    completedAt = FuzzyDate(completedAt?.year, completedAt?.month, completedAt?.day),
    updatedAt = (updatedAt ?: 0).toLong(),
    createdAt = (createdAt ?: 0).toLong(),
)

fun ReviewFields.toEntity(viewerId: Int?): ReviewEntity = ReviewEntity(
    id = id,
    mediaId = mediaId,
    userId = userId,
    userName = user?.name,
    userAvatar = user?.avatar?.medium,
    summary = summary.orEmpty(),
    body = body.orEmpty(),
    score = score ?: 0,
    isPrivate = private ?: false,
    rating = rating ?: 0,
    ratingAmount = ratingAmount ?: 0,
    createdAt = createdAt.toLong(),
    updatedAt = updatedAt.toLong(),
    mine = viewerId != null && userId == viewerId,
)

fun FuzzyDate.toInput(): FuzzyDateInput = FuzzyDateInput(
    year = Optional.present(year),
    month = Optional.present(month),
    day = Optional.present(day),
)

fun ListStatus.toApi(): MediaListStatus = MediaListStatus.safeValueOf(name)

fun EntryPatch.toEntry(kind: MediaKind, entryId: Int?, now: Long) = ListEntry(
    mediaId = mediaId, entryId = entryId, kind = kind, status = status, scoreRaw = scoreRaw,
    progress = progress, progressVolumes = progressVolumes, repeat = repeat, notes = notes,
    isPrivate = isPrivate, startedAt = startedAt, completedAt = completedAt, updatedAt = now, createdAt = now,
)
