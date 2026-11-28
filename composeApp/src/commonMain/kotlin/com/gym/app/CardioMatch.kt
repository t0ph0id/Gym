package com.gym.app

/**
 * Matching watch sessions (via Health Connect) to a workout's warm-up and cool-down, so cardio
 * minutes, calories and heart rate fill themselves in. Kept free of the UI so it can be tested.
 */

/** A session that fills [move] (which has no minutes yet) in the warm-up or, if not, the cool-down. */
data class CardioFill(val warmUp: Boolean, val move: String, val session: HealthSession)

/** A cardio session with no matching move: offered as a move to add to the warm-up or cool-down. */
data class CardioExtra(val warmUp: Boolean, val session: HealthSession)

data class CardioMatches(val fills: List<CardioFill>, val extras: List<CardioExtra>)

/** Names that mean the same machine, so "Stair master" matches a "Stair climber" session. */
private val SAME_CARDIO = listOf(
    setOf("stair climber", "stair master", "stairmaster", "step mill", "stepmill", "stairs"),
    setOf("exercise bike", "stationary bike", "spin bike", "bike", "recumbent bike", "assault bike", "air bike"),
    setOf("rowing machine", "rower", "rowing", "row"),
    setOf("treadmill", "treadmill jog", "treadmill run", "treadmill walk", "incline walk"),
    setOf("elliptical", "cross trainer", "crosstrainer", "elliptical trainer"),
)

/** True when a move called [move] is the same cardio as a session of kind [cardio]. */
fun sameCardio(move: String, cardio: String): Boolean {
    val a = exerciseKey(move)
    val b = exerciseKey(cardio)
    return a == b || SAME_CARDIO.any { a in it && b in it }
}

/**
 * Which of [sessions] fill which warm-up/cool-down moves of [workout]. A session counts as warm-up
 * when it started before the middle of the weights (first to last ticked set), else as cool-down.
 * Each one fills the first move of its kind with no minutes yet, in its own section first. Sessions
 * whose kind is already in the workout with minutes are taken as logged already; the rest, with no
 * move of their kind at all, come back as [CardioMatches.extras], except those in [dismissed]
 * (session start times).
 */
fun matchCardio(
    workout: Workout,
    sets: List<WorkoutSet>,
    sessions: List<HealthSession>,
    now: Long,
    dismissed: Set<Long> = emptySet(),
): CardioMatches {
    val ticked = sets.filter { it.done }.map { it.updatedAt }.filter { it > 0 }
    val end = workout.finishedAt ?: now
    val middle = if (ticked.isEmpty()) (workout.startedAt + end) / 2 else (ticked.min() + ticked.max()) / 2
    var warmUp = workout.warmUp
    var coolDown = workout.mobility
    val fills = mutableListOf<CardioFill>()
    val extras = mutableListOf<CardioExtra>()
    for (session in sessions.filter { it.cardio != null }.sortedBy { it.start }) {
        val kind = session.cardio!!
        val early = session.start < middle
        fun open(c: Circuit) = c.exercises.firstOrNull { sameCardio(it, kind) && c.minutesFor(it) == null }
        val inOwn = open(if (early) warmUp else coolDown)
        val inOther = if (inOwn == null) open(if (early) coolDown else warmUp) else null
        val move = inOwn ?: inOther
        if (move != null) {
            val toWarmUp = if (inOwn != null) early else !early
            fills += CardioFill(toWarmUp, move, session)
            val stats = session.stats
            if (toWarmUp) warmUp = warmUp.withMinutes(move, session.minutes, stats)
            else coolDown = coolDown.withMinutes(move, session.minutes, stats)
        } else if ((warmUp.exercises + coolDown.exercises).none { sameCardio(it, kind) } && session.start !in dismissed) {
            extras += CardioExtra(early, session)
        }
    }
    return CardioMatches(fills, extras)
}
