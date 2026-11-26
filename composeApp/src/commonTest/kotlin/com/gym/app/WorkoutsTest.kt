package com.gym.app

import com.gym.app.ui.summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.descriptors.elementNames

class WorkoutsTest {
    private val source = Workout(
        id = "old",
        planId = "plan1",
        name = "Push day",
        gymId = "home",
        exercises = listOf(
            WorkoutExercise("a", "Leg press", 4, 8, gymId = "oerlikon", brand = "gym80", isometric = true),
            WorkoutExercise("b", "Bench press", 3, 10),
        ),
        startedAt = 1,
        finishedAt = 2,
        warmUp = Circuit(listOf("Exercise bike", "Arm circles"), rounds = 1),
        mobility = Circuit(listOf("Child's pose", "Foam roll: calves"), rounds = 3),
    )

    private fun repeat(gymId: String, gym: GymLocation?): Workout {
        var n = 0
        return repeatOf(source, "new", gymId, gym, now = 99) { "id${n++}" }
    }

    @Test
    fun copiesExercisesWithFreshIds() {
        val w = repeat("home", GymLocation("home", "Evo Wiedikon", listOf("Technogym", "gym80")))
        assertEquals("new", w.id)
        assertEquals("Push day", w.name)
        assertEquals("plan1", w.planId)
        assertEquals(listOf("Leg press", "Bench press"), w.exercises.map { it.name })
        assertTrue(w.exercises.none { it.id == "a" || it.id == "b" })
        assertEquals(4, w.exercises[0].targetSets)
        assertEquals(true, w.exercises[0].isometric)
        assertEquals("gym80", w.exercises[0].brand)
        assertEquals(99, w.startedAt)
        assertNull(w.finishedAt)
    }

    @Test
    fun dropsPerExerciseGymAndBrandsTheGymDoesntHave() {
        val w = repeat("oerlikon", GymLocation("oerlikon", "Evo Oerlikon", listOf("Technogym", "Hammer Strength")))
        assertEquals("oerlikon", w.gymId)
        assertNull(w.exercises[0].gymId)
        assertNull(w.exercises[0].brand, "gym80 isn't at Oerlikon")
    }

    @Test
    fun repeatKeepsWarmUpAndMobility() {
        val w = repeat("home", GymLocation("home", "Evo Wiedikon"))
        assertEquals(source.warmUp, w.warmUp)
        assertEquals(source.mobility, w.mobility)
        assertEquals("2 moves · 3 rounds", w.mobility.summary())
        assertEquals("2 moves", w.warmUp.summary())
    }
}

class FormattingTest {
    @Test
    fun setsAreSingularOrPlural() {
        assertEquals("1 set", com.gym.app.ui.setsLabel(1))
        assertEquals("3 sets", com.gym.app.ui.setsLabel(3))
        assertEquals("0 sets", com.gym.app.ui.setsLabel(0))
    }

    @Test
    fun volumeUsesSwissThousandsSeparator() {
        assertEquals("232 kg", com.gym.app.ui.formatVolume(232.0))
        assertEquals("1'480 kg", com.gym.app.ui.formatVolume(1480.0))
        assertEquals("12'345 kg", com.gym.app.ui.formatVolume(12345.6))
    }
}

class FirestoreShapeTest {
    @Test
    fun arrayUnionMapHasExactlyTheSerializedFields() {
        // Exercises added with arrayUnion are written as raw maps; if a field were missing or
        // misnamed, reading the workout back would silently lose it.
        val mapKeys = WorkoutExercise("a", "Leg press", 3, 10, "gym", "gym80", true).toFirestoreMap().keys
        val serialKeys = WorkoutExercise.serializer().descriptor.elementNames.toSet()
        assertEquals(serialKeys, mapKeys)
    }

}

class JustMeTest {
    private val pair = listOf("me", "partner")

    @Test
    fun oldWorkoutsWithNoPeopleAreForBoth() {
        val w = Workout(id = "w")
        assertEquals(pair, w.peopleIn(pair))
        assertEquals(true, w.includes("partner"))
        assertEquals(false, w.isSolo)
    }

    @Test
    fun justMeWorkoutHasOnlyThatPerson() {
        val mine = Workout(id = "w", people = listOf("me"))
        assertEquals(listOf("me"), mine.peopleIn(pair))
        assertEquals(false, mine.includes("partner"))
        // My partner's own workout, seen from my phone.
        val theirs = Workout(id = "v", people = listOf("partner"))
        assertEquals(listOf("partner"), theirs.peopleIn(pair))
        assertEquals(false, theirs.includes("me"))
    }

    @Test
    fun repeatKeepsWhoChoseToTrain() {
        var n = 0
        val source = Workout(id = "w", name = "Legs", exercises = listOf(WorkoutExercise("e", "Squat")))
        val solo = repeatOf(source, "new", "home", null, 1L, listOf("me")) { "id${n++}" }
        assertEquals(listOf("me"), solo.people)
        assertEquals(emptyList(), repeatOf(source, "new2", "home", null, 1L) { "id${n++}" }.people)
    }
}

class CircuitMinutesTest {
    private val c = Circuit(listOf("Elliptical", "Leg swings"), rounds = 2)

    @Test
    fun minutesAreKeyedByNameAndTotalled() {
        val timed = c.withMinutes("Elliptical", 10)
        assertEquals(10, timed.minutesFor("elliptical"))
        assertEquals(20, timed.totalMinutes)
        assertEquals(null, timed.withMinutes("Elliptical", null).minutesFor("Elliptical"))
    }

    @Test
    fun removingAMoveDropsItsMinutes() {
        val removed = c.withMinutes("Elliptical", 10).without(0)
        assertEquals(listOf("Leg swings"), removed.exercises)
        assertEquals(emptyMap(), removed.minutes)
    }

    @Test
    fun renameMovesMinutes() {
        val w = Workout(warmUp = Circuit(listOf("My bike")).withMinutes("My bike", 8))
        assertEquals(8, w.renamed("My bike", "Air bike")!!.warmUp.minutesFor("Air bike"))
    }
}

class HealthFiguresTest {
    private val stats = HealthStats(kcal = 48, avgBpm = 132, maxBpm = 150)

    @Test
    fun pickedSessionKeepsFiguresAndTypingDropsThem() {
        val c = Circuit(listOf("Elliptical")).withMinutes("Elliptical", 5, stats)
        assertEquals(stats, c.healthFor("Elliptical"))
        assertEquals("48 kcal · avg 132 bpm", c.healthFor("Elliptical")!!.label())
        assertEquals("48 kcal · avg 132 bpm · max 150", stats.label(withMax = true))
        val typed = c.withMinutes("Elliptical", 7)
        assertEquals(7, typed.minutesFor("Elliptical"))
        assertNull(typed.healthFor("Elliptical"))
        assertTrue(c.without(0).health.isEmpty())
    }

    @Test
    fun visitStretchesToNearbyWatchSessions() {
        val min = 60_000L
        val warmUp = HealthSession("Elliptical", start = 0, end = 5 * min, source = null)
        val morningRun = HealthSession("Run", start = -300 * min, end = -270 * min, source = null)
        val coolDown = HealthSession("Rowing", start = 70 * min, end = 75 * min, source = null)
        // Workout started in the app 10 min after the warm-up began, finished at 65 min.
        assertEquals(0L..75 * min, visitRange(10 * min, 65 * min, listOf(warmUp, morningRun, coolDown)))
        assertEquals(10 * min..65 * min, visitRange(10 * min, 65 * min, emptyList()))
    }
}
