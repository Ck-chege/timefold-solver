package ai.timefold.solver.kotlin.dsl

/**
 * The type of comparison in a join or filter condition.
 * Maps directly to Bavet's [ai.timefold.solver.core.impl.score.stream.common.bi.DefaultBiJoiner] types.
 */
enum class ComparisonType {
    EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL;

    fun flip(): ComparisonType = when (this) {
        EQUAL -> EQUAL
        LESS_THAN -> GREATER_THAN
        LESS_THAN_OR_EQUAL -> GREATER_THAN_OR_EQUAL
        GREATER_THAN -> LESS_THAN
        GREATER_THAN_OR_EQUAL -> LESS_THAN_OR_EQUAL
    }
}

/**
 * A single join condition between two bound properties.
 * Created by infix operators like [eq], [lt], [gt].
 *
 * Example: `l1[Lessons.timeslot] eq l2[Lessons.timeslot]`
 * produces a JoinCondition(l1.timeslot, EQUAL, l2.timeslot)
 */
class JoinCondition internal constructor(
    val left: BoundProperty<*, *>,
    val comparisonType: ComparisonType,
    val right: BoundProperty<*, *>
) {
    override fun toString(): String = "$left ${comparisonType.name} $right"
}

/**
 * A filter condition comparing a bound property to a literal value.
 * Created by infix operators like [eq], [lt], [gt] with a constant right-hand side.
 *
 * Example: `room[Rooms.capacity] gt 30`
 */
class FilterCondition<T> internal constructor(
    val boundProperty: BoundProperty<*, T>,
    val comparisonType: ComparisonType,
    val value: T
) {
    override fun toString(): String = "$boundProperty ${comparisonType.name} $value"
}

// =====================================================================
// Infix operators: property-to-property (for joins)
// =====================================================================

/** Equal join condition. Analogous to Exposed's `eq`. */
infix fun <T> BoundProperty<*, T>.eq(other: BoundProperty<*, T>): JoinCondition =
    JoinCondition(this, ComparisonType.EQUAL, other)

/** Less-than join condition. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.lt(other: BoundProperty<*, T>): JoinCondition =
    JoinCondition(this, ComparisonType.LESS_THAN, other)

/** Less-than-or-equal join condition. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.lte(other: BoundProperty<*, T>): JoinCondition =
    JoinCondition(this, ComparisonType.LESS_THAN_OR_EQUAL, other)

/** Greater-than join condition. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.gt(other: BoundProperty<*, T>): JoinCondition =
    JoinCondition(this, ComparisonType.GREATER_THAN, other)

/** Greater-than-or-equal join condition. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.gte(other: BoundProperty<*, T>): JoinCondition =
    JoinCondition(this, ComparisonType.GREATER_THAN_OR_EQUAL, other)

// =====================================================================
// Infix operators: property-to-value (for filters)
// =====================================================================

/** Equal filter condition against a literal value. */
infix fun <T> BoundProperty<*, T>.eq(value: T): FilterCondition<T> =
    FilterCondition(this, ComparisonType.EQUAL, value)

/** Less-than filter condition against a literal value. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.lt(value: T): FilterCondition<T> =
    FilterCondition(this, ComparisonType.LESS_THAN, value)

/** Less-than-or-equal filter condition against a literal value. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.lte(value: T): FilterCondition<T> =
    FilterCondition(this, ComparisonType.LESS_THAN_OR_EQUAL, value)

/** Greater-than filter condition against a literal value. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.gt(value: T): FilterCondition<T> =
    FilterCondition(this, ComparisonType.GREATER_THAN, value)

/** Greater-than-or-equal filter condition against a literal value. */
infix fun <T : Comparable<T>> BoundProperty<*, T>.gte(value: T): FilterCondition<T> =
    FilterCondition(this, ComparisonType.GREATER_THAN_OR_EQUAL, value)
