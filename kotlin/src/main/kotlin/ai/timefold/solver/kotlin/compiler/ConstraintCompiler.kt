package ai.timefold.solver.kotlin.compiler

import ai.timefold.solver.core.api.score.Score
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.Joiners
import ai.timefold.solver.core.api.score.stream.bi.BiJoiner
import ai.timefold.solver.kotlin.dsl.ComparisonType
import ai.timefold.solver.kotlin.dsl.ConstraintDefinition
import ai.timefold.solver.kotlin.dsl.ConstraintSetDefinition
import ai.timefold.solver.kotlin.dsl.FilterCondition
import ai.timefold.solver.kotlin.dsl.JoinCondition
import ai.timefold.solver.kotlin.dsl.ScoringType
import java.util.function.Function

/**
 * Compiles a [ConstraintSetDefinition] (DSL intermediate representation) into
 * [Constraint] instances by mapping DSL declarations to Timefold's stream API.
 *
 * The compiler:
 * 1. Assigns slot indices to entity references (position in the tuple)
 * 2. Converts [JoinCondition]s into [BiJoiner]s
 * 3. Converts [FilterCondition]s into stream filter predicates
 * 4. Wires penalize/reward terminal operations
 *
 * Currently supports constraints with 1 or 2 entity sources.
 * Tri/Quad (3-4 entities) and auto-packing (5+) support is planned.
 */
internal class ConstraintCompiler {

    fun compile(
        factory: ConstraintFactory,
        constraintSet: ConstraintSetDefinition,
    ): List<Constraint> {
        return constraintSet.constraintDefinitionList.map { compileConstraint(factory, it) }
    }

    private fun compileConstraint(
        factory: ConstraintFactory,
        definition: ConstraintDefinition,
    ): Constraint {
        val entityRefList = definition.forEachDeclarationList.map { it.entityRef }
        entityRefList.forEachIndexed { index, ref -> ref.slotIndex = index }

        return when (entityRefList.size) {
            1 -> compileUniConstraint(factory, definition)
            2 -> compileBiConstraint(factory, definition)
            else ->
                throw UnsupportedOperationException(
                    "Constraint '${definition.name}' uses ${entityRefList.size} entities; " +
                        "currently only 1 or 2 are supported.",
                )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compileUniConstraint(
        factory: ConstraintFactory,
        definition: ConstraintDefinition,
    ): Constraint {
        val ref = definition.forEachDeclarationList[0].entityRef
        var stream = factory.forEach(ref.entity.entityClass) as
            ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream<Any>

        for (filter in definition.filterList) {
            if (filter is FilterCondition<*>) {
                val fc = filter as FilterCondition<Any>
                stream = stream.filter { entity -> evaluateFilter(fc, entity) }
            }
        }

        val scoring = definition.scoringDeclaration
        val builder = when (scoring.scoringType) {
            ScoringType.PENALIZE ->
                stream.penalize(scoring.constraintWeight as Score<Nothing>)
            ScoringType.REWARD ->
                stream.reward(scoring.constraintWeight as Score<Nothing>)
        }
        return builder.asConstraint(definition.name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun compileBiConstraint(
        factory: ConstraintFactory,
        definition: ConstraintDefinition,
    ): Constraint {
        val leftRef = definition.forEachDeclarationList[0].entityRef
        val rightRef = definition.forEachDeclarationList[1].entityRef

        val biJoinerList = mutableListOf<BiJoiner<Any, Any>>()
        for (joinDecl in definition.joinDeclarationList) {
            for (condition in joinDecl.conditionList) {
                biJoinerList.add(buildBiJoiner(condition))
            }
        }

        var stream = factory.forEach(leftRef.entity.entityClass as Class<Any>)
            .join(
                rightRef.entity.entityClass as Class<Any>,
                *biJoinerList.toTypedArray(),
            )

        for (filter in definition.filterList) {
            if (filter is FilterCondition<*>) {
                val fc = filter as FilterCondition<Any>
                stream = stream.filter { a, b ->
                    val entity = if (fc.boundProperty.entityRef.slotIndex == 0) a else b
                    evaluateFilter(fc, entity)
                }
            }
        }

        val scoring = definition.scoringDeclaration
        val builder = when (scoring.scoringType) {
            ScoringType.PENALIZE ->
                stream.penalize(scoring.constraintWeight as Score<Nothing>)
            ScoringType.REWARD ->
                stream.reward(scoring.constraintWeight as Score<Nothing>)
        }
        return builder.asConstraint(definition.name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildBiJoiner(condition: JoinCondition): BiJoiner<Any, Any> {
        val leftSlot = condition.left.entityRef.slotIndex
        val rightSlot = condition.right.entityRef.slotIndex

        val streamLeftGetter: (Any) -> Any?
        val streamRightGetter: (Any) -> Any?
        val effectiveComparison: ComparisonType

        if (leftSlot == 0 && rightSlot == 1) {
            streamLeftGetter = condition.left.property.getter as (Any) -> Any?
            streamRightGetter = condition.right.property.getter as (Any) -> Any?
            effectiveComparison = condition.comparisonType
        } else if (leftSlot == 1 && rightSlot == 0) {
            streamLeftGetter = condition.right.property.getter as (Any) -> Any?
            streamRightGetter = condition.left.property.getter as (Any) -> Any?
            effectiveComparison = condition.comparisonType.flip()
        } else {
            throw IllegalStateException(
                "Join condition '$condition' references entities in the same slot ($leftSlot). " +
                    "Use a filter instead.",
            )
        }

        val leftMapping = Function<Any, Any?> { streamLeftGetter(it) }
        val rightMapping = Function<Any, Any?> { streamRightGetter(it) }

        return when (effectiveComparison) {
            ComparisonType.EQUAL ->
                Joiners.equal(leftMapping, rightMapping) as BiJoiner<Any, Any>
            ComparisonType.LESS_THAN ->
                Joiners.lessThan(
                    leftMapping as Function<Any, Comparable<Any>>,
                    rightMapping as Function<Any, Comparable<Any>>,
                ) as BiJoiner<Any, Any>
            ComparisonType.LESS_THAN_OR_EQUAL ->
                Joiners.lessThanOrEqual(
                    leftMapping as Function<Any, Comparable<Any>>,
                    rightMapping as Function<Any, Comparable<Any>>,
                ) as BiJoiner<Any, Any>
            ComparisonType.GREATER_THAN ->
                Joiners.greaterThan(
                    leftMapping as Function<Any, Comparable<Any>>,
                    rightMapping as Function<Any, Comparable<Any>>,
                ) as BiJoiner<Any, Any>
            ComparisonType.GREATER_THAN_OR_EQUAL ->
                Joiners.greaterThanOrEqual(
                    leftMapping as Function<Any, Comparable<Any>>,
                    rightMapping as Function<Any, Comparable<Any>>,
                ) as BiJoiner<Any, Any>
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> evaluateFilter(condition: FilterCondition<T>, entity: Any): Boolean {
        val value = (condition.boundProperty.property.getter as (Any) -> T)(entity)
        val target = condition.value
        return when (condition.comparisonType) {
            ComparisonType.EQUAL -> value == target
            ComparisonType.LESS_THAN -> (value as Comparable<T>).compareTo(target) < 0
            ComparisonType.LESS_THAN_OR_EQUAL -> (value as Comparable<T>).compareTo(target) <= 0
            ComparisonType.GREATER_THAN -> (value as Comparable<T>).compareTo(target) > 0
            ComparisonType.GREATER_THAN_OR_EQUAL -> (value as Comparable<T>).compareTo(target) >= 0
        }
    }
}
