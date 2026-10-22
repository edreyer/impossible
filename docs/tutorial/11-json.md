# Step 11: JSON

**You will learn** how to read and write the library's types, and your own, with
kotlinx.serialization, so that decoding runs the real factory and invalid JSON never
becomes a value.

This step uses the `impossible-serialization` module and the Kotlin serialization
compiler plugin. Imports: `io.liquidsoftware.safedomain.serialization.*` (the
serializers, `asSerializer`, `InvalidValueException` and
`safeDomainSerializersModule`), `kotlinx.serialization.Serializable`,
`kotlinx.serialization.Contextual`, `kotlinx.serialization.KSerializer`,
`kotlinx.serialization.builtins.serializer` and `kotlinx.serialization.json.Json`.

## Your own types

Tests in this module can't see the library module's tests, so this step uses a
condensed copy of step 3's `Isbn` (one error case, no check digit). `asSerializer`
turns any `ValueFactory` into a serializer. You say how a value is written (`render`)
and how its errors are described:

```kotlin
object IsbnSerializer : KSerializer<Isbn> by Isbn.asSerializer(
    serialName = "com.example.bookshop.Isbn",
    input = String.serializer(),
    render = Isbn::value,
    describe = { listOf(ErrorMessage("isbn.invalid", "Enter the 13 digits of an ISBN.")) },
)
```

In a real project, `describe` would call the `describe` functions from step 10.

## Library types

Every library type has a serializer. Types that need configuration, such as
`BoundedInt`'s range, get one per use:

```kotlin
object RatingSerializer : KSerializer<BoundedInt> by BoundedIntSerializer(1..5)

@Serializable
data class Review(
    @Serializable(with = IsbnSerializer::class) val isbn: Isbn,
    @Serializable(with = EmailSerializer::class) val reviewer: Email,
    @Serializable(with = RatingSerializer::class) val rating: BoundedInt,
    @Serializable(with = NonBlankStringSerializer::class) val text: NonBlankString,
)
```

The wire forms are the plain ones you'd expect:

```json
{"isbn":"9780441013593","reviewer":"ada@example.com","rating":5,"text":"A classic."}
```

Text types are strings, integers are numbers, decimals are strings such as `"12.30"`
(so no platform rounds them through a `Double`), money is
`{"amount":"12.30","currency":"EUR"}`, dates and durations are ISO 8601 text, and
`UniqueList`/`BoundedList` are arrays.

## Invalid data throws a typed failure

Decoding runs the factory. When the factory rejects a value, decoding throws
`InvalidValueException`, a `SerializationException` that carries the type's
`serialName`, the typed `error`, and its `messages` with their codes:

```kotlin
sealed interface ReviewSubmission {
    data class Accepted(val review: Review) : ReviewSubmission
    data class Rejected(val serialName: String, val codes: List<String>) : ReviewSubmission
}

fun submitReview(body: String): ReviewSubmission = try {
    ReviewSubmission.Accepted(json.decodeFromString<Review>(body))
} catch (e: InvalidValueException) {
    ReviewSubmission.Rejected(e.serialName, e.messages.map { it.code })
}
```

A rating of 6 fails with `bounded_int.out_of_range` and `error` is a
`BoundedInt.Error.OutOfRange`. Neither the exception's message nor its codes quote the
rejected input. JSON that's malformed for the format itself, such as a date that
isn't a date, throws a plain `SerializationException`.

Exceptions are how kotlinx.serialization reports failure, so here the typed error
arrives inside one. Catch it at the boundary, as `submitReview` does, and you are
back to typed values.

## The contextual module

Instead of naming a serializer on each field, register `safeDomainSerializersModule`
once and mark fields `@Contextual`:

```kotlin
val json: Json = Json { serializersModule = safeDomainSerializersModule }

@Serializable
data class GiftCard(@Contextual val balance: Money, @Contextual val recipient: Email)
```

The module covers every type that needs no configuration. `TypedId` fields need
`@Serializable(with = TypedIdSerializer::class)` instead, because the serialization
plugin crashes on `@Contextual` for a generic value class.

## Secrets are never written

`SecretSerializer` reads a `Secret` but refuses to write one, so a password can't end
up in a response or a log by accident:

```kotlin
@Serializable
data class Login(@Contextual val email: Email, @Serializable(with = SecretSerializer::class) val password: Secret)
```

Decoding a `Login` works; encoding one throws `SerializationException`. Map secrets to
something safe, such as a hash, before writing.

## One error, or all of them?

A decoder stops at the first invalid field, so the exception names only one problem.
For a form or an API where the client should see every problem at once, decode into
a DTO of plain strings and numbers (such as step 9's `OrderInput`) and build the
domain value with the path-aware factory. Step 10 then turns all its failures into
one problem details response.

## What you have now

JSON in and out of your domain types, with the factories as the only way in: there's
no `fromTrusted` hidden in a decoder.

The code and its tests are in
[Step11JsonTest.kt](../../serialization/src/commonTest/kotlin/io/liquidsoftware/safedomain/serialization/tutorial/Step11JsonTest.kt).

Next: [Step 12, property testing](12-property-testing.md).
