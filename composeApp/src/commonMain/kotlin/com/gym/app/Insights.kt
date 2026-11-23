package com.gym.app

import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * Pure logic behind the charts, History's calendar and search, and "where's my partner" — kept
 * free of UI and Firebase so it's unit tested.
 */

// ---- charts ----

/** What a progress chart plots. */
enum class Metric(val label: String, val unit: String) {
    TopSet("Top set", "kg"),
    OneRepMax("Est. 1-rep max", "kg"),
    Volume("Volume", "kg"),
}

/**
 * Estimated one-rep max (Epley: weight × (1 + reps / 30)), rounded to 0.5 kg. A single rep is the
 * max itself. Only an estimate, and less reliable above about 12 reps.
 */
fun estimatedOneRepMax(weight: Double, reps: Int): Double {
    if (reps <= 1) return weight
    return ((weight * (1 + reps / 30.0)) * 2).roundToInt() / 2.0
}

/** This session's value for [metric], or null if there's nothing to plot (e.g. bodyweight only). */
fun ProgressPoint.value(metric: Metric): Double? = when (metric) {
    Metric.TopSet -> topWeight
    Metric.OneRepMax -> topWeight?.let { estimatedOneRepMax(it, topReps ?: 1) }
    Metric.Volume -> volume.takeIf { it > 0 }
}

enum class ChartRange(val label: String, val days: Int?) {
    ThreeMonths("3 months", 91),
    Year("1 year", 365),
    All("All", null),
}

fun List<ProgressPoint>.within(range: ChartRange, now: Long): List<ProgressPoint> {
    val days = range.days ?: return this
    val from = now - days * 86_400_000L
    return filter { it.at >= from }
}

// ---- History: calendar and search ----

@OptIn(ExperimentalTime::class)
fun localDate(millis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date

/** The Monday of [date]'s week. */
fun weekStart(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

/** One calendar week: its Monday and how many workouts were started on each day (Mon..Sun). */
data class CalendarWeek(val monday: LocalDate, val perDay: List<Int>) {
    val total: Int get() = perDay.sum()
}

/** The last [weeks] weeks up to and including this one, oldest first. */
fun workoutCalendar(startedAt: List<Long>, now: Long, weeks: Int, tz: TimeZone = TimeZone.currentSystemDefault()): List<CalendarWeek> {
    val thisMonday = weekStart(localDate(now, tz))
    val counts = startedAt.groupingBy { localDate(it, tz) }.eachCount()
    return (weeks - 1 downTo 0).map { back ->
        val monday = thisMonday.minus(DatePeriod(days = 7 * back))
        CalendarWeek(monday, (0..6).map { d -> counts[monday.plus(DatePeriod(days = d))] ?: 0 })
    }
}

/** Does [workout] match a History search? Name, any exercise, or the gym; case-insensitive. */
fun Workout.matches(query: String, gymName: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return name.lowercase().contains(q) || gymName.lowercase().contains(q) ||
        exercises.any { it.name.lowercase().contains(q) }
}

// ---- partner's position in a live workout ----

/** "Leg press, set 3": what [member] is on in a running workout, and when they last touched it. */
data class Position(val exerciseId: String, val exerciseName: String, val setNumber: Int, val at: Long)

/**
 * Where [member] is in [workout], from the set they changed most recently (in the last [withinMs]).
 * Once that set is ticked, they've moved on to the next unticked set of the same person and
 * exercise, if there is one.
 */
fun positionOf(member: String, workout: Workout, sets: List<WorkoutSet>, now: Long, withinMs: Long = 15 * 60_000L): Position? {
    val last = sets.filter { it.updatedBy == member && now - it.updatedAt <= withinMs }.maxByOrNull { it.updatedAt } ?: return null
    val exercise = workout.exercises.firstOrNull { it.id == last.exerciseId } ?: return null
    val current = if (last.done) {
        sets.filter { it.exerciseId == last.exerciseId && it.uid == last.uid && !it.done && it.order > last.order }
            .minByOrNull { it.order } ?: last
    } else last
    return Position(exercise.id, exercise.name, current.order + 1, last.updatedAt)
}
