package dev.viko.listaanimes.domain

import dev.viko.listaanimes.i18n.t

/** Textos das atividades de lista em português (o AniList manda em inglês). */
object SocialText {
    /**
     * [status] como vem do AniList ("watched episode", "read chapter", "completed", "plans to watch"...),
     * [progress] como "5" ou "1 - 3". Devolve o começo da frase; o título da obra vem depois.
     */
    fun listActivity(status: String?, progress: String?, kind: MediaKind): String {
        val s = status?.trim()?.lowercase().orEmpty()
        val p = progress?.trim()?.takeIf { it.isNotEmpty() }
        val range = p?.contains("-") == true
        fun ep(singular: String, plural: String) = if (range) t("{0} {1} de", plural, p) else t("{0} {1} de", singular, p)
        return when (s) {
            "watched episode" -> ep(t("Assistiu ao episódio"), t("Assistiu aos episódios"))
            "rewatched episode" -> ep(t("Reviu o episódio"), t("Reviu os episódios"))
            "read chapter" -> ep(t("Leu o capítulo"), t("Leu os capítulos"))
            "reread chapter" -> ep(t("Releu o capítulo"), t("Releu os capítulos"))
            "completed" -> t("Completou")
            "plans to watch" -> t("Quer ver")
            "plans to read" -> t("Quer ler")
            "dropped" -> t("Abandonou")
            "paused watching" -> t("Pausou")
            "paused reading" -> t("Pausou")
            "rewatched" -> t("Reviu")
            "reread" -> t("Releu")
            "watching" -> t("Está vendo")
            "reading" -> t("Está lendo")
            else -> if (p != null) t("{0} {1} de", status.orEmpty(), p) else status.orEmpty().replaceFirstChar { it.uppercase() }
        }.let { if (kind == MediaKind.MANGA && s == "dropped") t("Abandonou") else it }
    }

    fun notification(type: String, user: String, thread: String? = null): String = when (type) {
        "FollowingNotification" -> t("{0} começou a seguir você", user)
        "ActivityMessageNotification" -> t("{0} mandou uma mensagem", user)
        "ActivityMentionNotification" -> t("{0} mencionou você", user)
        "ActivityReplyNotification" -> t("{0} respondeu sua atividade", user)
        "ActivityReplySubscribedNotification" -> t("{0} respondeu uma atividade que você segue", user)
        "ActivityLikeNotification" -> t("{0} curtiu sua atividade", user)
        "ActivityReplyLikeNotification" -> t("{0} curtiu sua resposta", user)
        "ThreadCommentMentionNotification" -> t("{0} mencionou você", user) + (thread?.let { t(" em {0}", it) } ?: "")
        "ThreadCommentReplyNotification" -> t("{0} respondeu seu comentário", user) + (thread?.let { t(" em {0}", it) } ?: "")
        "ThreadCommentSubscribedNotification" -> t("{0} comentou", user) + (thread?.let { t(" em {0}", it) } ?: t(" num tópico que você segue"))
        "ThreadCommentLikeNotification" -> t("{0} curtiu seu comentário", user) + (thread?.let { t(" em {0}", it) } ?: "")
        "ThreadLikeNotification" -> t("{0} curtiu seu tópico", user) + (thread?.let { " $it" } ?: "")
        else -> user
    }
}
