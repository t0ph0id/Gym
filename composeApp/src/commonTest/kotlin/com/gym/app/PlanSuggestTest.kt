package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanSuggestTest {
    private val day = 86_400_000L
    private val now = 1_795_003_200_000L
    private var ids = 0
    private fun id() = "id${ids++}"

    private fun point(uid: String, exercise: String, sets: Int, daysAgo: Int, workout: String = "w$daysAgo") = ProgressPoint(
        id = "$workout$uid$exercise", workoutId = workout, uid = uid, exercise = exercise,
        exerciseKey = exerciseKey(exercise), at = now - daysAgo * day, sets = sets,
    )

    // Chris trains legs, chest and shoulders; nothing for back, biceps, glutes, triceps or core.
    private val history = listOf(
        point("chris", "Smith squat", 4, 2),
        point("chris", "Leg press", 3, 2),
        point("chris", "Chest press machine", 3, 1),
        point("chris", "Shoulder press machine", 3, 1),
        point("chris", "Triceps pushdown", 2, 1),
        point("chris", "Lat pulldown", 3, 100), // long ago, outside the 8 weeks
        point("tester", "Lat pulldown", 4, 3),
        point("tester", "Dumbbell curl", 3, 3),
    )

    @Test
    fun noHistoryGivesNoSuggestion() {
        assertNull(suggestPlan(emptyList(), listOf("chris"), now))
        assertNull(suggestPlan(history, listOf("nobody"), now))
    }

    @Test
    fun focusesOnTheLeastTrainedMusclesNeverTrainedFirst() {
        val s = suggestPlan(history, listOf("chris"), now, makeId = ::id)!!
        // Glutes, Biceps and Core were never trained; Back was, but long ago, so it comes after them.
        assertEquals(listOf(Muscle.Glutes, Muscle.Biceps, Muscle.Core), s.focus)
        assertEquals(Muscle.Back, s.loads[3].muscle)
        assertEquals(0, s.loads[3].sets)
        assertEquals("Glutes, Biceps & Core", s.plan.name)
        assertEquals(6, s.plan.exercises.size)
        assertEquals(2, s.workouts)
        // Ordered big muscles first: glutes, then biceps, then core.
        val muscles = s.plan.exercises.map { muscleOf(it.name) }
        assertEquals(listOf(Muscle.Glutes, Muscle.Glutes, Muscle.Biceps, Muscle.Biceps, Muscle.Core, Muscle.Core), muscles)
    }

    @Test
    fun picksExercisesYouAlreadyDoAndYourUsualEquipment() {
        val s = suggestPlan(history, listOf("chris"), now, focusCount = 4, makeId = ::id)!!
        val back = s.plan.exercises.filter { muscleOf(it.name) == Muscle.Back }.map { it.name }
        assertEquals("Lat pulldown", back.first()) // done before
        // Chris mostly uses machines, so the other glute exercise is a machine one.
        val glutes = s.plan.exercises.filter { muscleOf(it.name) == Muscle.Glutes }.map { Catalog.find(it.name)!!.equipment }
        assertTrue(Equipment.Machine in glutes)
    }

    @Test
    fun bothPartnersCountTogether() {
        val s = suggestPlan(history, listOf("chris", "tester"), now, makeId = ::id)!!
        // Back and biceps were trained by Tester, so they're no longer the gaps.
        assertTrue(Muscle.Back !in s.focus)
        assertTrue(Muscle.Biceps !in s.focus)
        assertEquals(listOf(Muscle.Glutes, Muscle.Core), s.focus.take(2))
    }

    @Test
    fun usesSetsRepsAndWarmUpGiven() {
        val warm = Circuit(listOf("Elliptical"), minutes = mapOf("elliptical" to 5), health = mapOf("elliptical" to HealthStats(kcal = 47)))
        val s = suggestPlan(history, listOf("chris"), now, sets = 4, reps = 12, warmUp = warm, makeId = ::id)!!
        assertTrue(s.plan.exercises.all { it.sets == 4 && it.reps == 12 })
        assertEquals(5, s.plan.warmUp.minutesFor("Elliptical"))
        assertTrue(s.plan.warmUp.health.isEmpty())
    }

    @Test
    fun otherExercisesRotatesTheChoice() {
        val a = suggestPlan(history, listOf("chris"), now, makeId = ::id)!!.plan.exercises.map { it.name }
        val b = suggestPlan(history, listOf("chris"), now, variant = 1, makeId = ::id)!!.plan.exercises.map { it.name }
        assertNotEquals(a, b)
        assertEquals(6, b.size)
    }

    @Test
    fun ownExercisesCount() {
        val own = listOf(CatalogExercise("Band pull-apart", Muscle.Back, Equipment.Other, custom = true))
        val pts = history + point("chris", "Band pull-apart", 3, 1)
        val loads = muscleLoads(pts, listOf("chris"), now - 56 * day, own.associate { exerciseKey(it.name) to it.muscle })
        assertEquals(3, loads.first { it.muscle == Muscle.Back }.sets)
    }

    @Test
    fun planNames() {
        assertEquals("Back", planName(listOf(Muscle.Back)))
        assertEquals("Back & Core", planName(listOf(Muscle.Back, Muscle.Core)))
    }
}
