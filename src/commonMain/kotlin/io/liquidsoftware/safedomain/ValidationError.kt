package io.liquidsoftware.safedomain

/**
 * Implemented by every error this library raises, so a boundary that only needs to
 * describe failures can handle them without naming each type: the boundary-mode
 * `fieldAt` overloads record them as they are, and `toProblemDetails()` describes them
 * with [describe].
 *
 * It is deliberately not a hierarchy with meaning. Inside a domain, keep each type's
 * precise `Error` and branch on that. A consumer error implements it to join a
 * boundary-mode composite, and then must say how it is described, so no error can reach
 * a response undescribed:
 *
 * ```kotlin
 * data class SeatsOverLimit(val limit: Int) : ValidationError {
 *     override fun describe() = ErrorMessage("signup.seats_over_limit", "Book at most $limit seats.")
 * }
 * ```
 */
public interface ValidationError {
    /**
     * This error's stable code, default English text and arguments. Library errors
     * delegate to their [ErrorMessages] overload.
     */
    public fun describe(): ErrorMessage
}
