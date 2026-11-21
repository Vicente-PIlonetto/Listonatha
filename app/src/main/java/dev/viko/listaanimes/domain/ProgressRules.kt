package dev.viko.listaanimes.domain

import dev.viko.listaanimes.i18n.t
import java.time.LocalDate

object ProgressRules {

    /**
     * Aplica um novo progresso (episódio/capítulo) com as mudanças automáticas:
     * - sair do zero em "Quero ver/ler" vira "Vendo/Lendo" e preenche o início;
     * - chegar ao último de uma obra encerrada vira "Completo" e preenche o fim
     *   (se estava revendo, soma uma repetição).
     */
    fun applyProgress(entry: ListEntry, media: Media, requested: Int, today: LocalDate, nowEpoch: Long): ListEntry {
        val total = media.total?.takeIf { it > 0 }
        val newProgress = requested.coerceAtLeast(0).let { if (total != null) it.coerceAtMost(total) else it }
        var status = entry.status
        var started = entry.startedAt
        var completed = entry.completedAt
        var repeat = entry.repeat

        if (entry.progress == 0 && newProgress >= 1 && status == ListStatus.PLANNING) {
            status = ListStatus.CURRENT
            if (started.isEmpty) started = FuzzyDate.of(today)
        }
        if (media.isFinished && total != null && newProgress == total && entry.progress < total &&
            status != ListStatus.COMPLETED
        ) {
            if (status == ListStatus.REPEATING) repeat += 1
            status = ListStatus.COMPLETED
            completed = FuzzyDate.of(today)
            if (started.isEmpty) started = FuzzyDate.of(today)
        }
        return entry.copy(
            progress = newProgress,
            status = status,
            startedAt = started,
            completedAt = completed,
            repeat = repeat,
            updatedAt = nowEpoch,
        )
    }

    /**
     * Troca de status com os preenchimentos automáticos:
     * Vendo/Lendo preenche o início; Completo preenche o fim (e o início, se vazio) e leva o progresso ao total.
     */
    fun applyStatus(entry: ListEntry, media: Media, status: ListStatus, today: LocalDate, nowEpoch: Long): ListEntry {
        var e = entry.copy(status = status, updatedAt = nowEpoch)
        val t = FuzzyDate.of(today)
        when (status) {
            ListStatus.CURRENT -> if (e.startedAt.isEmpty) e = e.copy(startedAt = t)
            ListStatus.COMPLETED -> {
                if (e.startedAt.isEmpty) e = e.copy(startedAt = t)
                if (e.completedAt.isEmpty || entry.status != ListStatus.COMPLETED) e = e.copy(completedAt = t)
                media.total?.takeIf { it > 0 }?.let { e = e.copy(progress = it) }
                if (entry.status == ListStatus.REPEATING) e = e.copy(repeat = e.repeat + 1)
            }
            ListStatus.REPEATING -> if (entry.status == ListStatus.COMPLETED) e = e.copy(progress = 0)
            else -> {}
        }
        return e
    }

    fun increment(entry: ListEntry, media: Media, today: LocalDate, nowEpoch: Long): ListEntry =
        applyProgress(entry, media, entry.progress + 1, today, nowEpoch)

    fun canIncrement(entry: ListEntry, media: Media): Boolean {
        val total = media.total?.takeIf { it > 0 } ?: return true
        return entry.progress < total
    }

    /** Episódios já lançados segundo o nextAiringEpisode, considerando que o horário pode já ter passado. */
    fun airedEpisodes(media: Media, nowEpoch: Long): Int? {
        val next = media.nextEpisode ?: return null
        val at = media.nextAiringAt ?: return next - 1
        return if (at <= nowEpoch) next else next - 1
    }

    /** Quantos episódios lançados eu ainda não vi (só anime em lançamento que estou vendo/revendo). */
    fun episodesBehind(entry: ListEntry, media: Media, nowEpoch: Long): Int {
        if (media.kind != MediaKind.ANIME) return 0
        if (entry.status != ListStatus.CURRENT && entry.status != ListStatus.REPEATING) return 0
        val aired = airedEpisodes(media, nowEpoch) ?: return 0
        return (aired - entry.progress).coerceAtLeast(0)
    }

    /** "6/12", "Ep. 6" (anime sem total) ou "Cap. 143" (mangá sem total). */
    fun progressText(entry: ListEntry, media: Media): String {
        val total = media.total?.takeIf { it > 0 }
        return when {
            total != null -> "${entry.progress}/$total"
            media.kind == MediaKind.ANIME -> t("Ep. {0}", entry.progress)
            else -> t("Cap. {0}", entry.progress)
        }
    }

    fun behindText(behind: Int): String? = when {
        behind <= 0 -> null
        behind == 1 -> t("1 atrasado")
        else -> t("{0} atrasados", behind)
    }
}
