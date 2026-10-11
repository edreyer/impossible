package io.liquidsoftware.safedomain.serialization

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ValueFactory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Decoding found well-formed input that the type's factory rejected. [error] is the
 * factory's typed error (cast it to the type's `Error`), and [messages] describe it
 * with stable codes. The exception message lists only the codes, never the rejected
 * input, which may be personal data or a secret.
 */
class InvalidValueException(
    val serialName: String,
    val error: Any?,
    val messages: List<ErrorMessage>,
) : SerializationException("Invalid $serialName: ${messages.joinToString { it.code }}")

/**
 * A serializer that writes [render] of a value with [input] and reads by running the
 * decoded input through this factory, so invalid data never becomes a value. A failure
 * throws [InvalidValueException] carrying the typed error, described by [describe].
 * Input the wire format itself cannot read, such as a malformed date, throws a plain
 * [SerializationException] that does not quote it.
 *
 * Use it to serialize your own refined types:
 * `object SkuSerializer : KSerializer<Sku> by Sku.asSerializer("com.example.Sku", String.serializer(), Sku::value, ::describe)`.
 */
fun <Input, Error, Value> ValueFactory<Input, Error, Value>.asSerializer(
    serialName: String,
    input: KSerializer<Input>,
    render: (Value) -> Input,
    describe: (Error) -> List<ErrorMessage>,
): KSerializer<Value> = ValidatingSerializer(serialName, this, input, render, describe)

private class ValidatingSerializer<Input, Error, Value>(
    private val serialName: String,
    private val factory: ValueFactory<Input, Error, Value>,
    private val input: KSerializer<Input>,
    private val render: (Value) -> Input,
    private val describe: (Error) -> List<ErrorMessage>,
) : KSerializer<Value> {
    override val descriptor: SerialDescriptor = input.descriptor.let { original ->
        when (val kind = original.kind) {
            is PrimitiveKind -> PrimitiveSerialDescriptor(serialName, kind)
            else -> SerialDescriptor(serialName, original)
        }
    }

    override fun serialize(encoder: Encoder, value: Value) {
        encoder.encodeSerializableValue(input, render(value))
    }

    override fun deserialize(decoder: Decoder): Value {
        val raw = try {
            decoder.decodeSerializableValue(input)
        } catch (e: SerializationException) {
            throw e
        } catch (_: IllegalArgumentException) {
            // Date, instant and duration parsers throw plain IllegalArgumentExceptions whose
            // messages quote the input; report malformed data as the format's own failure.
            throw SerializationException("Malformed $serialName")
        }
        return when (val result = either { factory.from(raw) }) {
            is Either.Right -> result.value
            is Either.Left -> throw InvalidValueException(serialName, result.value, describe(result.value))
        }
    }
}

/** Reads and writes [A] as [B]'s wire form, for surrogates that are plain data. */
internal fun <A, B> KSerializer<A>.mapped(serialName: String, decode: (A) -> B, encode: (B) -> A): KSerializer<B> {
    val inner = this
    return object : KSerializer<B> {
        override val descriptor: SerialDescriptor = SerialDescriptor(serialName, inner.descriptor)
        override fun serialize(encoder: Encoder, value: B) = encoder.encodeSerializableValue(inner, encode(value))
        override fun deserialize(decoder: Decoder): B = decode(decoder.decodeSerializableValue(inner))
    }
}
