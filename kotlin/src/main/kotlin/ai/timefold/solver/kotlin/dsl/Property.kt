package ai.timefold.solver.kotlin.dsl

import kotlin.reflect.KProperty1

/**
 * A type-safe reference to a property of a planning entity or problem fact.
 * Analogous to Exposed's `Column<T>` — provides compile-time type safety
 * for join conditions, filters, and scoring expressions.
 *
 * @param E the entity type this property belongs to
 * @param T the property value type
 * @param getter the function that extracts this property's value from an entity instance
 */
class Property<E : Any, T> internal constructor(
    val name: String,
    val getter: (E) -> T
) {
    override fun toString(): String = name
}

/**
 * Creates a [Property] from a Kotlin property reference.
 * Used inside [ConstraintEntity] definitions to declare typed property accessors.
 *
 * Example:
 * ```kotlin
 * object Lessons : ConstraintEntity<Lesson>() {
 *     val timeslot = prop(Lesson::timeslot)
 *     val room = prop(Lesson::room)
 * }
 * ```
 */
fun <E : Any, T> prop(property: KProperty1<E, T>): Property<E, T> =
    Property(property.name, property::get)
