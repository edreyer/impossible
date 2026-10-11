package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CollectionCompositionTest {
    @Test
    fun `parent collects every indexed element error and independent collection failures`() {
        val result: Either<NonEmptyList<DispatchPlan.Error>, DispatchPlan> = either {
            DispatchPlan.from(
                DispatchInput(
                    recipients = listOf("", "Alice", " "),
                    labels = listOf("urgent", "urgent", "urgent"),
                    attachments = emptyList(),
                    channels = emptyList(),
                ),
            )
        }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    DispatchPlan.Error.InvalidRecipient(0, NonBlankString.Error.Blank),
                    DispatchPlan.Error.InvalidRecipient(2, NonBlankString.Error.Blank),
                    DispatchPlan.Error.InvalidLabels(
                        nonEmptyListOf(
                            UniqueList.Error.Duplicate(0, 1),
                            UniqueList.Error.Duplicate(0, 2),
                        ),
                    ),
                    DispatchPlan.Error.InvalidAttachments(BoundedList.Error.TooSmall(1, 0)),
                    DispatchPlan.Error.InvalidChannels(NonEmptyError.Empty),
                ),
            ),
            result,
        )
    }

    @Test
    fun `empty recipients produce a collection error rather than an empty element error batch`() {
        val result: Either<NonEmptyList<DispatchPlan.Error>, DispatchPlan> = either {
            DispatchPlan.from(DispatchInput(emptyList(), emptyList(), listOf("a.pdf"), listOf("email")))
        }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    DispatchPlan.Error.InvalidRecipients(NonEmptyError.Empty),
                ),
            ),
            result,
        )
    }

    @Test
    fun `valid composite preserves typed elements order and each collection's semantics`() {
        val result = either {
            DispatchPlan.from(
                DispatchInput(
                    recipients = listOf(" Alice ", "Bob"),
                    labels = listOf("urgent", "private"),
                    attachments = listOf("a.pdf", "a.pdf"),
                    channels = listOf("email", "sms", "email"),
                ),
            )
        }
        val plan = assertIs<Either.Right<DispatchPlan>>(result).value
        assertEquals(listOf(" Alice ", "Bob"), plan.recipients.map { it.value }.all)
        assertEquals(listOf("urgent", "private"), plan.labels.value)
        assertEquals(listOf("a.pdf", "a.pdf"), plan.attachments.value)
        assertEquals(listOf("email", "sms"), plan.channels.toList())
    }
}

private data class DispatchInput(
    val recipients: List<String>,
    val labels: List<String>,
    val attachments: List<String>,
    val channels: List<String>,
)

private class DispatchPlan private constructor(
    val recipients: NonEmptyList<NonBlankString>,
    val labels: UniqueList<String>,
    val attachments: BoundedList<String>,
    val channels: NonEmptySet<String>,
) {
    sealed interface Error {
        data class InvalidRecipient(val index: Int, val cause: NonBlankString.Error) : Error
        data class InvalidRecipients(val cause: NonEmptyError) : Error
        data class InvalidLabels(val causes: NonEmptyList<UniqueList.Error>) : Error
        data class InvalidAttachments(val cause: BoundedList.Error) : Error
        data class InvalidChannels(val cause: NonEmptyError) : Error
    }

    companion object : ValueFactory<DispatchInput, NonEmptyList<Error>, DispatchPlan> {
        private val recipients = NonEmptyList.factory<NonBlankString>()
        private val labels = UniqueList.factory<String>()
        private val channels = NonEmptySet.factory<String>()
        private val attachments = BoundedList.factory<String>(1..2)

        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: DispatchInput): DispatchPlan = validate {
            val names by field {
                val elements by NonBlankString.eachField(value.recipients, Error::InvalidRecipient)
                recipients.from(elements, onError = Error::InvalidRecipients)
            }
            val tags by labels.field(value.labels, Error::InvalidLabels)
            val files by attachments.field(value.attachments, Error::InvalidAttachments)
            val modes by channels.field(value.channels, Error::InvalidChannels)
            DispatchPlan(names, tags, files, modes)
        }
    }
}
