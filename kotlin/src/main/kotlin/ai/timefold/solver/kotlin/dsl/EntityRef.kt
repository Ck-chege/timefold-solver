package ai.timefold.solver.kotlin.dsl

/**
 * A named reference to an entity instance flowing through the constraint network.
 * This is what [ConstraintScope.forEach] returns — a handle that tracks
 * which tuple slot this entity occupies at runtime.
 *
 * The user writes:
 * ```kotlin
 * val lesson = forEach(Lessons)
 * ```
 *
 * And can then use `lesson` in join conditions, filters, and scoring:
 * ```kotlin
 * join(l1, l2) { l1.timeslot eq l2.timeslot }
 * where { l1.room.capacity gt 30 }
 * ```
 *
 * @param E the entity type
 * @param entity the [ConstraintEntity] definition this reference belongs to
 * @param name the user-given name for this reference (for error messages)
 */
class EntityRef<E : Any> internal constructor(
    val entity: ConstraintEntity<E>,
    val name: String
) {

    /** The slot index in the physical tuple, assigned by the [SlotAllocator]. -1 means unassigned. */
    internal var slotIndex: Int = -1

    /** If this entity was packed during auto-packing, the pack group it belongs to. */
    internal var packGroup: PackGroup? = null

    /**
     * Access a typed property on this entity reference.
     * Returns a [BoundProperty] that knows both the entity ref and the property.
     */
    operator fun <T> get(property: Property<E, T>): BoundProperty<E, T> =
        BoundProperty(this, property)

    override fun toString(): String = "$name:${entity.entityClass.simpleName}"
}

/**
 * A property bound to a specific entity reference.
 * This is the operand for infix operators like [eq], [lt], [gt].
 *
 * Created by `entityRef[property]` or `entityRef.propertyName` (via extension properties).
 *
 * @param E the entity type
 * @param T the property value type
 */
class BoundProperty<E : Any, T> internal constructor(
    val entityRef: EntityRef<E>,
    val property: Property<E, T>
) {
    override fun toString(): String = "${entityRef.name}.${property.name}"
}

/**
 * Tracks how entities are packed when the 4-slot limit is exceeded.
 * Internal to the compiler — users never see this.
 */
internal class PackGroup(
    val entityRefList: MutableList<EntityRef<*>> = mutableListOf(),
    val slotIndex: Int = -1
)
