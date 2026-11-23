package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class InsightsTest {
    private val utc = TimeZone.UTC
    private val day = 86_400_000L
    // Wednesday 18 Nov 2026, noon UTC
    private val wed = 1_795_003_200_000L

    @Test
    fun oneRepMaxUsesEpleyAndRoundsToHalfKilos() {
        assertEquals(100.0, estimatedOneRepMax(100.0, 1))
        assertEquals(133.5, estimatedOneRepMax(100.0, 10)) // 133.33 -> 133.5
        assertEquals(80.0, estimatedOneRepMax(80.0, 0))
    }

    @Test
    fun metricsPickTheRightNumber() {
        val p = ProgressPoint(topWeight = 60.0, topReps = 10, volume = 1500.0)
        assertEquals(60.0, p.value(Metric.TopSet))
        assertEquals(80.0, p.value(Metric.OneRepMax))
        assertEquals(1500.0, p.value(Metric.Volume))
        val bodyweight = ProgressPoint(topWeight = null, topReps = 15, volume = 0.0)
        assertNull(bodyweight.value(Metric.TopSet))
        assertNull(bodyweight.value(Metric.Volume))
    }

    @Test
    fun rangeKeepsRecentPoints() {
        val pts = listOf(ProgressPoint(id = "old", at = wed - 200 * day), ProgressPoint(id = "new", at = wed - 10 * day))
        assertEquals(listOf("new"), pts.within(ChartRange.ThreeMonths, wed).map { it.id })
        assertEquals(2, pts.within(ChartRange.All, wed).size)
    }

    @Test
    fun calendarWeeksStartOnMondayAndEndThisWeek() {
        assertEquals(LocalDate(2026, 11, 16), weekStart(localDate(wed, utc)))
        val cal = workoutCalendar(listOf(wed, wed + 1000, wed - 7 * day, wed - 30 * day), now = wed, weeks = 4, tz = utc)
        assertEquals(4, cal.size)
        assertEquals(LocalDate(2026, 11, 16), cal.last().monday)
        assertEquals(listOf(0, 0, 2, 0, 0, 0, 0), cal.last().perDay) // two on Wednesday
        assertEquals(1, cal[2].total)
        assertEquals(0, cal[0].total) // 30 days back is outside 4 weeks
    }

    @Test
    fun historySearchLooksAtNameExercisesAndGym() {
        val w = Workout(name = "Legs", exercises = listOf(WorkoutExercise(name = "Hack squat")))
        assertTrue(w.matches("", "Evo"))
        assertTrue(w.matches("HACK", "Evo"))
        assertTrue(w.matches("legs", "Evo"))
        assertTrue(w.matches("evo", "Evo Wiedikon"))
        assertFalse(w.matches("bench", "Evo"))
    }

    @Test
    fun partnerPositionFollowsTheirLatestTick() {
        val w = Workout(exercises = listOf(WorkoutExercise(id = "lp", name = "Leg press")))
        fun s(order: Int, done: Boolean, at: Long, by: String = "g") =
            WorkoutSet(id = "s$order", exerciseId = "lp", uid = "g", order = order, done = done, updatedBy = by, updatedAt = at)
        val now = 10_000_000L
        // Ticked set 2 -> on to set 3.
        val sets = listOf(s(0, true, now - 300_000), s(1, true, now - 60_000), s(2, false, 0, by = "c"))
        val pos = positionOf("g", w, sets, now)!!
        assertEquals("Leg press", pos.exerciseName)
        assertEquals(3, pos.setNumber)
        // Typing in set 1 (not ticked) -> set 1.
        assertEquals(1, positionOf("g", w, listOf(s(0, false, now - 1000)), now)!!.setNumber)
        // Nothing recent -> unknown.
        assertNull(positionOf("g", w, listOf(s(0, true, now - 60 * 60_000)), now))
        // Last set of the exercise ticked -> stays on it.
        assertEquals(1, positionOf("g", w, listOf(s(0, true, now - 1000)), now)!!.setNumber)
    }
}
