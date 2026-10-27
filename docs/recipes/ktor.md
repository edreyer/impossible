# Ktor

> Not compiled: this recipe is checked by reading, not by the build. It targets Ktor 3
> with `ContentNegotiation` and kotlinx.serialization.

The shape: decode the body into a plain DTO of strings and numbers, run it through a
boundary composite with `fromEither`, and turn the failures into RFC 9457 problem
details. Nothing invalid reaches the handler's happy path.

## The request

```kotlin
@Serializable
data class SignupDto(val email: String? = null, val name: String, val country: String, val seats: Int)

data class Signup(val email: Email, val name: NonBlankString, val country: CountryCode, val seats: PositiveInt) {
    companion object : ValueFactory<SignupDto, NonEmptyList<FieldError<ValidationError>>, Signup> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: SignupDto): Signup = validate {
            val email by Email.required().fieldAt(value.email)
            val name by NonBlankString.fieldAt(value.name)
            val country by CountryCode.fieldAt(value.country)
            val seats by PositiveInt.fieldAt(value.seats)
            Signup(email, name, country, seats)
        }
    }
}
```

## Rendering problem details

`ProblemDetails` is plain data, so give it a serializable mirror once:

```kotlin
@Serializable
data class ProblemJson(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<InvalidFieldJson>,
)

@Serializable
data class InvalidFieldJson(val pointer: String, val code: String, val detail: String, val arguments: Map<String, String>)

fun ProblemDetails.toJson() = ProblemJson(
    type, title, status, detail, instance,
    errors.map { InvalidFieldJson(it.pointer, it.code, it.detail, it.arguments) },
)

val problemJson = ContentType.parse("application/problem+json")

suspend fun ApplicationCall.respondProblem(problem: ProblemDetails) =
    respondText(Json.encodeToString(problem.toJson()), problemJson, HttpStatusCode.fromValue(problem.status))
```

## The route

```kotlin
post("/signups") {
    val dto = call.receive<SignupDto>()
    val signup = Signup.fromOrElse(dto) { failures -> return@post call.respondProblem(failures.toProblemDetails()) }
    call.respond(HttpStatusCode.Created, service.register(signup))
}
```

`fromOrElse` is inline, so the lambda can leave the route with `return@post`.

## Values decoded directly

If a DTO declares library types itself (`@Contextual val email: Email` with
`safeDomainSerializersModule`), an invalid value fails during `receive`. Ktor wraps
decoding failures in `BadRequestException`; find the library's `InvalidValueException`
in the cause chain and answer with its codes:

```kotlin
install(StatusPages) {
    exception<BadRequestException> { call, cause ->
        val invalid = generateSequence<Throwable>(cause) { it.cause }.filterIsInstance<InvalidValueException>().firstOrNull()
            ?: return@exception call.respond(HttpStatusCode.BadRequest)
        val problem = ProblemDetails(
            type = "about:blank",
            title = "The request is not valid.",
            status = 422,
            detail = null,
            instance = null,
            errors = invalid.messages.map { ProblemDetails.InvalidField("", it.code, it.text, it.arguments) },
        )
        call.respondProblem(problem)
    }
}
```

`InvalidValueException` knows the type that failed but not the JSON path, so the pointer
is empty. When clients need paths, decode strings into a DTO and validate with a
composite, as above.
