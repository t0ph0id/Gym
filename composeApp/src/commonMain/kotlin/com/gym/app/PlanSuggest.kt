package com.gym.app

/*
 * "Suggest a plan": a plan built around the muscles you've trained least lately. Plain rules, no
 * AI: count each person's finished sets per muscle over the last few weeks (from ProgressPoints),
 * take the least trained muscles, and pick exercises for them, ones you already do first.
 */

/** Muscles a suggested plan can focus on (cardio and "Other" are left out). */
val PLANNED_MUSCLES = listOf(
    Muscle.Legs, Muscle.Back, Muscle.Chest, Muscle.Glutes,
    Muscle.Shoulders, Muscle.Triceps, Muscle.Biceps, Muscle.Core,
)

/** How much a muscle was trained in the period: finished sets, and when it was last trained (ever). */
data class MuscleLoad(val muscle: Muscle, val sets: Int, val lastAt: Long?)

data class PlanSuggestion(
    val plan: Plan,
    /** The muscles the plan works, least trained first. */
    val focus: List<Muscle>,
    /** Every muscle in [PLANNED_MUSCLES], least trained first. */
    val loads: List<MuscleLoad>,
    val days: Int,
    /** Finished workouts in the period that the sets came from. */
    val workouts: Int,
)

/** The muscle an exercise works: built-in catalogue first, then your own exercises. */
fun muscleOf(name: String, own: Map<String, Muscle> = emptyMap()): Muscle? =
    Catalog.find(name)?.muscle ?: own[exerciseKey(name)]

/**
 * Sets per muscle for [members] since [from], least trained first. Ties go to the muscle trained
 * longest ago (never trained counts as longest), then to [PLANNED_MUSCLES] order.
 */
fun muscleLoads(points: List<ProgressPoint>, members: List<String>, from: Long, own: Map<String, Muscle> = emptyMap()): List<MuscleLoad> {
    val mine = points.filter { it.uid in members }
    return PLANNED_MUSCLES.map { m ->
        val hits = mine.filter { muscleOf(it.exercise, own) == m && it.sets > 0 }
        MuscleLoad(m, hits.filter { it.at >= from }.sumOf { it.sets }, hits.maxOfOrNull { it.at })
    }.sortedWith(compareBy<MuscleLoad>({ it.sets }, { it.lastAt ?: Long.MIN_VALUE }, { PLANNED_MUSCLES.indexOf(it.muscle) }))
}

/**
 * Suggests a plan for [members] (one person, or both partners together) from their [points].
 * [focusCount] least-trained muscles get [perMuscle] exercises each. Exercises you've done before
 * come first (most recent first), then catalogue ones using the equipment you use most.
 * [variant] rotates to other exercises ("Other exercises"). Null when there's no history yet.
 */
fun suggestPlan(
    points: List<ProgressPoint>,
    members: List<String>,
    now: Long,
    own: List<CatalogExercise> = emptyList(),
    days: Int = 56,
    focusCount: Int = 3,
    perMuscle: Int = 2,
    sets: Int = 3,
    reps: Int = 10,
    warmUp: Circuit = Circuit(),
    mobility: Circuit = Circuit(),
    variant: Int = 0,
    makeId: () -> String = ::newId,
): PlanSuggestion? {
    val mine = points.filter { it.uid in members }
    if (mine.isEmpty()) return null
    val ownMuscles = own.associate { exerciseKey(it.name) to it.muscle }
    val from = now - days * 86_400_000L
    val loads = muscleLoads(points, members, from, ownMuscles)
    val focus = loads.take(focusCount).map { it.muscle }

    // When each exercise was last done, and how often each kind of equipment is used.
    val lastDone = mine.groupBy { it.exerciseKey }.mapValues { (_, p) -> p.maxOf { it.at } }
    val equipmentUse = mine.mapNotNull { p -> (Catalog.find(p.exercise) ?: own.firstOrNull { exerciseKey(it.name) == p.exerciseKey })?.equipment }
        .groupingBy { it }.eachCount()

    val chosen = focus.sortedBy { PLANNED_MUSCLES.indexOf(it) }.flatMap { m ->
        val candidates = (Catalog.exercises + own).filter { it.muscle == m }.distinctBy { exerciseKey(it.name) }
            .sortedWith(
                compareByDescending<CatalogExercise> { lastDone[exerciseKey(it.name)] ?: Long.MIN_VALUE }
                    .thenByDescending { equipmentUse[it.equipment] ?: 0 },
            )
        if (candidates.isEmpty()) return@flatMap emptyList()
        val start = (variant * perMuscle) % candidates.size
        (candidates.drop(start) + candidates.take(start)).take(perMuscle)
    }
    if (chosen.isEmpty()) return null

    val plan = Plan(
        id = makeId(),
        name = planName(focus),
        exercises = chosen.map { PlanExercise(id = makeId(), name = it.name, sets = sets, reps = reps) },
        updatedAt = now,
        warmUp = warmUp.copy(health = emptyMap()),
        mobility = mobility.copy(health = emptyMap()),
    )
    val workouts = mine.filter { it.at >= from }.map { it.workoutId }.distinct().size
    return PlanSuggestion(plan, focus, loads, days, workouts)
}

/** "Back, Biceps & Glutes" */
fun planName(muscles: List<Muscle>): String {
    val labels = muscles.map { it.label }
    return if (labels.size <= 1) labels.firstOrNull().orEmpty()
    else labels.dropLast(1).joinToString(", ") + " & " + labels.last()
}
