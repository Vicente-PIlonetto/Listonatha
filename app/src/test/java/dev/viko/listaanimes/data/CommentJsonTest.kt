package dev.viko.listaanimes.data

import dev.viko.listaanimes.data.repo.CommentJson
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentJsonTest {
    private val raw = listOf(
        mapOf(
            "id" to 10, "comment" to "a", "likeCount" to 2, "isLiked" to true, "createdAt" to 100,
            "user" to mapOf("id" to 1, "name" to "ana", "avatar" to mapOf("medium" to "u1")),
            "childComments" to listOf(
                mapOf("id" to 11, "comment" to "b", "likeCount" to 0, "createdAt" to 101,
                    "user" to mapOf("id" to 2, "name" to "bia"), "childComments" to null),
            ),
        ),
        mapOf("id" to 12, "comment" to "c", "childComments" to null),
        "lixo",
    )

    @Test fun parsesNestedReplies() {
        val tree = CommentJson.parseChildren(raw, 1)
        assertEquals(listOf(10, 12), tree.map { it.id })
        assertEquals("ana", tree[0].user?.name)
        assertEquals("u1", tree[0].user?.avatar)
        assertEquals(true, tree[0].isLiked)
        assertEquals(1, tree[0].depth)
        assertEquals(2, tree[0].children.single().depth)
        assertEquals(null, tree[1].user)
    }

    @Test fun flattensInReadingOrder() {
        assertEquals(listOf(10, 11, 12), CommentJson.flatten(CommentJson.parseChildren(raw, 0)).map { it.id })
    }

    @Test fun nullOrInvalidIsEmpty() {
        assertEquals(0, CommentJson.parseChildren(null, 0).size)
        assertEquals(0, CommentJson.parseChildren("x", 0).size)
    }
}
