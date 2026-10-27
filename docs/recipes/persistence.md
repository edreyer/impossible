# Persistence

> Not compiled: this recipe is checked by reading, not by the build. It targets Exposed
> 0.6x and Jakarta Persistence 3 (Hibernate 6).

Store the plain value a type renders to, and read it back **through the factory**, never
through `fromTrusted`. A database is input too: a row written by an older version, a
manual fix, or another service can hold text the type rejects. Reading through the
factory turns such a row into a typed error that names the column and the error code,
instead of a value that silently breaks the invariant.

## Exposed: a column type for `Email`

```kotlin
class CorruptColumn(column: String, code: String) : IllegalStateException("Column $column holds an invalid value: $code")

class EmailColumnType : ColumnType<Email>() {
    override fun sqlType(): String = "VARCHAR(320)"

    override fun valueFromDB(value: Any): Email =
        Email.fromOrElse(value as String) { throw CorruptColumn("email", ErrorMessages.describe(it).code) }

    override fun notNullValueToDB(value: Email): Any = value.value
}

fun Table.email(name: String): Column<Email> = registerColumn(name, EmailColumnType())

object Customers : Table("customers") {
    val id = uuid("id")
    val email = email("email")
}
```

The exception message holds the code (`email.invalid_format`), not the stored text,
which may be personal data.

## Exposed: `Money` as two columns

`Money` is an amount in minor units plus an ISO 4217 code, so store exactly that, a
`BIGINT` and a `CHAR(3)`, and rebuild it with `Money.ofMinor` after validating the
currency. Reading a row returns a typed result:

```kotlin
object Invoices : Table("invoices") {
    val id = uuid("id")
    val totalMinor = long("total_minor")
    val totalCurrency = char("total_currency", 3)
}

fun ResultRow.total(): Either<Currency.Error, Money> =
    Currency.fromEither(this[Invoices.totalCurrency]).map { currency -> Money.ofMinor(this[Invoices.totalMinor], currency) }

fun InsertStatement<*>.total(money: Money) {
    this[Invoices.totalMinor] = money.minor
    this[Invoices.totalCurrency] = money.currency.code
}
```

A repository can then decide what a corrupt row means for it: skip and report it, or
fail the request.

## JPA: attribute converters

```kotlin
@Converter(autoApply = true)
class EmailConverter : AttributeConverter<Email, String> {
    override fun convertToDatabaseColumn(attribute: Email?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): Email? = dbData?.let { text ->
        Email.fromOrElse(text) { throw IllegalStateException("Invalid stored email: ${ErrorMessages.describe(it).code}") }
    }
}
```

For `Money`, keep the two columns in an `@Embeddable` of plain fields and expose the
validated value through a method that calls `Currency.fromEither` and `Money.ofMinor`, as
in the Exposed example: a converter maps one column, and `Money` needs two.
