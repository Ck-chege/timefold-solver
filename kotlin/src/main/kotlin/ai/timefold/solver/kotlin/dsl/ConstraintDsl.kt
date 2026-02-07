package ai.timefold.solver.kotlin.dsl

import ai.timefold.solver.core.api.score.Score

/**
 * Top-level entry point for defining constraints using the Kotlin DSL.
 * Analogous to Exposed's `transaction {}` block.
 *
 * Example:
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
 * ```
 */
fun constraints(init: ConstraintSetScope.() -> Unit): ConstraintSetDefinition {
    val scope = ConstraintSetScope()
    scope.init()
    return scope.build()
}

/**
 * The scope inside a `constraints {}` block.
 * Provides the [constraint] function for defining individual constraints.
 */
@TimefoldDsl
class ConstraintSetScope internal constructor() {

    private val constraintDefinitionList: MutableList<ConstraintDefinition> = mutableListOf()

    /**
     * Defines a single named constraint.
     */
    fun constraint(name: String, init: ConstraintScope.() -> Unit) {
        val scope = ConstraintScope(name)
        scope.init()
        constraintDefinitionList.add(scope.build())
    }

    internal fun build(): ConstraintSetDefinition =
        ConstraintSetDefinition(constraintDefinitionList.toList())
}

/**
 * The scope inside a `constraint("name") {}` block.
 * Provides [forEach], [join], [where], [penalize], [reward] functions.
 */
@TimefoldDsl
class ConstraintScope internal constructor(private val name: String) {

    private val forEachList: MutableList<ForEachDeclaration<*>> = mutableListOf()
    private val joinDeclarationList: MutableList<JoinDeclaration> = mutableListOf()
    private val filterList: MutableList<Any> = mutableListOf() // JoinCondition or FilterCondition or lambda
    private var scoringDeclaration: ScoringDeclaration? = null

    /**
     * Declares a source of facts for this constraint.
     * Analogous to Exposed's `FROM table` or Timefold's `forEach(Class)`.
     *
     * @param entity the [ConstraintEntity] to iterate over
     * @param name a descriptive name for this reference (used in error messages)
     * @return an [EntityRef] for use in join conditions and filters
     */
    fun <E : Any> forEach(entity: ConstraintEntity<E>, name: String = entity.entityClass.simpleName): EntityRef<E> {
        val ref = EntityRef(entity, name)
        forEachList.add(ForEachDeclaration(ref))
        return ref
    }

    /**
     * Declares a join between two entity references with typed conditions.
     * Analogous to Exposed's `innerJoin` with `ON` conditions.
     *
     * Example:
     * ```kotlin
     * join(l1, l2) {
     *     l1[Lessons.timeslot] eq l2[Lessons.timeslot]
     *     l1[Lessons.room] eq l2[Lessons.room]
     * }
     * ```
     */
    fun join(
        left: EntityRef<*>,
        right: EntityRef<*>,
        conditions: JoinConditionScope.() -> Unit
    ) {
        val scope = JoinConditionScope()
        scope.conditions()
        joinDeclarationList.add(JoinDeclaration(left, right, scope.conditionList.toList()))
    }

    /**
     * Adds a filter condition to the constraint.
     * Analogous to Exposed's `where {}`.
     *
     * Accepts [JoinCondition] (property-to-property) or [FilterCondition] (property-to-value).
     */
    fun where(condition: () -> Any) {
        filterList.add(condition())
    }

    /**
     * Declares a penalty for each match.
     * Analogous to Timefold's `.penalize(score)`.
     */
    fun <Score_ : Score<Score_>> penalize(constraintWeight: Score_) {
        scoringDeclaration = ScoringDeclaration(ScoringType.PENALIZE, constraintWeight, null)
    }

    /**
     * Declares a penalty with a dynamic weight.
     * Analogous to Timefold's `.penalize(score, matchWeigher)`.
     */
    fun <Score_ : Score<Score_>> penalize(constraintWeight: Score_, matchWeigher: () -> Int) {
        scoringDeclaration = ScoringDeclaration(ScoringType.PENALIZE, constraintWeight, matchWeigher)
    }

    /**
     * Declares a reward for each match.
     * Analogous to Timefold's `.reward(score)`.
     */
    fun <Score_ : Score<Score_>> reward(constraintWeight: Score_) {
        scoringDeclaration = ScoringDeclaration(ScoringType.REWARD, constraintWeight, null)
    }

