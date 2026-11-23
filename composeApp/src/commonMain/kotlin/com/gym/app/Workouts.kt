package com.gym.app

/**
 * A fresh copy of [source] to do again: same name and exercises (with their machine brand and
 * isometric type), new ids, nothing ticked. Brands the chosen gym doesn't have are dropped, and
 * per-exercise "done at another gym" isn't carried over.
 */
fun repeatOf(source: Workout, newWorkoutId: String, gymId: String, gym: GymLocation?, now: Long, newId: () -> String): Workout =
    Workout(
        id = newWorkoutId,
        planId = source.planId,
        name = source.name,
        gymId = gymId,
        exercises = source.exercises.map {
            it.copy(
                id = newId(),
                gymId = null,
                brand = it.brand?.takeIf { b -> gym?.brands?.contains(b) != false },
            )
        },
        startedAt = now,
        warmUp = source.warmUp,
        mobility = source.mobility,
    )
