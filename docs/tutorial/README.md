# Tutorial: building a bookshop with impossible

This tutorial builds the domain model of a small online bookshop, one idea at a
time. Each step uses what the previous ones introduced, so read them in order the
first time; afterwards, every step works as a reference you can copy from.

By the end you will have books with validated ISBNs, customers who can always be
reached, wishlists, prices and discounts in exact money, promotions checked against
the clock, an order form that reports every mistake against the field that caused
it, an HTTP error response clients can translate, JSON that cannot smuggle in
invalid data, and property tests for all of it.

| Step | You learn | Library features |
| --- | --- | --- |
| [1. First values](01-first-values.md) | Construct a value that cannot be invalid and handle its typed error | `NonBlankString`, `PositiveInt`, `either`, `Raise` contexts, `fromTrusted` |
| [2. Your own errors](02-your-own-errors.md) | Wrap library errors in your domain's errors, layer by layer | `ValueFactory`, `from(value, onError)`, boundary outcomes |
| [3. Your own types](03-your-own-types.md) | Define new validated types without writing a parser | `map`, `mapError`, `ensure`, `andThen`, `NonBlankString.bounded`, `BoundedInt` |
| [4. Collect every error](04-collect-every-error.md) | Report all invalid fields at once | `validate { }`, `field` |
| [5. Optional and dependent fields](05-optional-and-dependent-fields.md) | Nullable input, required input, and rules that span fields | `optional()`, `required()`, `field { }`, `Email`, `Phone` |
| [6. Collections](06-collections.md) | Validate every element, then the collection | `eachField`, `NonEmptyList.factory()`, `UniqueList`, `BoundedList` |
| [7. Decimals and money](07-decimals-and-money.md) | Exact amounts, discounts, and arithmetic that never rounds by accident | `Decimal`, `Percentage`, `Money`, `PositiveMoney`, `flatField` |
| [8. Identifiers and time](08-ids-and-time.md) | Ids that can't be mixed up, dates checked against a clock | `TypedId`, `DateChecks`, `DateRange`, `TimeWindow`, `PositiveDuration` |
| [9. Field paths](09-field-paths.md) | Say which field failed, including nested and list fields | `fieldAt`, `nestedFieldAt`, `eachFieldAt`, `flatFieldAt`, `FieldPath` |
| [9b. Boundary mode](09b-boundary-mode.md) | Check a request with no wrapper errors and answer with one call | `ValidationError`, `fieldAt` without a lambda, `toProblemDetails()` |
| [10. Messages and problem details](10-messages-and-problem-details.md) | Turn errors into stable codes, text, and an RFC 9457 response | `ErrorMessages`, `ErrorMessage`, `toProblemDetails` |
| [11. JSON](11-json.md) | Read and write JSON through the validating factories | `impossible-serialization`, `asSerializer`, `InvalidValueException` |
| [12. Property testing](12-property-testing.md) | Generate valid values and real errors to test your own code | `impossible-testing`, `arb()`, `errorArb()`, `untrustedText()`, `nearMiss()` |

Already know the basics and want to see the library inside a framework? The
[recipes](../recipes/README.md) cover Ktor, Spring, persistence, and normalizing input
before parsing.

## Before you start

The library is Kotlin Multiplatform and needs Kotlin 2.4 or later, because its API
uses context parameters (`context(raise: Raise<Error>)`). It builds on
[Arrow](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/)'s `Raise`;
you don't need to know Arrow first, as step 1 introduces the few pieces you need.

The library is not yet published to Maven Central. Until it is, build against a
checkout, for example with a [composite build](https://docs.gradle.org/current/userguide/composite_builds.html)
in your `settings.gradle.kts`:

```kotlin-gradle
includeBuild("../impossible")
```

and the coordinates in your module's dependencies:

```kotlin-gradle
dependencies {
    implementation("io.liquidsoftware:impossible:1.0-SNAPSHOT")
    // Optional modules, used in steps 11 and 12:
    implementation("io.liquidsoftware:impossible-serialization:1.0-SNAPSHOT")
    testImplementation("io.liquidsoftware:impossible-testing:1.0-SNAPSHOT")
}
```

Step 12 uses `impossible-testing`, which depends on kotest-property 6.x; a project on
kotest 5 has to upgrade before it can use that module.

Every type lives in a subpackage of `io.liquidsoftware.safedomain` named after its
domain (`text`, `numeric`, `money`, `contact`, `time`, and so on); the construction
tools (`ValueFactory`, `validate`, the combinators) live in the root package. Each
step lists the imports it needs.

## The code is tested

Every Kotlin snippet in these chapters is copied from a test source that compiles and
runs on every platform the library supports, and `TutorialSnippetsTest` fails the
build if a snippet and its source drift apart. The sources are:

- [`src/commonTest/.../tutorial`](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial) for steps 1 to 10,
- [`serialization/src/commonTest/.../tutorial`](../../serialization/src/commonTest/kotlin/io/liquidsoftware/safedomain/serialization/tutorial) for step 11,
- [`testing/src/commonTest/.../tutorial`](../../testing/src/commonTest/kotlin/io/liquidsoftware/safedomain/testing/tutorial) for step 12.

Each file ends with the tests for its step, which double as more examples. The
[README](../../README.md) is the reference for every type's exact rules.

Start with [step 1](01-first-values.md).
