package io.liquidsoftware.safedomain.web

import java.net.URI

/** The URL as written, as a `java.net.URI`. Parsing follows the same rules, so this cannot fail. */
fun HttpUrl.toJavaUri(): URI = URI(value)
