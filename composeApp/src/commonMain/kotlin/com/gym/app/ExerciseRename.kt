package com.gym.app

/**
 * Renaming one of your own exercises. Weights, notes and charts are keyed by the exercise name (see
 * [weightKeys]), so a rename moves those keys too; these helpers are the pure part of that.
 */

/** [key] under the new name if it belongs to [oldName] ("leg press~iso@home@gym80"), else unchanged. */
fun renameKey(key: String, oldName: String, newName: String): String {
    val old = exerciseKey(oldName)
    if (!key.startsWith(old)) return key
    val rest = key.substring(old.length)
    val ours = rest.isEmpty() || rest.startsWith("@") || rest == "~iso" || rest.startsWith("~iso@")
    return if (ours) exerciseKey(newName) + rest else key
}

fun <V> Map<String, V>.renameKeys(oldName: String, newName: String): Map<String, V> =
    mapKeys { renameKey(it.key, oldName, newName) }

fun LastWeights.renamed(oldName: String, newName: String) = LastWeights(
    weights = weights.renameKeys(oldName, newName),
    last = last.renameKeys(oldName, newName),
    best = best.renameKeys(oldName, newName),
)

private fun String.isExercise(name: String) = exerciseKey(this) == exerciseKey(name)

/** The workout with the exercise renamed (exercise list and personal bests), or null if it isn't in it. */
fun Workout.renamed(oldName: String, newName: String): Workout? {
    if (exercises.none { it.name.isExercise(oldName) }) return null
    return copy(
        exercises = exercises.map { if (it.name.isExercise(oldName)) it.copy(name = newName) else it },
        prs = prs.mapValues { (_, names) -> names.map { if (it.isExercise(oldName)) newName else it } },
    )
}

fun Plan.renamed(oldName: String, newName: String): Plan? {
    if (exercises.none { it.name.isExercise(oldName) }) return null
    return copy(exercises = exercises.map { if (it.name.isExercise(oldName)) it.copy(name = newName) else it })
}

fun ProgressPoint.renamed(oldName: String, newName: String) = copy(
    exercise = newName,
    exerciseKey = exerciseKey(newName),
    setupKey = renameKey(setupKey, oldName, newName),
)
