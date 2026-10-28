# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/). Until 1.0.0, a minor
version may change the public API.

## [Unreleased]

## [0.2.0]

### Changed

- `impossible-jackson` targets Jackson 3 (`tools.jackson`), the version Spring Boot 4
  uses, instead of Jackson 2. Register it with
  `JsonMapper.builder().addModule(ImpossibleModule()).build()`. `asJacksonDeserializer`
  and `jacksonSerializer` return Jackson 3's `ValueDeserializer` and `ValueSerializer`,
  and `InvalidValueException` is now a `DatabindException`. Following Jackson 3's
  defaults, unknown fields in money, range and point objects are accepted unless the
  mapper enables `FAIL_ON_UNKNOWN_PROPERTIES`. Spring Boot 3 and other Jackson 2 users
  stay on 0.1.0.

## [0.1.0]

The first release, to Maven Central as `io.liquidsoftware:impossible`,
`io.liquidsoftware:impossible-serialization`, `io.liquidsoftware:impossible-jackson` and
`io.liquidsoftware:impossible-testing`.

### Added

- Validated value types for text, contact details, numbers, money, dates and time
  zones, locales, identifiers, web addresses, network names, geographic points and
  collections, each built through a `ValueFactory` that returns typed errors through
  Arrow's `Raise`.
- Factory combinators (`map`, `mapError`, `andThen`, `ensure`), `optional()` and
  `required()`, and the `validate { }` DSL for accumulating composites.
- Field paths, error messages and RFC 9457 problem details for validation failures.
- `impossible-serialization`: kotlinx.serialization serializers that decode through
  the validating factories.
- `impossible-jackson`: a Jackson 2 module for the JVM with the same wire form, also
  decoding through the validating factories.
- `ValueFactory` entry points without `Raise` (`fromEither`, `fromOrNull`, `fromOrElse`),
  the `ValidationError` marker for boundary mode, and recipes for Ktor, Spring and
  persistence.
- `impossible-testing`: kotest generators for every type and its errors.
- Targets: JVM (Java 17 and later), JS, Wasm (JS), Linux x64, macOS arm64, iOS arm64
  and the iOS arm64 simulator.
