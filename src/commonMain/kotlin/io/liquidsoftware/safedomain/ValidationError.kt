package io.liquidsoftware.safedomain

/**
 * Implemented by every error this library raises, so a boundary that only needs to
 * describe failures can handle them without naming each type: the boundary-mode
 * `fieldAt` overloads record them as they are, and [ErrorMessages.describe] describes any
 * of them.
 *
 * It is deliberately a plain marker, not a hierarchy with meaning. Inside a domain,
 * keep each type's precise `Error` and branch on that. A consumer error may implement
 * it to join a boundary-mode composite, but must then be described by the consumer's
 * own `describe` function, which delegates library errors to [ErrorMessages.describe].
 */
public interface ValidationError
