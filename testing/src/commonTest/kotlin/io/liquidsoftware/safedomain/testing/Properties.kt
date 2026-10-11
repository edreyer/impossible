package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.checkAll

/**
 * A fixed seed keeps every run deterministic, as the repository's test rules require.
 * To explore further, change it locally or raise [ITERATIONS]; any failure kotest
 * reports is reproducible with the seed it prints.
 */
private const val SEED = 20_261_011L

private const val ITERATIONS = 300

/** Checks [property] for [ITERATIONS] values of [arb], from a fixed seed. */
// PropTestConfig's constructor exposes experimental parameters; none are used here.
@OptIn(ExperimentalKotest::class)
internal suspend fun <A> forAll(
    arb: Arb<A>,
    iterations: Int = ITERATIONS,
    property: suspend PropertyContext.(A) -> Unit,
) {
    checkAll(PropTestConfig(seed = SEED, iterations = iterations), arb, property)
}

/** Runs a construction and captures its typed failure. */
internal inline fun <Error, A> attempt(block: Raise<Error>.() -> A): Either<Error, A> = either(block)
