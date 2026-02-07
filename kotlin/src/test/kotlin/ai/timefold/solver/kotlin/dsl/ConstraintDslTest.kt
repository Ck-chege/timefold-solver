package ai.timefold.solver.kotlin.dsl

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConstraintDslTest {

    // Simple domain model for testing
    data class Lesson(
        val id: Long,
        val timeslot: String,
        val room: String,
        val teacher: String,
    )

    object Lessons : ConstraintEntity<Lesson>(Lesson::class.java) {
        val id = prop(Lesson::id)
        val timeslot = prop(Lesson::timeslot)
        val room = prop(Lesson::room)
        val teacher = prop(Lesson::teacher)
    }

    @Test
    fun singleEntityConstraint() {
        val definition = constraints {
            constraint("Single entity") {
                forEach(Lessons)
                penalize(SimpleScore.ONE)
            }
        }

        assertThat(definition.constraintDefinitionList).hasSize(1)
        val constraint = definition.constraintDefinitionList[0]
        assertThat(constraint.name).isEqualTo("Single entity")
        assertThat(constraint.forEachDeclarationList).hasSize(1)
        assertThat(constraint.forEachDeclarationList[0].entityRef.entity).isSameAs(Lessons)
        assertThat(constraint.joinDeclarationList).isEmpty()
        assertThat(constraint.scoringDeclaration.scoringType).isEqualTo(ScoringType.PENALIZE)
    }

    @Test
    fun twoEntityJoinConstraint() {
        val definition = constraints {
            constraint("Room conflict") {
                val l1 = forEach(Lessons, "l1")
                val l2 = forEach(Lessons, "l2")
                join(l1, l2) {
                    l1[Lessons.timeslot] eq l2[Lessons.timeslot]
                    l1[Lessons.room] eq l2[Lessons.room]
                    l1[Lessons.id] lt l2[Lessons.id]
                }
                penalize(HardSoftScore.ONE_HARD)
            }
        }

        assertThat(definition.constraintDefinitionList).hasSize(1)
        val constraint = definition.constraintDefinitionList[0]
        assertThat(constraint.name).isEqualTo("Room conflict")
        assertThat(constraint.forEachDeclarationList).hasSize(2)
        assertThat(constraint.joinDeclarationList).hasSize(1)

        val joinConditionList = constraint.joinDeclarationList[0].conditionList
        assertThat(joinConditionList).hasSize(3)
        assertThat(joinConditionList[0].comparisonType).isEqualTo(ComparisonType.EQUAL)
        assertThat(joinConditionList[1].comparisonType).isEqualTo(ComparisonType.EQUAL)
        assertThat(joinConditionList[2].comparisonType).isEqualTo(ComparisonType.LESS_THAN)

        assertThat(constraint.scoringDeclaration.scoringType).isEqualTo(ScoringType.PENALIZE)
        assertThat(constraint.scoringDeclaration.constraintWeight).isEqualTo(HardSoftScore.ONE_HARD)
    }

    @Test
    fun rewardConstraint() {
        val definition = constraints {
            constraint("Preferred room") {
                forEach(Lessons)
                reward(HardSoftScore.ONE_SOFT)
            }
        }

        val constraint = definition.constraintDefinitionList[0]
        assertThat(constraint.scoringDeclaration.scoringType).isEqualTo(ScoringType.REWARD)
        assertThat(constraint.scoringDeclaration.constraintWeight).isEqualTo(HardSoftScore.ONE_SOFT)
    }

    @Test
    fun multipleConstraints() {
        val definition = constraints {
            constraint("First") {
                forEach(Lessons)
                penalize(SimpleScore.ONE)
            }
            constraint("Second") {
                forEach(Lessons)
                reward(SimpleScore.ONE)
            }
        }

        assertThat(definition.constraintDefinitionList).hasSize(2)
        assertThat(definition.constraintDefinitionList[0].name).isEqualTo("First")
        assertThat(definition.constraintDefinitionList[1].name).isEqualTo("Second")
    }

    @Test
    fun constraintWithoutScoringFails() {
        assertThatThrownBy {
            constraints {
                constraint("No scoring") {
                    forEach(Lessons)
                }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("penalize() or reward()")
    }

    @Test
    fun constraintWithoutForEachFails() {
        assertThatThrownBy {
            constraints {
                constraint("No forEach") {
                    penalize(SimpleScore.ONE)
                }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("forEach()")
    }

    @Test
    fun entityRefPropertyAccess() {
        val lesson = Lesson(1L, "Monday 8:00", "Room A", "Teacher X")
        val timeslotProp = Lessons.timeslot

        assertThat(timeslotProp.name).isEqualTo("timeslot")
        assertThat(timeslotProp.getter(lesson)).isEqualTo("Monday 8:00")
    }

    @Test
    fun boundPropertyCreation() {
        val definition = constraints {
            constraint("Test") {
                val l = forEach(Lessons, "lesson")
                penalize(SimpleScore.ONE)
            }
        }

        val ref = definition.constraintDefinitionList[0].forEachDeclarationList[0].entityRef
        val bound = ref[Lessons.timeslot]
        assertThat(bound.entityRef).isSameAs(ref)
        assertThat(bound.property).isSameAs(Lessons.timeslot)
    }

    @Test
    fun comparisonTypeFlip() {
        assertThat(ComparisonType.EQUAL.flip()).isEqualTo(ComparisonType.EQUAL)
        assertThat(ComparisonType.LESS_THAN.flip()).isEqualTo(ComparisonType.GREATER_THAN)
        assertThat(ComparisonType.LESS_THAN_OR_EQUAL.flip()).isEqualTo(ComparisonType.GREATER_THAN_OR_EQUAL)
        assertThat(ComparisonType.GREATER_THAN.flip()).isEqualTo(ComparisonType.LESS_THAN)
        assertThat(ComparisonType.GREATER_THAN_OR_EQUAL.flip()).isEqualTo(ComparisonType.LESS_THAN_OR_EQUAL)
    }

    @Test
    fun filterConditionCreation() {
        val definition = constraints {
            constraint("Filter test") {
                val l = forEach(Lessons)
                where { l[Lessons.teacher] eq "Teacher X" }
                penalize(SimpleScore.ONE)
            }
        }

        val constraint = definition.constraintDefinitionList[0]
        assertThat(constraint.filterList).hasSize(1)
        val filter = constraint.filterList[0]
        assertThat(filter).isInstanceOf(FilterCondition::class.java)
    }

    @Test
    fun joinConditionInfixOperators() {
        val definition = constraints {
            constraint("All operators") {
                val l1 = forEach(Lessons, "l1")
                val l2 = forEach(Lessons, "l2")
                join(l1, l2) {
                    l1[Lessons.timeslot] eq l2[Lessons.timeslot]
                    l1[Lessons.id] lt l2[Lessons.id]
                    l1[Lessons.id] lte l2[Lessons.id]
                    l1[Lessons.id] gt l2[Lessons.id]
                    l1[Lessons.id] gte l2[Lessons.id]
                }
                penalize(SimpleScore.ONE)
            }
        }

        val conditionList = definition.constraintDefinitionList[0].joinDeclarationList[0].conditionList
        assertThat(conditionList).hasSize(5)
        assertThat(conditionList[0].comparisonType).isEqualTo(ComparisonType.EQUAL)
        assertThat(conditionList[1].comparisonType).isEqualTo(ComparisonType.LESS_THAN)
        assertThat(conditionList[2].comparisonType).isEqualTo(ComparisonType.LESS_THAN_OR_EQUAL)
        assertThat(conditionList[3].comparisonType).isEqualTo(ComparisonType.GREATER_THAN)
        assertThat(conditionList[4].comparisonType).isEqualTo(ComparisonType.GREATER_THAN_OR_EQUAL)
    }
}
