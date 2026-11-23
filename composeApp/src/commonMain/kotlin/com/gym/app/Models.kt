package com.gym.app

import kotlinx.serialization.Serializable

/*
 * Firestore layout
 *
 * users/{uid}                                   UserProfile
 * invites/{code}                                Invite -> which partnership a 6-letter code joins
 * partnerships/{pid}                            Partnership (the two training partners)
 * partnerships/{pid}/plans/{planId}             Plan
 * partnerships/{pid}/workouts/{workoutId}       Workout
 * partnerships/{pid}/workouts/{wid}/sets/{id}   WorkoutSet (one doc per set, so both phones can
 *                                               edit different sets at the same time without clobbering)
 * partnerships/{pid}/lastWeights/{uid}          LastWeights (pre-fills next session's weights)
 * partnerships/{pid}/exercises/{id}             CustomExercise (your own additions to the catalogue)
 * partnerships/{pid}/gyms/{id}                  GymLocation ("home" is created automatically)
 * partnerships/{pid}/machineNotes/{uid}         MachineNotes (seat/pad settings per machine)
 * partnerships/{pid}/progress/{wid_uid_exId}    ProgressPoint (one per person per exercise per workout)
 * partnerships/{pid}/exercisePhotos/{customId}  ExercisePhoto (photo for one of your own exercises)
 */

@Serializable
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val partnershipId: String? = null,
    /** Rest timer length after ticking a set; 0 = off. */
    val restSeconds: Int = 90,
)

@Serializable
data class Invite(val partnershipId: String = "")

@Serializable
data class Partnership(
    val id: String = "",
    val code: String = "",
    val members: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    /** Where you normally train; new workouts default to it. */
    val homeGymId: String = HOME_GYM_ID,
    /** True once the default gym has been created, so deleting it later doesn't bring it back. */
    val gymsSeeded: Boolean = false,
    /** Sets × reps given to an exercise when it's added to a plan or workout (shared by the pair). */
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
)

/** A gym you train at, and the machine brands it has (weights differ between brands). */
@Serializable
data class GymLocation(
    val id: String = "",
    val name: String = "",
    val brands: List<String> = emptyList(),
)

const val HOME_GYM_ID = "home"
val DEFAULT_HOME_GYM = GymLocation(HOME_GYM_ID, "Our gym", listOf("Technogym", "gym80"))
val BRAND_SUGGESTIONS = listOf(
    "Technogym", "gym80", "Life Fitness", "Hammer Strength", "Matrix", "Cybex", "Precor", "Panatta", "Nautilus", "Hoist",
)

@Serializable
data class PlanExercise(
    val id: String = "",
    val name: String = "",
    val sets: Int = 3,
    val reps: Int = 10,
    /** Preferred machine brand, e.g. "gym80"; null = not specified. */
    val brand: String? = null,
    /** Isometric version of the exercise (still logged as weight × reps). */
    val isometric: Boolean = false,
)

/**
 * A plan's Warm-up or Mobility section: a list of moves (exercise names, usually from
 * [MobilityCatalog]) done as a circuit, [rounds] times. Nothing is logged for them.
 */
@Serializable
data class Circuit(
    val exercises: List<String> = emptyList(),
    val rounds: Int = 1,
) {
    val isEmpty: Boolean get() = exercises.isEmpty()
}

@Serializable
data class Plan(
    val id: String = "",
    val name: String = "",
    val exercises: List<PlanExercise> = emptyList(),
    val updatedAt: Long = 0,
    /** Done before the exercises. */
    val warmUp: Circuit = Circuit(),
    /** Done after the exercises. */
    val mobility: Circuit = Circuit(),
)

@Serializable
data class WorkoutExercise(
    val id: String = "",
    val name: String = "",
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    /** Set when this exercise was done at a different gym from the rest of the workout. */
    val gymId: String? = null,
    /** Machine brand used, e.g. "Technogym". */
    val brand: String? = null,
    /** Isometric version of the exercise (still logged as weight × reps). */
    val isometric: Boolean = false,
)

@Serializable
data class Workout(
    val id: String = "",
    val planId: String? = null,
    val name: String = "",
    /** null on workouts from before gyms existed = the home gym. */
    val gymId: String? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    /** New personal bests set in this workout: uid -> exercise names. Filled in at finish. */
    val prs: Map<String, List<String>> = emptyMap(),
    /** Copied from the plan when the workout starts. */
    val warmUp: Circuit = Circuit(),
    val mobility: Circuit = Circuit(),
)

@Serializable
data class WorkoutSet(
    val id: String = "",
    val exerciseId: String = "",
    /** Whose set this is. Either partner may edit it. */
    val uid: String = "",
    val order: Int = 0,
    val weight: Double? = null,
    val reps: Int? = null,
    val done: Boolean = false,
    val updatedBy: String = "",
    val updatedAt: Long = 0,
)

/** An exercise one of you added that isn't in the built-in [Catalog]; shared by the pair. */
@Serializable
data class CustomExercise(
    val id: String = "",
    val name: String = "",
    /** [Muscle] enum name */
    val muscle: String = "Other",
    /** [Equipment] enum name */
    val equipment: String = "Other",
)

/**
 * Photo for one of your own exercises: a small JPEG (longest side 800 px), base64 in Firestore so
 * it syncs like everything else without needing Firebase Storage. Doc id = the CustomExercise id.
 */
@Serializable
data class ExercisePhoto(val jpegBase64: String = "")

@Serializable
data class LastWeights(
    /** Working weight to pre-fill, keyed by [weightKeys]. */
    val weights: Map<String, Double> = emptyMap(),
    /** What was actually done last time, for the "Last time" line; keyed by [weightKeys]. */
    val last: Map<String, Performance> = emptyMap(),
    /** Heaviest top set ever, per exact setup (most specific [weightKeys] key), for personal bests. */
    val best: Map<String, Double> = emptyMap(),
)

/**
 * One person's result for one exercise in one finished workout. Stored in
 * partnerships/{pid}/progress so the Progress charts don't need to read every workout's sets.
 */
@Serializable
data class ProgressPoint(
    val id: String = "",
    val workoutId: String = "",
    val uid: String = "",
    val exercise: String = "",
    val exerciseKey: String = "",
    /** Gym + brand + type, as in [weightKeys]; charts compare like with like. */
    val setupKey: String = "",
    val gymId: String = "",
    val brand: String? = null,
    val isometric: Boolean = false,
    val at: Long = 0,
    val topWeight: Double? = null,
    val topReps: Int? = null,
    val volume: Double = 0.0,
    val sets: Int = 0,
)

/** Per-person machine settings ("seat 4, back pad 2"), keyed like [weightKeys]. One doc per person. */
@Serializable
data class MachineNotes(val notes: Map<String, String> = emptyMap())

/** One set as remembered for "Last time". */
@Serializable
data class SetRecord(val weight: Double? = null, val reps: Int? = null)

/** The sets one person did for an exercise in their most recent workout with it. */
@Serializable
data class Performance(val sets: List<SetRecord> = emptyList(), val at: Long = 0)

fun exerciseKey(name: String) = name.trim().lowercase()

/**
 * Keys under which a working weight is remembered, most specific first: same gym and brand, then
 * same gym. Weights are never carried between gyms, since machines differ.
 */
fun weightKeys(name: String, gymId: String, brand: String?, isometric: Boolean = false): List<String> {
    // Isometric versions use different loads from the normal version, so they're remembered apart.
    val key = exerciseKey(name) + if (isometric) "~iso" else ""
    return listOfNotNull(brand?.let { "$key@$gymId@${it.trim().lowercase()}" }, "$key@$gymId")
}
