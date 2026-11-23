package com.gym.app

/**
 * Everything about remembered weights, kept free of Firebase so it can be unit tested:
 * which weight to pre-fill, how to re-fill when the gym/brand/type changes, what to remember when
 * a workout is finished, and the "Last time" line.
 *
 * Weights are remembered per exercise + gym + machine brand (+ isometric), see [weightKeys].
 */
class WeightMemory(
    private val byMember: Map<String, LastWeights>,
    private val homeGymId: String,
    private val notesByMember: Map<String, Map<String, String>> = emptyMap(),
) {
    /** Where this exercise was actually done. */
    fun gymOf(workout: Workout, ex: WorkoutExercise): String = ex.gymId ?: workout.gymId ?: homeGymId

    fun weight(member: String, name: String, gymId: String, brand: String?, isometric: Boolean): Double? {
        val weights = byMember[member]?.weights ?: return null
        for (key in weightKeys(name, gymId, brand, isometric)) weights[key]?.let { return it }
        // Weights saved before gyms existed were all at the home gym (and never isometric).
        return if (gymId == homeGymId && !isometric) weights[exerciseKey(name)] else null
    }

    fun weight(member: String, workout: Workout, ex: WorkoutExercise): Double? =
        weight(member, ex.name, gymOf(workout, ex), ex.brand, ex.isometric)

    /** What [member] did last time for this exercise in the same setup, if anything. */
    fun lastTime(member: String, workout: Workout, ex: WorkoutExercise): Performance? {
        val last = byMember[member]?.last ?: return null
        for (key in weightKeys(ex.name, gymOf(workout, ex), ex.brand, ex.isometric)) last[key]?.let { return it }
        return null
    }

    /**
     * [member]'s machine note ("seat 4") for this exercise in this setup: the brand-specific note if
     * there is one, else the gym-wide one. Notes never carry over between gyms.
     */
    fun note(member: String, workout: Workout, ex: WorkoutExercise): String? {
        val notes = notesByMember[member] ?: return null
        for (key in weightKeys(ex.name, gymOf(workout, ex), ex.brand, ex.isometric)) notes[key]?.let { return it }
        return null
    }

    /** Where a note for this exercise is saved: as specific as the setup allows (gym + brand). */
    fun noteKey(workout: Workout, ex: WorkoutExercise): String =
        weightKeys(ex.name, gymOf(workout, ex), ex.brand, ex.isometric).first()

    /** Fresh sets for each member, pre-filled with their remembered weight and the target reps. */
    fun newSets(
        workout: Workout,
        ex: WorkoutExercise,
        members: List<String>,
        updatedBy: String,
        now: Long,
        newId: () -> String,
    ): List<WorkoutSet> = members.flatMap { member ->
        val w = weight(member, workout, ex)
        (0 until ex.targetSets).map { i ->
            WorkoutSet(
                id = newId(), exerciseId = ex.id, uid = member, order = i, weight = w,
                reps = ex.targetReps, updatedBy = updatedBy, updatedAt = now,
            )
        }
    }

    /**
     * After a gym/brand/type change, swap in the remembered weight for the new setup on sets that
     * aren't ticked off and still hold the old pre-filled weight (typed-in numbers are kept).
     * Returns only the sets that change.
     */
    fun refill(before: Workout, after: Workout, sets: List<WorkoutSet>, updatedBy: String, now: Long): List<WorkoutSet> =
        sets.mapNotNull { s ->
            if (s.done) return@mapNotNull null
            val exBefore = before.exercises.firstOrNull { it.id == s.exerciseId } ?: return@mapNotNull null
            val exAfter = after.exercises.firstOrNull { it.id == s.exerciseId } ?: return@mapNotNull null
            val old = weight(s.uid, before, exBefore)
            val new = weight(s.uid, after, exAfter)
            if (old == new || (s.weight != null && s.weight != old)) null
            else s.copy(weight = new, updatedBy = updatedBy, updatedAt = now)
        }

    /** Everything a finish writes: updated memory per person, progress records and new bests. */
    data class FinishResult(
        val memory: Map<String, LastWeights>,
        val progress: List<ProgressPoint>,
        val prs: Map<String, List<String>>,
    )

    /**
     * Finishing [workout]: [afterFinish]'s memory updates plus one [ProgressPoint] per person per
     * exercise with data, and personal bests (top weight above the previous best for that exact setup;
     * a first-ever session isn't counted as a best).
     */
    fun finish(workout: Workout, sets: List<WorkoutSet>, now: Long): FinishResult {
        val memory = afterFinish(workout, sets, now).toMutableMap()
        val progress = mutableListOf<ProgressPoint>()
        val prs = mutableMapOf<String, MutableList<String>>()
        for (member in sets.map { it.uid }.distinct()) {
            val newBests = mutableMapOf<String, Double>()
            for (ex in workout.exercises) {
                if (exerciseKey(ex.name).isEmpty()) continue
                // Only ticked sets count: un-ticked ones still hold pre-filled numbers nobody lifted.
                val counted = sets.filter { it.uid == member && it.exerciseId == ex.id && it.done }
                if (counted.isEmpty()) continue
                val gymId = gymOf(workout, ex)
                val setupKey = weightKeys(ex.name, gymId, ex.brand, ex.isometric).first()
                val top = counted.maxWithOrNull(compareBy<WorkoutSet>({ it.weight ?: -1.0 }, { it.reps ?: -1 }))
                progress += ProgressPoint(
                    id = "${workout.id}_${member}_${ex.id}",
                    workoutId = workout.id,
                    uid = member,
                    exercise = ex.name,
                    exerciseKey = exerciseKey(ex.name),
                    setupKey = setupKey,
                    gymId = gymId,
                    brand = ex.brand,
                    isometric = ex.isometric,
                    at = now,
                    topWeight = top?.weight,
                    topReps = top?.reps,
                    volume = counted.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) },
                    sets = counted.size,
                )
                val topWeight = top?.weight ?: continue
                val previous = byMember[member]?.best?.get(setupKey)
                if (previous != null && topWeight > previous) prs.getOrPut(member) { mutableListOf() } += ex.name
                if (previous == null || topWeight > previous) newBests[setupKey] = topWeight
            }
            if (newBests.isNotEmpty()) {
                val base = memory[member] ?: byMember[member] ?: LastWeights()
                memory[member] = base.copy(best = base.best + newBests)
            }
        }
        return FinishResult(memory, progress, prs)
    }

    /**
     * What to store per member when [workout] is finished: the working weight (last ticked set with a
     * weight) and the ticked sets for "Last time". Un-ticked sets are ignored.
     * Only members with something new are returned; their existing memory is merged in.
     */
    fun afterFinish(workout: Workout, sets: List<WorkoutSet>, now: Long): Map<String, LastWeights> =
        sets.map { it.uid }.distinct().mapNotNull { member ->
            val weightUpdates = mutableMapOf<String, Double>()
            val lastUpdates = mutableMapOf<String, Performance>()
            for (ex in workout.exercises) {
                if (exerciseKey(ex.name).isEmpty()) continue
                // Only ticked sets count (un-ticked ones still hold pre-filled numbers nobody lifted).
                val counted = sets.filter { it.uid == member && it.exerciseId == ex.id && it.done }.sortedBy { it.order }
                val keys = weightKeys(ex.name, gymOf(workout, ex), ex.brand, ex.isometric)

                counted.lastOrNull { it.weight != null }?.weight?.let { w ->
                    keys.forEach { weightUpdates[it] = w }
                }

                if (counted.isNotEmpty()) {
                    val perf = Performance(counted.map { SetRecord(it.weight, it.reps) }, now)
                    keys.forEach { lastUpdates[it] = perf }
                }
            }
            if (weightUpdates.isEmpty() && lastUpdates.isEmpty()) return@mapNotNull null
            val existing = byMember[member] ?: LastWeights()
            member to existing.copy(weights = existing.weights + weightUpdates, last = existing.last + lastUpdates)
        }.toMap()
}

/** "80×10 · 80×10 · 85×8" (bodyweight sets show just the reps). */
fun Performance.summary(): String = sets.joinToString(" · ") { s ->
    when {
        s.weight != null && s.reps != null -> "${formatWeight(s.weight)}×${s.reps}"
        s.weight != null -> "${formatWeight(s.weight)} kg"
        else -> "${s.reps ?: 0} reps"
    }
}
