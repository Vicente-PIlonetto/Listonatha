package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialTextTest {
    @Test fun listActivities() {
        assertEquals("Assistiu ao episódio 5 de", SocialText.listActivity("watched episode", "5", MediaKind.ANIME))
        assertEquals("Assistiu aos episódios 1 - 3 de", SocialText.listActivity("watched episode", "1 - 3", MediaKind.ANIME))
        assertEquals("Leu o capítulo 143 de", SocialText.listActivity("read chapter", "143", MediaKind.MANGA))
        assertEquals("Releu os capítulos 10 - 12 de", SocialText.listActivity("reread chapter", "10 - 12", MediaKind.MANGA))
        assertEquals("Completou", SocialText.listActivity("completed", null, MediaKind.ANIME))
        assertEquals("Quer ver", SocialText.listActivity("plans to watch", null, MediaKind.ANIME))
        assertEquals("Quer ler", SocialText.listActivity("plans to read", null, MediaKind.MANGA))
        assertEquals("Pausou", SocialText.listActivity("paused watching", null, MediaKind.ANIME))
        assertEquals("Abandonou", SocialText.listActivity("dropped", null, MediaKind.MANGA))
    }

    @Test fun unknownStatusFallsBackToOriginal() {
        assertEquals("Something new", SocialText.listActivity("something new", null, MediaKind.ANIME))
    }

    @Test fun notifications() {
        assertEquals("ana começou a seguir você", SocialText.notification("FollowingNotification", "ana"))
        assertEquals("ana curtiu sua atividade", SocialText.notification("ActivityLikeNotification", "ana"))
        assertEquals("ana respondeu seu comentário em Tópico X", SocialText.notification("ThreadCommentReplyNotification", "ana", "Tópico X"))
    }
}
