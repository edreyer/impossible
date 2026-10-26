// Octets, group counts and bit masks are protocol constants, clearer inline.
@file:Suppress("MagicNumber")

package io.liquidsoftware.safedomain.web

import arrow.core.raise.Raise
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An IPv4 or IPv6 address, parsed from its standard text form. Equality and hashing use
 * the address bytes, so `::1` equals `0:0:0:0:0:0:0:1`; [toString] gives the canonical
 * text (dotted decimal for IPv4, RFC 5952 for IPv6). IPv4 parts are 0..255 written
 * without leading zeros, because `010` is octal to some parsers and decimal to others.
 * IPv6 accepts `::` compression and a trailing dotted IPv4 part; zone ids (`%eth0`) and
 * brackets are rejected. An IPv4-mapped IPv6 address (`::ffff:192.0.2.1`) stays IPv6.
 */
public sealed class IpAddress {
    public sealed interface Error : ValidationError {
        /** [toString] redacts [value], because an IP address can identify a person. */
        public data class InvalidFormat(val value: String) : Error {
            override fun toString(): String = "InvalidFormat(value=<redacted>)"
        }
    }

    protected abstract val bytes: ByteArray

    /** A copy of the address bytes in network order: 4 for IPv4, 16 for IPv6. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is IpAddress && this::class == other::class && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    public class V4 private constructor(override val bytes: ByteArray) : IpAddress() {
        override fun toString(): String = bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }

        public companion object : ValueFactory<String, Error, V4> {
            context(raise: Raise<Error>)
            override fun from(value: String): V4 =
                V4(raise.ensureNotNull(parseIpv4(value)) { Error.InvalidFormat(value) })
        }
    }

    public class V6 private constructor(override val bytes: ByteArray) : IpAddress() {
        override fun toString(): String {
            val groups = IntArray(8) {
                ((bytes[2 * it].toInt() and 0xFF) shl 8) or (bytes[2 * it + 1].toInt() and 0xFF)
            }
            // RFC 5952: compress the longest run of two or more zero groups, the first if tied.
            var bestStart = -1
            var bestLength = 1
            var index = 0
            while (index < 8) {
                if (groups[index] == 0) {
                    val start = index
                    while (index < 8 && groups[index] == 0) index++
                    if (index - start > bestLength) {
                        bestStart = start
                        bestLength = index - start
                    }
                } else {
                    index++
                }
            }
            if (bestStart < 0) return groups.joinToString(":") { it.toString(16) }
            val head = groups.take(bestStart).joinToString(":") { it.toString(16) }
            val tail = groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
            return "$head::$tail"
        }

        public companion object : ValueFactory<String, Error, V6> {
            context(raise: Raise<Error>)
            override fun from(value: String): V6 =
                V6(raise.ensureNotNull(parseIpv6(value)) { Error.InvalidFormat(value) })
        }
    }

    /** Parses either form: text containing ':' is IPv6, anything else IPv4. */
    public companion object : ValueFactory<String, Error, IpAddress> {
        context(raise: Raise<Error>)
        override fun from(value: String): IpAddress = if (':' in value) V6.from(value) else V4.from(value)

        private val decimalPart = Regex("0|[1-9][0-9]{0,2}")
        private val hexGroup = Regex("[0-9A-Fa-f]{1,4}")

        private fun parseIpv4(text: String): ByteArray? {
            val parts = text.split('.')
            if (parts.size != 4 || !parts.all(decimalPart::matches)) return null
            val octets = parts.map { it.toInt() }
            if (octets.any { it > 255 }) return null
            return ByteArray(4) { octets[it].toByte() }
        }

        @Suppress("CyclomaticComplexMethod") // A hand-written parser; one branch per grammar case.
        private fun parseIpv6(text: String): ByteArray? {
            val halves = text.split("::")
            if (halves.size > 2) return null
            val sides = halves.map { half -> if (half.isEmpty()) emptyList() else half.split(':') }
            val all = sides.flatten()
            // A dotted IPv4 part may only end the address, and supplies the last two groups.
            val ipv4 = sides.last().lastOrNull()?.takeIf { '.' in it }?.let { parseIpv4(it) ?: return null }
            val hexCount = all.size - if (ipv4 == null) 0 else 1
            val units = hexCount + if (ipv4 == null) 0 else 2
            // With "::" the explicit groups must leave room for at least one zero group.
            val fitsGroupCount = if (halves.size == 2) units < 8 else units == 8
            if (!fitsGroupCount) return null
            val groups = all.take(hexCount)
            if (!groups.all(hexGroup::matches)) return null

            val result = ByteArray(16)
            fun put(position: Int, group: String) {
                val number = group.toInt(16)
                result[2 * position] = (number shr 8).toByte()
                result[2 * position + 1] = number.toByte()
            }
            val headCount = if (halves.size == 2) sides[0].size else hexCount
            groups.take(headCount).forEachIndexed { index, group -> put(index, group) }
            val tailGroups = groups.drop(headCount)
            val tailStart = 8 - (if (ipv4 == null) 0 else 2) - tailGroups.size
            tailGroups.forEachIndexed { index, group -> put(tailStart + index, group) }
            ipv4?.copyInto(result, 12)
            return result
        }
    }
}
