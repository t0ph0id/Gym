package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GripTest {
    private val chris = "chris"

    private fun row(id: String, grip: String?) = WorkoutExercise(id, "Seated cable row", 3, 10, grip = grip)

    private fun set(ex: String, order: Int, weight: Double) =
        WorkoutSet(id = "$ex-$order", exerciseId = ex, uid = chris, order = order, weight = weight, reps = 10, done = true)

    @Test
    fun gripsHaveTheirOwnKeys() {
        val none = weightKeys("Seated cable row", "home", "gym80")
        val wide = weightKeys("Seated cable row", "home", "gym80", grip = "Wide grip")
        assertTrue(none.none { it in wide }, "$none vs $wide")
        assertEquals(wide, weightKeys("Seated cable row", "home", "gym80", grip = " wide GRIP "))
        assertEquals(none, weightKeys("Seated cable row", "home", "gym80", grip = "  "))
        assertNotEquals(
            weightKeys("Seated cable row", "home", null, isometric = true, grip = "Wide grip"),
            weightKeys("Seated cable row", "home", null, grip = "Wide grip"),
        )
    }

    @Test
    fun bothGripsInOneWorkoutAreRememberedApart() {
        val narrow = row("a", "Narrow grip")
        val wide = row("b", "Wide grip")
        val workout = Workout(id = "w", gymId = "home", exercises = listOf(narrow, wide), startedAt = 1)
        val sets = listOf(set("a", 0, 50.0), set("a", 1, 50.0), set("b", 0, 40.0), set("b", 1, 40.0))
        val result = WeightMemory(emptyMap(), "home").finish(workout, sets, now = 10)
        val after = WeightMemory(result.memory, "home")
        val next = Workout(id = "w2", gymId = "home", exercises = listOf(narrow, wide), startedAt = 20)
        assertEquals(50.0, after.weight(chris, next, narrow))
        assertEquals(40.0, after.weight(chris, next, wide))
        // Without a grip there's nothing remembered yet.
        assertNull(after.weight(chris, next, row("c", null)))
        // Progress keeps them apart too.
        assertEquals(setOf("Narrow grip", "Wide grip"), result.progress.map { it.grip }.toSet())
        assertEquals(2, result.progress.map { it.setupKey }.distinct().size)
    }

    @Test
    fun oldWeightsWithoutGymOnlyFillTheNoGripVersion() {
        val m = WeightMemory(mapOf(chris to LastWeights(mapOf("seated cable row" to 45.0))), "home")
        val w = Workout(id = "w", gymId = "home", startedAt = 1)
        assertEquals(45.0, m.weight(chris, w, row("a", null)))
        assertNull(m.weight(chris, w, row("a", "Wide grip")))
    }

    @Test
    fun machineNotesAreSharedByEveryGrip() {
        val w = Workout(id = "w", gymId = "home", startedAt = 1)
        val m = WeightMemory(emptyMap(), "home", mapOf(chris to mapOf("seated cable row@home" to "seat 3")))
        assertEquals("seat 3", m.note(chris, w, row("a", "Wide grip")))
        assertEquals(m.noteKey(w, row("a", null)), m.noteKey(w, row("a", "Wide grip")))
    }

    @Test
    fun labels() {
        assertEquals("Seated cable row", exerciseLabel("Seated cable row"))
        assertEquals("Seated cable row (wide grip)", exerciseLabel("Seated cable row", grip = "Wide grip"))
        assertEquals("Lat pulldown (v-handle, isometric)", exerciseLabel("Lat pulldown", isometric = true, grip = "V-handle"))
    }
}

class GripAmendTest {
    private val chris = "chris"

    @Test
    fun changingAGripAfterFinishingForgetsTheOldGripsNumbers() {
        val wide = WorkoutExercise("b", "Seated cable row", 3, 10, grip = "Wide grip")
        val finished = Workout(id = "w", gymId = "home", exercises = listOf(wide), startedAt = 1, finishedAt = 100)
        val sets = listOf(WorkoutSet(id = "s", exerciseId = "b", uid = chris, order = 0, weight = 15.0, reps = 12, done = true))
        val atFinish = WeightMemory(emptyMap(), "home").finish(finished, sets, now = 100)
        // An earlier wide-grip session at 12.5 kg, then this one is changed to V-handle.
        val earlier = ProgressPoint(id = "old", workoutId = "old", uid = chris, exercise = "Seated cable row", exerciseKey = "seated cable row",
            gymId = "home", grip = "Wide grip", at = 50, topWeight = 12.5, sets = 3)
        val amended = finished.copy(exercises = listOf(wide.copy(grip = "V-handle")))
        val r = WeightMemory(atFinish.memory, "home").refinish(amended, sets, atFinish.progress + earlier)
        val after = WeightMemory(r.memory, "home")
        val next = Workout(id = "n", gymId = "home", startedAt = 200)
        assertEquals(15.0, after.weight(chris, next, wide.copy(grip = "V-handle")))
        assertEquals(12.5, after.weight(chris, next, wide), "back to the earlier wide-grip session")
        assertNull(after.lastTime(chris, next, wide))
        // Without the earlier session, the wide grip is forgotten.
        val r2 = WeightMemory(atFinish.memory, "home").refinish(amended, sets, atFinish.progress)
        assertNull(WeightMemory(r2.memory, "home").weight(chris, next, wide))
    }
}
