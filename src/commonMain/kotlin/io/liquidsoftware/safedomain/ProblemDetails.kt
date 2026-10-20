package io.liquidsoftware.safedomain

import arrow.core.NonEmptyList

/**
 * An RFC 9457 problem details object for an HTTP API error response, with the RFC's
 * `errors` extension listing each invalid field. This is plain data: render it as
 * `application/problem+json` with your JSON library.
 *
 * @property type a URI identifying the problem type; `about:blank` when the HTTP status says it all
 * @property title a short, human-readable summary that does not change between occurrences
 * @property status the HTTP status code
 * @property detail an explanation specific to this occurrence
 * @property instance a URI identifying this occurrence, if any
 */
public data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: String?,
    val errors: List<InvalidField>,
) {
    /**
     * One failure: [pointer] is an RFC 6901 JSON Pointer to the field (empty for the
     * whole request), [code] the stable error code, [detail] its default English
     * description, and [arguments] the values a translation of [code] needs, as in
     * [ErrorMessage.arguments].
     */
    public data class InvalidField(
        val pointer: String,
        val code: String,
        val detail: String,
        val arguments: Map<String, String> = emptyMap(),
    )
}

/**
 * Describes every failure with [describe] and collects them into a single problem: by
 * default `422 Unprocessable Content`, whose `errors` point at each invalid field and
 * carry each message's code and arguments, so a client can localize them. [detail]
 * defaults to an English count of the problems; pass your own, or null to omit it.
 */
public fun <Error> NonEmptyList<FieldError<Error>>.toProblemDetails(
    describe: (Error) -> ErrorMessage,
    title: String = "The request is not valid.",
    status: Int = 422,
    type: String = "about:blank",
    instance: String? = null,
    detail: String? = if (size == 1) "The request has 1 problem." else "The request has $size problems.",
): ProblemDetails {
    val fields = map { failure ->
        val message = describe(failure.error)
        ProblemDetails.InvalidField(failure.path.toJsonPointer(), message.code, message.text, message.arguments)
    }
    return ProblemDetails(type, title, status, detail, instance, fields)
}
