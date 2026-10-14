package com.gonzaroman.acornix.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DocQueryTest {
    private fun doc(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun ids(list: List<JsonObject>) = list.map { it["_id"]!!.jsonPrimitive.content }

    private val docs = listOf(
        doc("""{"_id":"a","_created":1,"text":"Comprar pan","done":false,"score":10,"tags":["casa"]}"""),
        doc("""{"_id":"b","_created":2,"text":"Llamar a Ana","done":true,"score":250,"tags":["trabajo"]}"""),
        doc("""{"_id":"c","_created":3,"text":"pan integral","done":false,"score":99.5}"""),
    )

    @Test
    fun equalityAndOperators() {
        assertEquals(listOf("a", "c"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"done":false}""")))))
        assertEquals(listOf("b"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"score":{"${'$'}gt":100}}""")))))
        assertEquals(listOf("a", "c"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"text":{"${'$'}contains":"PAN"}}""")))))
        assertEquals(listOf("a"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"tags":{"${'$'}contains":"casa"}}""")))))
        assertEquals(listOf("a", "b"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"_id":{"${'$'}in":["a","b","z"]}}""")))))
        assertEquals(listOf("b"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"done":{"${'$'}ne":false}}""")))))
        // 10 and 10.0 are the same number.
        assertEquals(listOf("a"), ids(DocQueryEngine.apply(docs, DocQuery(where = doc("""{"score":10.0}""")))))
    }

    @Test
    fun sortingPagingAndMissingFields() {
        assertEquals(listOf("b", "c", "a"), ids(DocQueryEngine.apply(docs, DocQuery(orderBy = "-score"))))
        assertEquals(listOf("a", "c", "b"), ids(DocQueryEngine.apply(docs, DocQuery(orderBy = "score"))))
        assertEquals(listOf("c", "b"), ids(DocQueryEngine.apply(docs, DocQuery(orderBy = "_created", desc = true, limit = 2))))
        assertEquals(listOf("b"), ids(DocQueryEngine.apply(docs, DocQuery(orderBy = "_created", offset = 1, limit = 1))))
        // Documents without "tags" sort last.
        assertEquals("c", ids(DocQueryEngine.apply(docs, DocQuery(orderBy = "tags"))).last())
    }

    @Test
    fun parsesQueryFromJs() {
        val q = DocQuery.from(doc("""{"where":{"done":false},"orderBy":"text","desc":true,"limit":5}"""))
        assertEquals("text", q.orderBy)
        assertEquals(true, q.desc)
        assertEquals(5, q.limit)
        assertEquals(DocQuery(), DocQuery.from(null))
    }

    @Test
    fun describesFieldsForTheAi() {
        assertEquals("text (string), done (boolean), score (number), tags (array)", DocQueryEngine.describeFields(docs))
    }

    @Test
    fun listsRepeatedCategoryValues() {
        val todos = listOf(
            doc("""{"text":"Buy milk","category":"home"}"""),
            doc("""{"text":"Buy bread","category":"shopping"}"""),
            doc("""{"text":"Call Ana","category":"home"}"""),
        )
        assertEquals("text (string), category (string: \"home\" | \"shopping\")", DocQueryEngine.describeFields(todos))
    }
}
