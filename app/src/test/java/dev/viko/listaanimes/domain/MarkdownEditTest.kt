package dev.viko.listaanimes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditTest {
    @Test fun wrapSelection() {
        val r = MarkdownEdit.wrap("um texto aqui", 3, 8, "__", "__", "negrito")
        assertEquals("um __texto__ aqui", r.text)
        assertEquals(5, r.selStart); assertEquals(10, r.selEnd)
    }

    @Test fun wrapWithoutSelectionInsertsPlaceholderSelected() {
        val r = MarkdownEdit.wrap("abc", 3, 3, "~!", "!~", "spoiler")
        assertEquals("abc~!spoiler!~", r.text)
        assertEquals("spoiler", r.text.substring(r.selStart, r.selEnd))
    }

    @Test fun wrapTwiceRemoves() {
        val once = MarkdownEdit.wrap("x texto y", 2, 7, "_", "_", "itálico")
        val twice = MarkdownEdit.wrap(once.text, once.selStart, once.selEnd, "_", "_", "itálico")
        assertEquals("x texto y", twice.text)
        assertEquals("texto", twice.text.substring(twice.selStart, twice.selEnd))
    }

    @Test fun linePrefixOnSeveralLinesAndToggle() {
        val text = "um\ndois\ntrês"
        val r = MarkdownEdit.linePrefix(text, 1, 6, "> ")
        assertEquals("> um\n> dois\ntrês", r.text)
        val back = MarkdownEdit.linePrefix(r.text, r.selStart, r.selEnd, "> ")
        assertEquals(text, back.text)
    }

    @Test fun linePrefixAtCursorInMiddleLine() {
        val r = MarkdownEdit.linePrefix("a\nbcd\ne", 3, 3, "# ")
        assertEquals("a\n# bcd\ne", r.text)
        assertEquals(5, r.selStart)
    }

    @Test fun insertBlockAddsBlankLines() {
        val r = MarkdownEdit.insertBlock("fim", 3, "---")
        assertEquals("fim\n\n---\n", r.text)
    }

    @Test fun wordsAndReading() {
        assertEquals(3, MarkdownEdit.words("um  dois\n três **"))
        assertEquals(1, MarkdownEdit.readingMinutes("poucas palavras"))
        assertEquals(2, MarkdownEdit.readingMinutes(List(201) { "p" }.joinToString(" ")))
    }
}
