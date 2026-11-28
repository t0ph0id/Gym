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
 * partnerships/{pid}/recipes/{id}              Recipe (a shared link to a recipe)
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
    /** Grip or handle, e.g. "Wide grip" or "V-handle"; null = not set. Weights are kept per grip. */
    val grip: String? = null,
)

/**
 * A plan's or workout's Warm-up or Cool-down section: a list of moves (exercise names, usually from
 * [MobilityCatalog]) done as a circuit, [rounds] times. Only minutes are logged for them (cardio).
 */
@Serializable
data class Circuit(
    val exercises: List<String> = emptyList(),
    val rounds: Int = 1,
    /** Minutes per move, keyed by [exerciseKey] of its name; e.g. "elliptical" -> 10. Mostly cardio. */
    val minutes: Map<String, Int> = emptyMap(),
    /** Calories and heart rate per move, when its minutes came from a watch session; same keys. */
    val health: Map<String, HealthStats> = emptyMap(),
) {
    val isEmpty: Boolean get() = exercises.isEmpty()

    fun minutesFor(name: String): Int? = minutes[exerciseKey(name)]

    fun healthFor(name: String): HealthStats? = health[exerciseKey(name)]

    /**
     * With [name]'s minutes set, or cleared when null. Typed minutes drop any watch figures, which
     * belonged to the old minutes; [stats] gives the new ones (from a watch session).
     */
    fun withMinutes(name: String, value: Int?, stats: HealthStats? = null): Circuit {
        val key = exerciseKey(name)
        return copy(
            minutes = if (value == null) minutes - key else minutes + (key to value),
            health = if (value == null || stats == null || stats.isEmpty) health - key else health + (key to stats),
        )
    }

    /** Without the move at [index] (and its minutes, unless the same move is in it twice). */
    fun without(index: Int): Circuit {
        val name = exercises.getOrNull(index) ?: return this
        val rest = exercises.filterIndexed { i, _ -> i != index }
        val key = exerciseKey(name)
        if (rest.any { exerciseKey(it) == key }) return copy(exercises = rest)
        return copy(exercises = rest, minutes = minutes - key, health = health - key)
    }

    /** Minutes over all rounds. */
    val totalMinutes: Int get() = exercises.sumOf { minutesFor(it) ?: 0 } * rounds
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
    /** Grip or handle, e.g. "Wide grip" or "V-handle"; null = not set. Weights are kept per grip. */
    val grip: String? = null,
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
    /**
     * Who's training in it. Empty = both partners (every workout from before "Just me" existed).
     * A "Just me" workout lists one person; the other can join later.
     */
    val people: List<String> = emptyList(),
    /** Calories and heart rate for the whole visit, from each person's watch: uid -> figures. */
    val health: Map<String, HealthStats> = emptyMap(),
) {
    val isSolo: Boolean get() = people.size == 1

    /** [pair] (me first) narrowed to the people in this workout, keeping the order. */
    fun peopleIn(pair: List<String>): List<String> = if (people.isEmpty()) pair else pair.filter { it in people }

    fun includes(member: String): Boolean = people.isEmpty() || member in people
}

/**
 * Figures from a watch (via Health Connect): calories and heart rate. [uid] is whose watch, since a
 * shared warm-up can be filled in by either of you.
 */
@Serializable
data class HealthStats(
    val kcal: Int? = null,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val uid: String = "",
) {
    val isEmpty: Boolean get() = kcal == null && avgBpm == null && maxBpm == null

    /** "48 kcal · avg 132 bpm" (max too with [withMax]) */
    fun label(withMax: Boolean = false): String = listOfNotNull(
        kcal?.let { "$it kcal" },
        avgBpm?.let { "avg $it bpm" },
        maxBpm?.takeIf { withMax }?.let { "max $it" },
    ).joinToString(" · ")
}

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
    val grip: String? = null,
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
fun weightKeys(name: String, gymId: String, brand: String?, isometric: Boolean = false, grip: String? = null): List<String> {
    // Isometric versions and other grips use different loads, so they're remembered apart.
    val key = exerciseKey(name) + (if (isometric) "~iso" else "") + (gripKey(grip)?.let { "~$it" } ?: "")
    return listOfNotNull(brand?.let { "$key@$gymId@${it.trim().lowercase()}" }, "$key@$gymId")
}

/** [grip] normalised for keys ("wide grip"), or null when it isn't set. */
fun gripKey(grip: String?): String? = grip?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

/** "Seated cable row (wide grip, isometric)": the name plus whatever makes it a different version. */
fun exerciseLabel(name: String, isometric: Boolean = false, grip: String? = null): String {
    val extra = listOfNotNull(gripKey(grip), "isometric".takeIf { isometric })
    return if (extra.isEmpty()) name else "$name (${extra.joinToString(", ")})"
}

/** Grips and handles offered as chips; any other can be typed. */
val GRIP_SUGGESTIONS = listOf(
    "Narrow grip", "Wide grip", "Neutral grip", "Underhand", "V-handle", "Rope", "Straight bar", "Single handle",
)

/** A recipe link the pair shares: the page's preview (title, picture, site) plus your own note and tags. */
@Serializable
data class Recipe(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    val site: String = "",
    val imageUrl: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val favourite: Boolean = false,
    val addedBy: String = "",
    val addedAt: Long = 0,
)

val RECIPE_TAG_SUGGESTIONS = listOf(
    "Breakfast", "Lunch", "Dinner", "Snack", "Post-workout", "High protein", "Meal prep", "Vegetarian", "Dessert",
)
