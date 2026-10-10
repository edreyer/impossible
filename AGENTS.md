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
- Translate failures at meaningful domain boundaries. Higher layers should
  handle their own closed outcomes and retain lower-level failures as details,
  rather than enumerate a growing library-wide error hierarchy. Reuse an error
  model within a boundary instead of adding a wrapper for every nested value.
- Apply these defaults to new and touched code; do not perform unrelated
  repository-wide conversions.

## Value type design

- Keep constructors private when construction requires validation. Every public
  factory and future serialization adapter must preserve the same invariant.
- Prefer value classes for simple wrappers where they fit. Avoid construction,
  copying, or mutation paths that bypass validation.
- Document the exact invariant, accepted boundaries, normalization, and equality
  semantics. Do not silently trim, normalize, clamp, or repair input.
- Keep errors specific to each type. Add distinctions only when they carry
  meaning for callers.
- Keep operations invariant-preserving. If arithmetic is introduced, handle
  overflow and invalid results explicitly through precise typed failures.
- Do not duplicate Arrow's result types or build a generic validation framework.
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
- Use `./gradlew test` for tests and `./gradlew build` for the complete build.
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
