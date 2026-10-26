package io.liquidsoftware.safedomain.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonMappingException
import io.liquidsoftware.safedomain.ErrorMessage

/**
 * Reading found well-formed JSON that the type's factory rejected. [error] is the
 * factory's typed error (cast it to the type's `Error`), [messages] describe it with
 * stable codes, and [pointer] is the RFC 6901 JSON Pointer of the rejected value, such as
 * `/email`, ready for a problem-details response. The exception message lists only the
 * codes, never the rejected input, which may be personal data or a secret.
 */
public class InvalidValueException(
    parser: JsonParser?,
    public val typeName: String,
    public val error: Any?,
    public val messages: List<ErrorMessage>,
    public val pointer: String,
) : JsonMappingException(parser, "Invalid $typeName: ${messages.joinToString { it.code }}")
