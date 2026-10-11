package io.liquidsoftware.safedomain.serialization

import io.liquidsoftware.safedomain.ErrorMessage
import kotlinx.serialization.SerializationException

/**
 * Decoding found well-formed input that the type's factory rejected. [error] is the
 * factory's typed error (cast it to the type's `Error`), and [messages] describe it
 * with stable codes. The exception message lists only the codes, never the rejected
 * input, which may be personal data or a secret.
 */
public class InvalidValueException(
    public val serialName: String,
    public val error: Any?,
    public val messages: List<ErrorMessage>,
) : SerializationException("Invalid $serialName: ${messages.joinToString { it.code }}")
