package io.liquidsoftware.safedomain.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException

// Strict readers for the library's own wire forms, matching impossible-serialization:
// text only from JSON strings, integers only from JSON integers in range, and no
// silent coercion (a float is never truncated to an integer). Their failures name the
// expected shape, never the input.

internal fun mismatch(p: JsonParser, message: String): Nothing =
    throw MismatchedInputException.from(p, Any::class.java, message)

internal val readString: Reader<String> = { p, _ ->
    if (p.currentToken == JsonToken.VALUE_STRING) p.text else mismatch(p, "Expected a JSON string")
}

internal val readInt: Reader<Int> = { p, _ ->
    if (p.currentToken == JsonToken.VALUE_NUMBER_INT && p.numberType == JsonParser.NumberType.INT) {
        p.intValue
    } else {
        mismatch(p, "Expected a JSON integer in the Int range")
    }
}

internal val readLong: Reader<Long> = { p, _ ->
    val type = p.numberType
    if (p.currentToken == JsonToken.VALUE_NUMBER_INT && (type == JsonParser.NumberType.INT || type == JsonParser.NumberType.LONG)) {
        p.longValue
    } else {
        mismatch(p, "Expected a JSON integer in the Long range")
    }
}

/** Reads text with [parse], reporting a parse failure as malformed input without quoting it. */
internal fun <A> readParsed(what: String, parse: (String) -> A): Reader<A> = { p, ctxt ->
    val text = readString(p, ctxt)
    try {
        parse(text)
    } catch (_: IllegalArgumentException) {
        mismatch(p, "Malformed $what")
    }
}

/** The named fields of a JSON object, each required; unknown fields fail when Jackson is configured to. */
internal class Fields(private val p: JsonParser, private val node: JsonNode) {
    fun string(name: String): String {
        val value = field(name)
        return if (value.isTextual) value.textValue() else mismatch(p, "Expected a JSON string in field $name")
    }

    fun double(name: String): Double {
        val value = field(name)
        return if (value.isNumber) value.doubleValue() else mismatch(p, "Expected a JSON number in field $name")
    }

    fun <A> parsed(name: String, what: String, parse: (String) -> A): A = try {
        parse(string(name))
    } catch (_: IllegalArgumentException) {
        mismatch(p, "Malformed $what in field $name")
    }

    private fun field(name: String): JsonNode = node.get(name) ?: mismatch(p, "Missing field $name")
}

internal fun <A> readObject(vararg names: String, build: Fields.() -> A): Reader<A> = { p, ctxt ->
    if (p.currentToken != JsonToken.START_OBJECT) mismatch(p, "Expected a JSON object")
    val node: JsonNode = ctxt.readTree(p)
    if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
        val unknown = node.fieldNames().asSequence().firstOrNull { it !in names }
        if (unknown != null) mismatch(p, "Unknown field $unknown")
    }
    Fields(p, node).build()
}
