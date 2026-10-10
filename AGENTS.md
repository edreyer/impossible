# safe-domain

Build a small Kotlin library of safe, commonly useful business value types.
Shared types enforce explicit, reusable invariants; consuming applications own
their domain names, policies, authorization decisions, and workflows.

## Domain rules

- Prefer immutable domain data and explicit state transitions.
- Prefer value objects and sealed hierarchies over primitive strings, flags,
  and combinations of nullable fields.
- Use explicit algebraic data types and typed outcomes when absence or failure
  has domain or application meaning. Reserve nullable values for genuinely
  optional data, interop or framework contracts, and private implementation
  details where no meaningful state distinction is lost.
- Treat nullable types as a last resort in state-machine plumbing. Do not use
  `null` to encode control flow when explicit states express the meaning.
- Opt into optionality explicitly with `Factory.optional()` (null stays null,
  everything else is fully validated, blank is never treated as absent). Turn
  nullable boundary input into required domain values with `Factory.required()`
  and a typed missing error, never `!!` or a silent default. When absence has
  meaning, or at least one of several values must be present, use a sealed type
  or Arrow's `Ior` instead of nullable fields.
- Make illegal states unrepresentable where practical. Validate untrusted
  input at boundaries, then construct types that enforce their invariants.
- Use typed results for expected validation, authorization, capability, and
  operation outcomes. Do not use exceptions for ordinary domain decisions.
- Reserve exceptions for unexpected failures, programmer errors, unrecoverable
  conditions, and framework contracts that require them. Translate infrastructure
  and framework failures into appropriate errors at system boundaries.
- Validate model-produced structured output deterministically through the same
  construction rules as other untrusted input.
- Handle closed domain states and failures exhaustively.

## Arrow and typed failures

- Prefer named `Raise<Error>` context parameters for functions that compose
  recoverable failures. Return the success type directly:

  ```kotlin
  context(raise: Raise<PositiveInt.Error>)
  fun quantity(raw: Int): PositiveInt = PositiveInt.from(raw)
  ```

- Materialize `Either` with `either { ... }` where callers need to inspect,
  retain, or pass a result as a value.
- Prefer Arrow's idiomatic `ensure`, `raise`, `bind`, and `getOrElse` operations
  when they make control flow clearer. With a named context parameter, use
  `raise.ensure(...)` or `raise.raise(...)` as appropriate.
- Preserve precise error types. Do not introduce artificial shared error
  hierarchies, widen errors to `Any`, or add functional abstractions solely to
  avoid a small explicit `when`.
- Keep explicit result types required by ports, framework contracts, or
  meaningful domain outcomes. Do not replace closed domain outcomes for style.
- At application boundaries, consume typed results explicitly and translate
  errors into the boundary's closed outcome model where applicable.
- Compose validated types through `ValueFactory<Input, Error, Value>` when the
  factory contract fits. Use `from(value, onError = ...)` to map failures into
  the constructing type's own precise error model.
- Build accumulating composites with `validate { }` and named `by` fields
  (`field`, `flatField`, `eachField`, and `field { }` for dependent steps)
  rather than `zipOrAccumulate` or direct use of Arrow's `accumulate`. Read
  field values only in the final construction or inside a dependent
  `field { }` block. Keep Arrow's experimental accumulate opt-in confined to
  `Validation.kt`.
- Translate failures at meaningful domain boundaries. Higher layers should
  handle their own closed outcomes and retain lower-level failures as details,
  rather than enumerate a growing library-wide error hierarchy. Reuse an error
  model within a boundary instead of adding a wrapper for every nested value.
- Apply these defaults to new and touched code; do not perform unrelated
  repository-wide conversions.

## Package layout

- Shared construction contracts (`ValueFactory`, the `validate` DSL) live in
  the root package `io.liquidsoftware.safedomain`.
- Each value type lives in the domain subpackage it belongs to: `text`,
  `contact`, `numeric`, `money`, `time`, `locale`, `identity`, `web`, `geo`,
  or `collections`.
  Add a new subpackage only when a type fits none of them.
- Keep `internal` helpers in the package of the types that use them.
- Mirror the main package in tests. Cross-cutting composition and factory tests
  stay in the root test package.

## Value type design

- Keep constructors private when construction requires validation. Every public
  factory and future serialization adapter must preserve the same invariant.
- Prefer value classes for simple wrappers where they fit. Avoid construction,
  copying, or mutation paths that bypass validation.
- Document the exact invariant, accepted boundaries, normalization, and equality
  semantics. Do not silently trim, normalize, clamp, or repair input.
- Keep errors specific to each type. Add distinctions only when they carry
  meaning for callers. Redact raw personal data (such as email addresses and
  phone numbers) from error `toString` output.
- Treat programmer configuration (bounds, ranges, literals) differently from
  untrusted input: take plain Kotlin values such as `IntRange` and `require`
  validity, and use `fromTrusted` only for literals and already validated data.
- Do not model "past" or "future" as stored value types; check relationships to
  the current time through a `Clock` context parameter and store the plain value.
- Sign refinements come in mirrored pairs: every `PositiveX` has a `NegativeX`
  with negation between them. A `NonNegativeX` exists only where zero is a common
  valid value (`Decimal`, `Money`); integers use `BoundedInt` instead. There are no
  `NonPositive` types.
- Keep operations invariant-preserving. If arithmetic is introduced, handle
  overflow and invalid results explicitly through precise typed failures.
- Do not duplicate Arrow's result types or build a generic validation framework.
- The library is Kotlin Multiplatform. Common code (`src/commonMain`) must not
  use JDK types: use the Kotlin standard library (for example `kotlin.uuid.Uuid`),
  kotlinx-datetime, or the library's own types (`Decimal`, `RoundingMode`).
  Validation must behave identically on every target. JVM-only conversions to
  JDK types belong in `src/jvmMain`. Do not add a dependency without
  confirmation.
- Do not add serialization, framework integrations, arithmetic APIs, or new
  types speculatively.

## Delivery and verification

- Prefer the smallest change that satisfies the request. Preserve unrelated
  user changes and existing architectural boundaries.
- State material assumptions and uncertainties. Ask when a decision changes
  scope, product behavior, or architecture; otherwise complete the authorized
  slice through implementation, relevant verification, and diff review.
- Before installing machine-wide tools or adding or upgrading dependencies,
  obtain explicit user confirmation unless already authorized. Restoring
  declared, pinned dependencies for a build or test does not need confirmation.
- Test actual contracts: invalid inputs, valid boundaries, exact preservation,
  value semantics, and typed failure composition. Keep tests deterministic.
- Use `./gradlew allTests` for tests (or `jvmTest` and `jsNodeTest` for a fast
  check) and `./gradlew build` for the complete build. Apple targets need macOS.
  Report failures and checks that could not run; do not claim unverified success.
- Keep the README and API documentation aligned with the implementation.
- Use Context7 for external API behavior or version-specific guidance. Resolve
  the library ID first, query one concept at a time, and reuse verified results.
  If coverage is missing or mismatched, consult official documentation or
  installed sources and disclose remaining uncertainty. Do not use Context7
  for repository behavior or general domain logic established locally.
- When Serena is available, activate this project and read its instructions
  once per session before semantic work. Prefer symbolic navigation for code
  and shell tools for configuration, documentation, and checks. Do not modify
  Serena memories unless explicitly requested.
