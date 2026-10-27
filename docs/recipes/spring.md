# Spring

> Not compiled: this recipe is checked by reading, not by the build. It targets Spring
> Boot 3 (Spring Framework 6, Jackson 2).

## Validate in the controller

Accept a DTO of plain values, then build the domain value with a boundary composite
(see the [Ktor recipe](ktor.md) for a `Signup` built that way). `fromOrElse` is inline,
so its lambda can return from the handler:

```kotlin
@PostMapping("/signups")
fun signup(@RequestBody dto: SignupDto): ResponseEntity<Any> {
    val signup = Signup.fromOrElse(dto) { failures -> return problem(failures.toProblemDetails()) }
    return ResponseEntity.status(HttpStatus.CREATED).body(service.register(signup))
}
```

## Render `ProblemDetails` with Spring's own `ProblemDetail`

Spring already writes `org.springframework.http.ProblemDetail` as
`application/problem+json`. Copy the library's data into it, keeping the `errors`
extension:

```kotlin
fun problem(details: ProblemDetails): ResponseEntity<Any> {
    val problem = ProblemDetail.forStatus(details.status).apply {
        type = URI.create(details.type)
        title = details.title
        detail = details.detail
        details.instance?.let { instance = URI.create(it) }
        setProperty(
            "errors",
            details.errors.map { mapOf("pointer" to it.pointer, "code" to it.code, "detail" to it.detail, "arguments" to it.arguments) },
        )
    }
    return ResponseEntity.status(details.status).body(problem)
}
```

The same function serves a `@RestControllerAdvice` when validation runs deeper, for
example in a service that raises a domain exception carrying the failures:

```kotlin
class InvalidRequest(val failures: NonEmptyList<FieldError<ValidationError>>) : RuntimeException("Invalid request")

@RestControllerAdvice
class ProblemAdvice {
    @ExceptionHandler(InvalidRequest::class)
    fun invalid(exception: InvalidRequest): ResponseEntity<Any> = problem(exception.failures.toProblemDetails())
}
```

## Library types in request and response bodies

The `impossible-jackson` module reads and writes every library type with the same JSON
as `impossible-serialization`, and decoding runs the real factory. Spring Boot
registers every Jackson `Module` bean with its `ObjectMapper`:

```kotlin
@Configuration
class JacksonConfig {
    @Bean
    fun impossibleModule(): Module = ImpossibleModule()
}
```

An invalid value then fails while Spring reads the body, as an
`HttpMessageNotReadableException` whose cause chain holds the module's
`InvalidValueException` with the error codes (never the rejected input):

```kotlin
@ExceptionHandler(HttpMessageNotReadableException::class)
fun unreadable(exception: HttpMessageNotReadableException): ResponseEntity<Any> {
    val invalid = generateSequence<Throwable>(exception) { it.cause }.filterIsInstance<InvalidValueException>().firstOrNull()
        ?: return ResponseEntity.badRequest().build()
    val fields = invalid.messages.map { ProblemDetails.InvalidField(invalid.pointer, it.code, it.text, it.arguments) }
    return problem(ProblemDetails("about:blank", "The request is not valid.", 422, null, null, fields))
}
```

Jackson knows where it was when the value failed, so `InvalidValueException.pointer`
is the JSON Pointer of the field, such as `/email`.
