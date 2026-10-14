package com.gonzaroman.acornix.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A query over the documents of one collection, as sent by `collection.list({...})`:
 * `where` matches top-level fields by equality or with operators ($gt, $gte, $lt, $lte, $ne, $in,
 * $contains), `orderBy` sorts by a field ("-field" sorts descending).
 */
data class DocQuery(
    val where: JsonObject? = null,
    val orderBy: String? = null,
    val desc: Boolean = false,
    val limit: Int? = null,
    val offset: Int = 0,
) {
    companion object {
        fun from(json: JsonElement?): DocQuery {
            val obj = json as? JsonObject ?: return DocQuery()
            return DocQuery(
                where = obj["where"] as? JsonObject,
                orderBy = (obj["orderBy"] as? JsonPrimitive)?.content,
                desc = (obj["desc"] as? JsonPrimitive)?.booleanOrNull ?: false,
                limit = (obj["limit"] as? JsonPrimitive)?.intOrNull,
                offset = (obj["offset"] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }
}

/** Pure filtering/sorting so it can run (and be tested) without SQLite. */
object DocQueryEngine {

    fun apply(docs: List<JsonObject>, q: DocQuery): List<JsonObject> {
        var result = docs.filter { matches(it, q.where) }
        val order = q.orderBy?.takeIf { it.isNotBlank() }
        if (order != null) {
            val field = order.removePrefix("-")
            val desc = q.desc || order.startsWith("-")
            val cmp = Comparator<JsonObject> { a, b -> compareValues(a[field], b[field]) }
            result = result.sortedWith(if (desc) cmp.reversed() else cmp)
        } else if (q.desc) {
            result = result.reversed()
        }
        val from = q.offset.coerceAtLeast(0)
        if (from > 0) result = result.drop(from)
        q.limit?.takeIf { it >= 0 }?.let { result = result.take(it) }
        return result
    }

    fun matches(doc: JsonObject, where: JsonObject?): Boolean {
        if (where == null) return true
        return where.all { (field, condition) ->
            val value = doc[field]
            if (condition is JsonObject && condition.keys.any { it.startsWith("$") }) {
                condition.all { (op, arg) -> test(op, value, arg) }
            } else {
                equalsJson(value, condition)
            }
        }
    }

    private fun test(op: String, value: JsonElement?, arg: JsonElement): Boolean = when (op) {
        "\$eq" -> equalsJson(value, arg)
        "\$ne" -> !equalsJson(value, arg)
        "\$gt" -> value.isPresent() && compareValues(value, arg) > 0
        "\$gte" -> value.isPresent() && compareValues(value, arg) >= 0
        "\$lt" -> value.isPresent() && compareValues(value, arg) < 0
        "\$lte" -> value.isPresent() && compareValues(value, arg) <= 0
        "\$in" -> (arg as? JsonArray)?.any { equalsJson(value, it) } ?: false
        "\$contains" -> {
            val needle = (arg as? JsonPrimitive)?.content.orEmpty()
            when (value) {
                is JsonArray -> value.any { equalsJson(it, arg) }
                is JsonPrimitive -> value.content.contains(needle, ignoreCase = true)
                else -> false
            }
        }
        else -> false
    }

    private fun JsonElement?.isPresent() = this != null && this !is JsonNull

    private fun equalsJson(a: JsonElement?, b: JsonElement?): Boolean {
        val x = a ?: JsonNull
        val y = b ?: JsonNull
        if (x is JsonPrimitive && y is JsonPrimitive && !x.isString && !y.isString) {
            val dx = x.doubleOrNull
            val dy = y.doubleOrNull
            if (dx != null && dy != null) return dx == dy
        }
        return x == y
    }

    /** Numbers numerically, strings case-insensitively, missing values last. */
    fun compareValues(a: JsonElement?, b: JsonElement?): Int {
        val pa = (a as? JsonPrimitive)?.takeIf { it !is JsonNull }
        val pb = (b as? JsonPrimitive)?.takeIf { it !is JsonNull }
        if (pa == null && pb == null) return 0
        if (pa == null) return 1
        if (pb == null) return -1
        val na = if (pa.isString) null else pa.doubleOrNull
        val nb = if (pb.isString) null else pb.doubleOrNull
        if (na != null && nb != null) return na.compareTo(nb)
        val ba = pa.booleanOrNull
        val bb = pb.booleanOrNull
        if (!pa.isString && !pb.isString && ba != null && bb != null) return ba.compareTo(bb)
        return pa.content.compareTo(pb.content, ignoreCase = true)
    }

    /**
     * "text (string), category (string: "home" | "work"), done (boolean)" — describes a collection to
     * the AI. Short text fields that keep repeating a few values (categories, states…) list them, so
     * new documents reuse the same values instead of inventing new ones.
     */
    fun describeFields(docs: List<JsonObject>): String {
        val sample = docs.take(50)
        val types = linkedMapOf<String, MutableSet<String>>()
        val strings = linkedMapOf<String, MutableList<String>>()
        sample.forEach { doc ->
            doc.forEach { (k, v) ->
                if (k.startsWith("_")) return@forEach
                types.getOrPut(k) { linkedSetOf() } += typeOf(v)
                if (v is JsonPrimitive && v.isString) strings.getOrPut(k) { mutableListOf() } += v.content
            }
        }
        return types.entries.joinToString(", ") { (k, t) ->
            val values = strings[k].orEmpty()
            val distinct = values.distinct()
            val enumLike = values.size >= 2 && distinct.size <= 5 && distinct.size <= values.size * 0.7 && distinct.all { it.length <= 24 }
            if (enumLike) "$k (${t.joinToString("|")}: ${distinct.joinToString(" | ") { "\"$it\"" }})"
            else "$k (${t.joinToString("|")})"
        }
    }

    private fun typeOf(v: JsonElement): String = when {
        v is JsonNull -> "null"
        v is JsonObject -> "object"
        v is JsonArray -> "array"
        v is JsonPrimitive && v.isString -> "string"
        v is JsonPrimitive && v.booleanOrNull != null -> "boolean"
        else -> "number"
    }
}
