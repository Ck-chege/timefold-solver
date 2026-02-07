package ai.timefold.solver.kotlin.dsl

import kotlin.reflect.KProperty1

/**
 * Base class for declaring planning entity or problem fact schemas.
 * Analogous to Exposed's `Table` — each subclass is an `object` singleton
 * that declares typed [Property] references.
 *
 * Example:
 * ```kotlin
 * object Lessons : ConstraintEntity<Lesson>(Lesson::class) {
 *     val timeslot = prop(Lesson::timeslot)
 *     val room = prop(Lesson::room)
 *     val teacher = prop(Lesson::teacher)
 * }
 * ```
 *
 * @param E the entity or fact type
 * @param entityClass the Java class of the entity
 */
abstract class ConstraintEntity<E : Any>(val entityClass: Class<E>) {

    private val propertyList: MutableList<Property<E, *>> = mutableListOf()

    /**
     * Declares a typed property reference on this entity.
     * Analogous to Exposed's `integer("id")` or `varchar("name", 50)`.
     */
    protected fun <T> prop(property: KProperty1<E, T>): Property<E, T> {
        val p = Property(property.name, property::get)
        propertyList.add(p)
        return p
    }

    /**
     * Returns all declared properties on this entity.
     */
    fun properties(): List<Property<E, *>> = propertyList.toList()

    override fun toString(): String = entityClass.simpleName
}

/**
 * Convenience constructor using reified type parameter.
 *
 * Example:
 * ```kotlin
 * object Lessons : ConstraintEntity<Lesson>(Lesson::class)
 * ```
 */
inline fun <reified E : Any> constraintEntity(): Class<E> = E::class.java
