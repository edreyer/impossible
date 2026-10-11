package io.liquidsoftware.safedomain

/**
 * Where in a composite input a failure occurred, such as `lines[2].price`: field names
 * and list indices from the outermost value inward. The [ROOT] path, with no segments,
 * stands for the whole input, for checks that span several fields.
 *
 * The `validate` DSL builds paths for you: [fieldAt] and its siblings name a field after
 * the property it is assigned to, and nested composites prefix their paths with the
 * field that holds them.
 */
class FieldPath private constructor(val segments: List<Segment>) {
    /** One step into a value: a named field or a zero-based list position. */
    sealed interface Segment {
        data class Name(val name: String) : Segment
        data class Index(val index: Int) : Segment
    }

    val isRoot: Boolean get() = segments.isEmpty()

    /** This path followed by the field [name]. */
    fun child(name: String): FieldPath = FieldPath(segments + Segment.Name(name))

    /** This path followed by the list position [index]. */
    fun index(index: Int): FieldPath {
        require(index >= 0) { "Index must not be negative: $index" }
        return FieldPath(segments + Segment.Index(index))
    }

    /** This path followed by every segment of [inner]. */
    operator fun plus(inner: FieldPath): FieldPath = if (inner.isRoot) this else FieldPath(segments + inner.segments)

    /**
     * The path as an RFC 6901 JSON Pointer, such as `/lines/2/price`, as RFC 9457
     * problem details use. `~` and `/` in names are escaped; the root is the empty string.
     */
    fun toJsonPointer(): String = segments.joinToString("") { segment ->
        when (segment) {
            is Segment.Name -> "/" + segment.name.replace("~", "~0").replace("/", "~1")
            is Segment.Index -> "/" + segment.index
        }
    }

    override fun equals(other: Any?): Boolean = other is FieldPath && segments == other.segments

    override fun hashCode(): Int = segments.hashCode()

    /** Dotted names with bracketed indices, such as `lines[2].price`; the root is empty. */
    override fun toString(): String = buildString {
        for (segment in segments) {
            when (segment) {
                is Segment.Name -> {
                    if (isNotEmpty()) append('.')
                    append(segment.name)
                }
                is Segment.Index -> append('[').append(segment.index).append(']')
            }
        }
    }

    companion object {
        /** The whole input. */
        val ROOT: FieldPath = FieldPath(emptyList())

        /** The top-level field [name]. */
        fun of(name: String): FieldPath = ROOT.child(name)
    }
}

/**
 * A failure of type [Error] at [path]. Composites built with the path-aware `validate`
 * helpers raise a `NonEmptyList<FieldError<Error>>`, so a boundary can report each
 * failure against the field that caused it.
 */
data class FieldError<out Error>(val path: FieldPath, val error: Error) {
    /** The same failure with its error mapped by [transform]. */
    fun <Mapped> map(transform: (Error) -> Mapped): FieldError<Mapped> = FieldError(path, transform(error))

    /** The same failure, nested under [parent]. */
    fun under(parent: FieldPath): FieldError<Error> = FieldError(parent + path, error)

    /** `path: error`, or just the error at the root; the error's own redaction applies. */
    override fun toString(): String = if (path.isRoot) error.toString() else "$path: $error"
}
