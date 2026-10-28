package io.liquidsoftware.safedomain.jackson

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ValueFactory
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer

/**
 * A deserializer that reads [input] with Jackson's usual rules and runs it through this
 * factory, so invalid data never becomes a value. A failure throws
 * [InvalidValueException] carrying the typed error, described by [describe].
 *
 * Use it for your own refined types, next to [jacksonSerializer]:
 * `module.addDeserializer(Sku::class.java, Sku.asJacksonDeserializer("com.example.Sku", String::class.java, ::describe))`.
 */
public fun <Input, Error, Value> ValueFactory<Input, Error, Value>.asJacksonDeserializer(
    typeName: String,
    input: Class<Input>,
    describe: (Error) -> List<ErrorMessage>,
): ValueDeserializer<Value> = ValidatingDeserializer(typeName, this, { parser, context ->
    context.readValue(parser, input)
}, describe)

/** A serializer that writes [render] of a value with whatever serializer Jackson has for the result. */
public fun <Value, Output> jacksonSerializer(render: (Value) -> Output): ValueSerializer<Value> =
    object : ValueSerializer<Value>() {
        override fun serialize(value: Value, gen: JsonGenerator, serializers: SerializationContext) {
            serializers.writeValue(gen, render(value))
        }
    }

/** Reads a factory's input from the parser's current token. */
internal typealias Reader<Input> = (JsonParser, DeserializationContext) -> Input

internal class ValidatingDeserializer<Input, Error, Value>(
    private val typeName: String,
    private val factory: ValueFactory<Input, Error, Value>,
    private val read: Reader<Input>,
    private val describe: (Error) -> List<ErrorMessage>,
) : ValueDeserializer<Value>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Value {
        // Take the pointer before reading: an object or array has already opened its own context.
        val pointer = pointerOf(p)
        val raw = read(p, ctxt)
        return when (val result = either { factory.from(raw) }) {
            is Either.Right -> result.value
            is Either.Left -> throw InvalidValueException(p, typeName, result.value, describe(result.value), pointer)
        }
    }
}

/** The JSON Pointer of the value at the parser's current token. */
internal fun pointerOf(p: JsonParser): String {
    val context = p.streamReadContext()
    val holder = if (p.currentToken()?.isStructStart == true) context.parent ?: context else context
    return holder.pathAsPointer().toString()
}
