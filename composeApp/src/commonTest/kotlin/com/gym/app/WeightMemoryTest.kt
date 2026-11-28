package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightMemoryTest {

    private val chris = "chris"
    private val graciela = "graciela"
    private val home = "home"
    private val other = "oerlikon"

    private fun ex(
        id: String = "ex1",
        name: String = "Leg press",
        gymId: String? = null,
        brand: String? = null,
        isometric: Boolean = false,
        sets: Int = 3,
        reps: Int = 10,
    ) = WorkoutExercise(id, name, sets, reps, gymId, brand, isometric)

    private fun workout(vararg exercises: WorkoutExercise, gymId: String? = home) =
        Workout(id = "w1", name = "Test", gymId = gymId, exercises = exercises.toList(), startedAt = 1)

    private fun set(
        order: Int,
        weight: Double? = null,
        reps: Int? = 10,
        done: Boolean = false,
        uid: String = chris,
        exerciseId: String = "ex1",
    ) = WorkoutSet(id = "$uid-$exerciseId-$order", exerciseId = exerciseId, uid = uid, order = order, weight = weight, reps = reps, done = done)

    private fun memory(vararg entries: Pair<String, LastWeights>, homeGym: String = home) =
        WeightMemory(entries.toMap(), homeGym)

    // ---- pre-filling ----

    @Test
    fun brandSpecificWeightWinsOverGymWide() {
        val m = memory(chris to LastWeights(mapOf("leg press@home@gym80" to 90.0, "leg press@home" to 70.0)))
        assertEquals(90.0, m.weight(chris, workout(ex(brand = "gym80")), ex(brand = "gym80")))
        assertEquals(70.0, m.weight(chris, workout(ex(brand = "Technogym")), ex(brand = "Technogym")))
        assertEquals(70.0, m.weight(chris, workout(ex()), ex()))
    }

    @Test
    fun weightsNeverCrossGyms() {
        val m = memory(chris to LastWeights(mapOf("leg press@home" to 70.0, "leg press" to 60.0)))
        val w = workout(ex(), gymId = other)
        assertNull(m.weight(chris, w, ex()), "a home-gym weight must not pre-fill at another gym")
    }

    @Test
    fun legacyWeightsCountOnlyForHomeGymAndNormalType() {
        val m = memory(chris to LastWeights(mapOf("leg press" to 60.0)))
        assertEquals(60.0, m.weight(chris, workout(ex()), ex()))
        assertNull(m.weight(chris, workout(ex(isometric = true)), ex(isometric = true)))
        assertNull(m.weight(chris, workout(ex(), gymId = other), ex()))
    }

    @Test
    fun isometricIsRememberedSeparately() {
        val m = memory(chris to LastWeights(mapOf("leg press@home" to 70.0, "leg press~iso@home" to 120.0)))
        assertEquals(120.0, m.weight(chris, workout(ex(isometric = true)), ex(isometric = true)))
        assertEquals(70.0, m.weight(chris, workout(ex()), ex()))
    }

    @Test
    fun exerciseLevelGymOverridesWorkoutGym() {
        val m = memory(chris to LastWeights(mapOf("leg press@oerlikon" to 55.0)))
        val e = ex(gymId = other)
        assertEquals(other, m.gymOf(workout(e), e))
        assertEquals(55.0, m.weight(chris, workout(e), e))
    }

    @Test
    fun newSetsArePrefilledPerPerson() {
        val m = memory(
            chris to LastWeights(mapOf("leg press@home" to 80.0)),
            graciela to LastWeights(mapOf("leg press@home" to 50.0)),
        )
        var n = 0
        val sets = m.newSets(workout(ex()), ex(sets = 2, reps = 12), listOf(chris, graciela), chris, 5) { "id${n++}" }
        assertEquals(4, sets.size)
        assertEquals(listOf(80.0, 80.0), sets.filter { it.uid == chris }.map { it.weight })
        assertEquals(listOf(50.0, 50.0), sets.filter { it.uid == graciela }.map { it.weight })
        assertTrue(sets.all { it.reps == 12 })
        assertEquals(listOf(0, 1), sets.filter { it.uid == chris }.map { it.order })
    }

    // ---- re-filling after a gym / brand / type change ----

    @Test
    fun refillSwapsOnlyUntouchedPrefilledSets() {
        val m = memory(chris to LastWeights(mapOf("leg press@home@gym80" to 90.0, "leg press@home@technogym" to 60.0)))
        val before = workout(ex(brand = "gym80"))
        val after = workout(ex(brand = "Technogym"))
        val sets = listOf(
            set(0, weight = 90.0),              // still the pre-fill -> swapped
            set(1, weight = 95.0),              // typed by hand -> kept
            set(2, weight = 90.0, done = true), // already done -> kept
        )
        val changed = m.refill(before, after, sets, chris, 9)
        assertEquals(1, changed.size)
        assertEquals(0, changed[0].order)
        assertEquals(60.0, changed[0].weight)
    }

    @Test
    fun refillClearsWeightWhenNewSetupHasNoHistory() {
        val m = memory(chris to LastWeights(mapOf("leg press@home" to 70.0)))
        val changed = m.refill(workout(ex()), workout(ex(), gymId = other), listOf(set(0, weight = 70.0)), chris, 9)
        assertEquals(listOf<Double?>(null), changed.map { it.weight })
    }

    @Test
    fun refillDoesNothingWhenWeightIsTheSame() {
        val m = memory(chris to LastWeights(mapOf("leg press@home" to 70.0)))
        val changed = m.refill(workout(ex()), workout(ex(brand = "gym80")), listOf(set(0, weight = 70.0)), chris, 9)
        assertTrue(changed.isEmpty(), "gym-wide 70 applies to both, so nothing should be rewritten")
    }

    // ---- finishing ----

    @Test
    fun finishRemembersLastTickedSetPerPerson() {
        val m = memory()
        val sets = listOf(
            set(0, 80.0, done = true), set(1, 85.0, done = true), set(2, 90.0, done = false),
            set(0, 50.0, done = false, uid = graciela), set(1, 55.0, done = false, uid = graciela),
        )
        val result = m.afterFinish(workout(ex(brand = "gym80")), sets, now = 100)
        // Chris: last *ticked* set (85), not the unticked 90.
        assertEquals(85.0, result.getValue(chris).weights["leg press@home@gym80"])
        assertEquals(85.0, result.getValue(chris).weights["leg press@home"])
        // Graciela ticked nothing: her pre-filled sets don't count, so nothing is remembered.
        assertTrue(graciela !in result)
    }

    @Test
    fun finishStoresLastTimeSets() {
        val sets = listOf(set(0, 80.0, 10, done = true), set(1, 85.0, 8, done = true), set(2, 90.0, 5, done = false))
        val last = memory().afterFinish(workout(ex()), sets, now = 100).getValue(chris).last.getValue("leg press@home")
        assertEquals(listOf(SetRecord(80.0, 10), SetRecord(85.0, 8)), last.sets)
        assertEquals(100, last.at)
        assertEquals("80×10 · 85×8", last.summary())
    }

    @Test
    fun prefilledButUntickedSetsCountForNothing() {
        // Seen on the device: a partner who skipped an exercise showed "3 sets · 660 kg" because
        // their sets were pre-filled with last time's 22 kg.
        val m = memory(chris to LastWeights(mapOf("leg press@home" to 22.0), best = mapOf("leg press@home" to 22.0)))
        val sets = listOf(set(0, 22.0), set(1, 22.0), set(2, 22.0))
        val r = m.finish(workout(ex()), sets, now = 1)
        assertTrue(r.progress.isEmpty())
        assertTrue(r.prs.isEmpty())
        assertTrue(chris !in r.memory)
    }

    @Test
    fun finishWithNoDataRemembersNothing() {
        val sets = listOf(set(0, weight = null), set(1, weight = null))
        assertTrue(memory().afterFinish(workout(ex()), sets, now = 100).isEmpty())
    }

    @Test
    fun finishMergesWithExistingMemory() {
        val m = memory(chris to LastWeights(mapOf("squat@home" to 100.0)))
        val result = m.afterFinish(workout(ex()), listOf(set(0, 80.0, done = true)), now = 1).getValue(chris)
        assertEquals(100.0, result.weights["squat@home"], "other exercises must be kept")
        assertEquals(80.0, result.weights["leg press@home"])
    }

    @Test
    fun lastTimeMatchesSetup() {
        val perf = Performance(listOf(SetRecord(80.0, 10)), 1)
        val m = memory(chris to LastWeights(last = mapOf("leg press@home@gym80" to perf)))
        assertEquals(perf, m.lastTime(chris, workout(ex(brand = "gym80")), ex(brand = "gym80")))
        assertNull(m.lastTime(chris, workout(ex(), gymId = other), ex()))
        assertNull(m.lastTime(graciela, workout(ex(brand = "gym80")), ex(brand = "gym80")))
    }

    // ---- progress + personal bests ----

    @Test
    fun finishMakesOneProgressPointPerPersonAndExercise() {
        val sets = listOf(
            set(0, 80.0, 10, done = true), set(1, 85.0, 8, done = true), set(2, 90.0, 5, done = false),
            set(0, 50.0, 10, done = true, uid = graciela),
        )
        val result = memory().finish(workout(ex(brand = "gym80")), sets, now = 100)
        assertEquals(2, result.progress.size)
        val mine = result.progress.single { it.uid == chris }
        assertEquals("w1_chris_ex1", mine.id)
        assertEquals(85.0, mine.topWeight, "unticked 90 doesn't count")
        assertEquals(8, mine.topReps)
        assertEquals(80.0 * 10 + 85.0 * 8, mine.volume)
        assertEquals(2, mine.sets)
        assertEquals("leg press@home@gym80", mine.setupKey)
        assertEquals("gym80", mine.brand)
    }

    @Test
    fun firstSessionIsNotAPersonalBestButSetsTheBar() {
        val result = memory().finish(workout(ex()), listOf(set(0, 80.0, done = true)), now = 1)
        assertTrue(result.prs.isEmpty())
        assertEquals(80.0, result.memory.getValue(chris).best["leg press@home"])
    }

    @Test
    fun beatingThePreviousBestIsAPersonalBest() {
        val m = memory(chris to LastWeights(best = mapOf("leg press@home" to 80.0)))
        val beat = m.finish(workout(ex()), listOf(set(0, 82.5, done = true)), now = 1)
        assertEquals(listOf("Leg press"), beat.prs[chris])
        assertEquals(82.5, beat.memory.getValue(chris).best["leg press@home"])

        val equal = m.finish(workout(ex()), listOf(set(0, 80.0, done = true)), now = 1)
        assertTrue(equal.prs.isEmpty(), "matching the best isn't a new best")
        assertEquals(80.0, equal.memory.getValue(chris).best["leg press@home"])
    }

    @Test
    fun bestsAreTrackedPerSetup() {
        val m = memory(chris to LastWeights(best = mapOf("leg press@home@gym80" to 100.0)))
        // Technogym at the same gym has no history yet -> first session, not a best.
        val r = m.finish(workout(ex(brand = "Technogym")), listOf(set(0, 120.0, done = true)), now = 1)
        assertTrue(r.prs.isEmpty())
        assertEquals(120.0, r.memory.getValue(chris).best["leg press@home@technogym"])
        assertEquals(100.0, r.memory.getValue(chris).best["leg press@home@gym80"])
    }

    @Test
    fun bodyweightExercisesStillGetProgressButNoBest() {
        val r = memory().finish(workout(ex(name = "Pull-up")), listOf(set(0, null, 12, done = true)), now = 1)
        assertEquals(12, r.progress.single().topReps)
        assertNull(r.progress.single().topWeight)
        assertTrue(r.memory[chris]?.best.isNullOrEmpty())
    }

    // ---- machine notes ----

    @Test
    fun notesPreferBrandThenGymAndNeverCrossGyms() {
        val m = WeightMemory(
            emptyMap(), home,
            mapOf(chris to mapOf("leg press@home@gym80" to "seat 4", "leg press@home" to "seat 6")),
        )
        assertEquals("seat 4", m.note(chris, workout(ex(brand = "gym80")), ex(brand = "gym80")))
        assertEquals("seat 6", m.note(chris, workout(ex(brand = "Technogym")), ex(brand = "Technogym")))
        assertNull(m.note(chris, workout(ex(), gymId = other), ex()))
        assertNull(m.note(graciela, workout(ex()), ex()))
    }

    @Test
    fun notesAreSavedUnderTheMostSpecificKey() {
        val m = memory()
        assertEquals("leg press@home@gym80", m.noteKey(workout(ex(brand = "gym80")), ex(brand = "gym80")))
        assertEquals("leg press@home", m.noteKey(workout(ex()), ex()))
        assertEquals("leg press~iso@oerlikon", m.noteKey(workout(ex(isometric = true), gymId = other), ex(isometric = true)))
    }

    @Test
    fun summaryHandlesBodyweightAndMissingReps() {
        assertEquals("12 reps · 20×8 · 30 kg", Performance(listOf(SetRecord(null, 12), SetRecord(20.0, 8), SetRecord(30.0, null))).summary())
    }

    // ---- fixing a finished workout ----

    @Test
    fun refinishFixesChartsBestsAndLastTime() {
        // Finished at 100 with a typo: 800 kg instead of 80.
        val w = workout(ex()).copy(finishedAt = 100)
        val typo = listOf(set(0, 800.0, done = true))
        val fixed = listOf(set(0, 80.0, done = true))
        val key = weightKeys("Leg press", home, null).first()
        val before = memory(chris to LastWeights(
            weights = mapOf(key to 800.0),
            last = mapOf(key to Performance(listOf(SetRecord(800.0, 10)), 100)),
            best = mapOf(key to 800.0),
        ))
        val earlier = ProgressPoint(id = "w0_chris_ex1", workoutId = "w0", uid = chris, exercise = "Leg press", setupKey = key, at = 50, topWeight = 70.0)
        val stale = before.finish(w, typo, 100).progress.single()
        val r = before.refinish(w, fixed, listOf(earlier, stale))
        assertEquals(80.0, r.upserts.single().topWeight)
        assertEquals(100L, r.upserts.single().at)
        assertTrue(r.deleteIds.isEmpty())
        val mem = r.memory.getValue(chris)
        assertEquals(80.0, mem.weights[key])
        assertEquals(80.0, mem.best[key])
        assertEquals(listOf(SetRecord(80.0, 10)), mem.last[key]!!.sets)
        assertEquals(listOf("Leg press"), r.prs[chris]) // 80 beats the earlier 70
    }

    @Test
    fun refinishLeavesNewerWorkoutsAlone() {
        val w = workout(ex()).copy(finishedAt = 100)
        val key = weightKeys("Leg press", home, null).first()
        val newer = Performance(listOf(SetRecord(90.0, 10)), 200)
        val r = memory(chris to LastWeights(weights = mapOf(key to 90.0), last = mapOf(key to newer)))
            .refinish(w, listOf(set(0, 80.0, done = true)), emptyList())
        val mem = r.memory.getValue(chris) // only the best changes
        assertEquals(90.0, mem.weights[key])
        assertEquals(newer, mem.last[key])
        assertEquals(80.0, mem.best[key])
    }

    @Test
    fun refinishDropsRecordsOfUntickedSets() {
        val w = workout(ex()).copy(finishedAt = 100)
        val old = ProgressPoint(id = "w1_chris_ex1", workoutId = "w1", uid = chris, setupKey = "leg press@home", at = 100, topWeight = 80.0)
        val r = memory().refinish(w, listOf(set(0, 80.0, done = false)), listOf(old))
        assertTrue(r.upserts.isEmpty())
        assertEquals(listOf("w1_chris_ex1"), r.deleteIds)
    }

    // ---- next weight ----

    private fun lastTime(vararg sets: Pair<Double, Int>) = memory(
        chris to LastWeights(
            weights = mapOf("leg press@home" to sets.maxOf { it.first }),
            last = mapOf("leg press@home" to Performance(sets.map { SetRecord(it.first, it.second) })),
        ),
    )

    @Test
    fun suggestsTheNextWeightWhenEveryTopSetHitTheReps() {
        val w = workout(ex(sets = 4, reps = 12))
        // A lighter warm-up set first is fine.
        assertEquals(42.5, lastTime(20.0 to 10, 40.0 to 12, 40.0 to 12, 40.0 to 12).suggestion(chris, w, w.exercises[0]))
        assertEquals(42.5, lastTime(40.0 to 12, 40.0 to 13, 40.0 to 12, 40.0 to 12).suggestion(chris, w, w.exercises[0]))
    }

    @Test
    fun noSuggestionWhenRepsWereMissedOrTooFewSets() {
        val w = workout(ex(sets = 4, reps = 12))
        assertNull(lastTime(40.0 to 12, 40.0 to 12, 40.0 to 10).suggestion(chris, w, w.exercises[0]))
        assertNull(lastTime(20.0 to 12, 30.0 to 12, 40.0 to 12).suggestion(chris, w, w.exercises[0]))
        assertNull(memory().suggestion(chris, w, w.exercises[0]))
    }

    @Test
    fun stepsUpByTheUsualIncrement() {
        assertEquals(4.0, nextWeight(3.0))
        assertEquals(10.0, nextWeight(7.5))
        assertEquals(32.5, nextWeight(30.0))
        assertEquals(55.0, nextWeight(50.0))
    }
}
