# Recipes

Short, copyable answers to "how do I use this with …". The tutorial teaches the library
from the ground up; these start from the framework you already run.

| Recipe | What it shows | Compiled |
| --- | --- | --- |
| [Normalize, then parse](normalize-then-parse.md) | Accepting friendly input (spaced phone numbers, padded emails) without the types ever normalizing | Yes, checked by `TutorialSnippetsTest` |
| [Ktor](ktor.md) | Validating a request body and answering with `application/problem+json` | No |
| [Spring](spring.md) | A `@RestControllerAdvice` that renders `ProblemDetails`, and the Jackson module | No |
| [Persistence](persistence.md) | Exposed column types and JPA converters that read through the factories | No |

Recipes marked "No" are written against the frameworks' public APIs as of late 2026
(Ktor 3, Spring Boot 4 with Jackson 3, Exposed 0.6x, Jakarta Persistence 3) but are not
built in this repository; check them against the versions you use.
