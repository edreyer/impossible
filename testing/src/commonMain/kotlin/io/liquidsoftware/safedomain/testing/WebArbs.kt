package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filter
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress

/** Names of one to four mixed-case labels; the last label is never all digits. */
public fun DomainName.Companion.arb(): Arb<DomainName> {
    val lastLabels = dnsLabel(maxLength = 20).filter { label -> !label.all { it in '0'..'9' } }
    return arbitrary { rs ->
        val labels = (1 until rs.random.nextInt(1, 5)).map { dnsLabel(maxLength = 20).bind() } + lastLabels.bind()
        DomainName.fromTrusted(labels.joinToString("."))
    }
}

/** Any IPv4 address, with 0.0.0.0 and 255.255.255.255 as edge cases. */
public fun IpAddress.V4.Companion.arb(): Arb<IpAddress.V4> =
    arbitrary(listOf("0.0.0.0", "255.255.255.255").map { IpAddress.V4.fromTrusted(it) }) { rs ->
        IpAddress.V4.fromTrusted((1..4).joinToString(".") { rs.random.nextInt(256).toString() })
    }

/**
 * Any IPv6 address. Each group is zero with high probability, so runs of zero groups,
 * the case RFC 5952 compression handles, are common. `::` and `::1` are edge cases.
 */
public fun IpAddress.V6.Companion.arb(): Arb<IpAddress.V6> =
    arbitrary(listOf("::", "::1").map { IpAddress.V6.fromTrusted(it) }) { rs ->
        val groups = (1..8).map { if (rs.random.nextInt(5) < 2) 0 else rs.random.nextInt(0x10000) }
        IpAddress.V6.fromTrusted(groups.joinToString(":") { it.toString(16) })
    }

/** IPv4 and IPv6 addresses in equal measure. */
public fun IpAddress.Companion.arb(): Arb<IpAddress> = Arb.choice(IpAddress.V4.arb(), IpAddress.V6.arb())

private const val URL_UNRESERVED = ALPHANUMERIC + "-_.~"

/**
 * Absolute HTTP and HTTPS URLs with schemes in mixed case, an optional user, a domain,
 * IPv4, or bracketed IPv6 host, an optional port, and optional path, query, and fragment
 * that include percent escapes.
 */
public fun HttpUrl.Companion.arb(): Arb<HttpUrl> {
    val urlHostLabels = dnsLabel(maxLength = 15, startWithLetter = true)
    val escape = arbitrary { rs ->
        "%" + "0123456789abcdefABCDEF".random(rs.random) + "0123456789ABCDEF".random(rs.random)
    }
    fun part(extra: String) = arbitrary { rs ->
        buildString {
            repeat(rs.random.nextInt(1, 12)) {
                if (rs.random.nextInt(10) == 0) append(escape.bind()) else append((URL_UNRESERVED + extra).random(rs.random))
            }
        }
    }
    val segments = part(":@&=+$,;")
    val queries = part(":@&=+$,;/?")
    val users = part(";:&=+$,")
    return arbitrary { rs ->
        val random = rs.random
        val url = buildString {
            append(listOf("http", "https", "HTTP", "Https").random(random)).append("://")
            if (random.nextInt(5) == 0) append(users.bind()).append('@')
            when (random.nextInt(4)) {
                0 -> append(IpAddress.V4.arb().bind())
                1 -> append('[').append(IpAddress.V6.arb().bind()).append(']')
                else -> {
                    val labels = (1 until random.nextInt(1, 4)).map {
                        dnsLabel(maxLength = 15).bind()
                    } + urlHostLabels.bind()
                    append(labels.joinToString("."))
                }
            }
            if (random.nextInt(4) == 0) append(':').append(random.nextInt(1, 65536))
            repeat(random.nextInt(4)) { append('/').append(segments.bind()) }
            if (random.nextInt(3) == 0) append('?').append(queries.bind())
            if (random.nextInt(5) == 0) append('#').append(queries.bind())
        }
        HttpUrl.fromTrusted(url)
    }
}
