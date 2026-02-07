package ai.timefold.solver.kotlin

import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.kotlin.compiler.ConstraintCompiler
import ai.timefold.solver.kotlin.dsl.ConstraintSetDefinition

/**
 * Bridges the Kotlin DSL [ConstraintSetDefinition] to Timefold's [ConstraintProvider] interface.
 *
 * Usage:
 * ```kotlin
 * val myConstraints = constraints {
 *     constraint("Room conflict") {
 *         val l1 = forEach(Lessons, "l1")
 *         val l2 = forEach(Lessons, "l2")
 *         join(l1, l2) {
 *             l1[Lessons.timeslot] eq l2[Lessons.timeslot]
 *             l1[Lessons.room] eq l2[Lessons.room]
 *             l1[Lessons.id] lt l2[Lessons.id]
 *         }
 *         penalize(HardSoftScore.ONE_HARD)
 *     }
 * }
 *
 * val provider = myConstraints.toConstraintProvider()
 * ```
 */
class KotlinConstraintProvider(
    private val constraintSet: ConstraintSetDefinition,
) : ConstraintProvider {

    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return ConstraintCompiler().compile(constraintFactory, constraintSet).toTypedArray()
    }
}

/**
 * Converts this [ConstraintSetDefinition] into a [ConstraintProvider]
 * that can be plugged into the Timefold Solver configuration.
 */
fun ConstraintSetDefinition.toConstraintProvider(): ConstraintProvider =
    KotlinConstraintProvider(this)
