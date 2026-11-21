package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextUtilsTest {
    @Test fun stripsAniListDescription() {
        val html = "Maomao lived a <i>peaceful</i> life.<br>\n<br>\nUntil one day&#039;s &quot;trip&quot; &amp; more.<br><br>\n(Source: Crunchyroll)"
        assertEquals("Maomao lived a peaceful life.\n\nUntil one day's \"trip\" & more.\n\n(Source: Crunchyroll)", TextUtils.stripHtml(html))
    }

    @Test fun emptyAndNull() {
        assertEquals("", TextUtils.stripHtml(null))
        assertEquals("", TextUtils.stripHtml("   "))
    }

    @Test fun collapsesManyBreaks() {
        assertEquals("a\n\nb", TextUtils.stripHtml("a<br><br><br><br>b"))
    }
}