    /**
     * Declares a reward with a dynamic weight.
     */
    fun <Score_ : Score<Score_>> reward(constraintWeight: Score_, matchWeigher: () -> Int) {
        scoringDeclaration = ScoringDeclaration(ScoringType.REWARD, constraintWeight, matchWeigher)
    }

    internal fun build(): ConstraintDefinition {
        requireNotNull(scoringDeclaration) {
            "The constraint (%s) must have a penalize() or reward() call.".format(name)
        }
        require(forEachList.isNotEmpty()) {
            "The constraint (%s) must have at least one forEach() call.".format(name)
        }
        return ConstraintDefinition(
            name = name,
            forEachDeclarationList = forEachList.toList(),
            joinDeclarationList = joinDeclarationList.toList(),
            filterList = filterList.toList(),
            scoringDeclaration = scoringDeclaration!!
        )
    }
}

/**
 * The scope inside a `join(l1, l2) {}` block.
 * Collects [JoinCondition]s declared via infix operators.
 */
@TimefoldDsl
class JoinConditionScope internal constructor() {

    internal val conditionList: MutableList<JoinCondition> = mutableListOf()

    /** Allows declaring join conditions that are automatically collected. */
    infix fun <T> BoundProperty<*, T>.eq(other: BoundProperty<*, T>): JoinCondition {
        val condition = JoinCondition(this, ComparisonType.EQUAL, other)
        conditionList.add(condition)
        return condition
    }

    infix fun <T : Comparable<T>> BoundProperty<*, T>.lt(other: BoundProperty<*, T>): JoinCondition {
        val condition = JoinCondition(this, ComparisonType.LESS_THAN, other)
        conditionList.add(condition)
        return condition
    }

    infix fun <T : Comparable<T>> BoundProperty<*, T>.lte(other: BoundProperty<*, T>): JoinCondition {
        val condition = JoinCondition(this, ComparisonType.LESS_THAN_OR_EQUAL, other)
        conditionList.add(condition)
        return condition
    }

    infix fun <T : Comparable<T>> BoundProperty<*, T>.gt(other: BoundProperty<*, T>): JoinCondition {
        val condition = JoinCondition(this, ComparisonType.GREATER_THAN, other)
        conditionList.add(condition)
        return condition
    }

    infix fun <T : Comparable<T>> BoundProperty<*, T>.gte(other: BoundProperty<*, T>): JoinCondition {
        val condition = JoinCondition(this, ComparisonType.GREATER_THAN_OR_EQUAL, other)
        conditionList.add(condition)
        return condition
    }
}

// =====================================================================
// DSL marker to prevent scope leaking
// =====================================================================

/** Prevents implicit access to outer receivers in nested DSL blocks. */
@DslMarker
annotation class TimefoldDsl

// =====================================================================
// Internal data classes — the intermediate representation
// =====================================================================

enum class ScoringType { PENALIZE, REWARD }

class ForEachDeclaration<E : Any> internal constructor(
    val entityRef: EntityRef<E>
)

class JoinDeclaration internal constructor(
    val left: EntityRef<*>,
    val right: EntityRef<*>,
    val conditionList: List<JoinCondition>
)

class ScoringDeclaration internal constructor(
    val scoringType: ScoringType,
    val constraintWeight: Score<*>,
    val matchWeigher: (() -> Int)?
)

/**
 * The fully defined intermediate representation of a single constraint.
 * Ready to be compiled into constraints by the [ai.timefold.solver.kotlin.compiler.ConstraintCompiler].
 */
class ConstraintDefinition internal constructor(
    val name: String,
    val forEachDeclarationList: List<ForEachDeclaration<*>>,
    val joinDeclarationList: List<JoinDeclaration>,
    val filterList: List<Any>,
    val scoringDeclaration: ScoringDeclaration
) {
    override fun toString(): String = "ConstraintDefinition($name)"
}

/**
 * A set of constraint definitions, ready for compilation.
 * This is what the top-level [constraints] function returns.
 */
class ConstraintSetDefinition internal constructor(
    val constraintDefinitionList: List<ConstraintDefinition>
) {
    override fun toString(): String =
        "ConstraintSetDefinition(${constraintDefinitionList.size} constraints)"
}
