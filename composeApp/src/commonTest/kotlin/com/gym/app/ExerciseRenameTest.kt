package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseRenameTest {

    @Test
    fun renamesEveryKeyOfTheExerciseOnly() {
        assertEquals("sled drive@home@gym80", renameKey("sled push@home@gym80", "Sled push", "Sled drive"))
        assertEquals("sled drive~iso@home", renameKey("sled push~iso@home", "Sled push", "Sled drive"))
        assertEquals("sled drive", renameKey("sled push", "Sled push", "Sled drive")) // pre-gym key
        // A different exercise whose name merely starts the same way is left alone.
        assertEquals("sled pushdown@home", renameKey("sled pushdown@home", "Sled push", "Sled drive"))
        assertEquals("leg press@home", renameKey("leg press@home", "Sled push", "Sled drive"))
    }

    @Test
    fun lastWeightsMoveWithTheName() {
        val lw = LastWeights(
            weights = mapOf("sled push@home" to 40.0, "leg press@home" to 100.0),
            last = mapOf("sled push@home" to Performance(listOf(SetRecord(40.0, 10)))),
            best = mapOf("sled push@home@gym80" to 50.0),
        )
        val r = lw.renamed("Sled push", "Sled drive")
        assertEquals(mapOf("sled drive@home" to 40.0, "leg press@home" to 100.0), r.weights)
        assertEquals(setOf("sled drive@home"), r.last.keys)
        assertEquals(mapOf("sled drive@home@gym80" to 50.0), r.best)
    }

    @Test
    fun workoutRenamesExerciseAndPersonalBests() {
        val w = Workout(
            exercises = listOf(WorkoutExercise(id = "a", name = "sled push"), WorkoutExercise(id = "b", name = "Leg press")),
            prs = mapOf("u1" to listOf("Sled push", "Leg press")),
        )
        val r = w.renamed("Sled push", "Sled drive")!!
        assertEquals(listOf("Sled drive", "Leg press"), r.exercises.map { it.name })
        assertEquals(listOf("Sled drive", "Leg press"), r.prs["u1"])
        assertNull(Workout(exercises = listOf(WorkoutExercise(name = "Leg press"))).renamed("Sled push", "Sled drive"))
    }

    @Test
    fun progressPointGetsNewKeys() {
        val p = ProgressPoint(exercise = "Sled push", exerciseKey = "sled push", setupKey = "sled push@home@gym80")
            .renamed("Sled push", "Sled drive")
        assertEquals("Sled drive", p.exercise)
        assertEquals("sled drive", p.exerciseKey)
        assertEquals("sled drive@home@gym80", p.setupKey)
    }

    @Test
    fun renameReachesWarmUpAndMobility() {
        val plan = Plan(warmUp = Circuit(listOf("Mat crunch", "Arm circles")), mobility = Circuit(listOf("mat crunch")))
        val r = plan.renamed("Mat crunch", "Mat sit-up")!!
        assertEquals(listOf("Mat sit-up", "Arm circles"), r.warmUp.exercises)
        assertEquals(listOf("Mat sit-up"), r.mobility.exercises)
        val w = Workout(warmUp = Circuit(listOf("Mat crunch"))).renamed("Mat crunch", "Mat sit-up")!!
        assertEquals(listOf("Mat sit-up"), w.warmUp.exercises)
    }
}
