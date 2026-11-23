package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure logic that the rest of the app relies on: keys for remembered weights, number parsing, ids. */
class CoreLogicTest {

    @Test
    fun exerciseKeyIgnoresCaseAndSpacing() {
        assertEquals("leg press", exerciseKey("  Leg Press "))
        assertEquals(exerciseKey("Leg press"), exerciseKey("LEG PRESS"))
    }

    @Test
    fun weightKeysAreMostSpecificFirst() {
        assertEquals(
            listOf("leg press@home@gym80", "leg press@home"),
            weightKeys("Leg press", "home", "gym80"),
        )
        assertEquals(listOf("leg press@home"), weightKeys("Leg press", "home", null))
    }

    @Test
    fun weightKeysNormaliseBrandCase() {
        assertEquals(weightKeys("Leg press", "home", "gym80"), weightKeys("Leg press", "home", " GYM80 "))
    }

    @Test
    fun isometricWeightsAreKeptApartFromNormal() {
        val normal = weightKeys("Leg press", "home", "gym80")
        val iso = weightKeys("Leg press", "home", "gym80", isometric = true)
        assertTrue(normal.none { it in iso }, "isometric and normal keys must not overlap: $normal vs $iso")
    }

    @Test
    fun differentGymsNeverShareKeys() {
        val a = weightKeys("Leg press", "gymA", "Technogym")
        val b = weightKeys("Leg press", "gymB", "Technogym")
        assertTrue(a.none { it in b })
    }

    @Test
    fun parseWeightAcceptsCommaAndDot() {
        assertEquals(72.5, parseWeight("72.5"))
        assertEquals(72.5, parseWeight("72,5"))
        assertEquals(7.0, parseWeight("7."))
        assertNull(parseWeight(""))
        assertNull(parseWeight("."))
    }

    @Test
    fun formatWeightDropsTrailingZero() {
        assertEquals("80", formatWeight(80.0))
        assertEquals("72.5", formatWeight(72.5))
        assertEquals("", formatWeight(null))
    }

    @Test
    fun formatThenParseRoundTrips() {
        for (w in listOf(0.5, 1.25, 2.5, 20.0, 72.5, 102.5, 140.0, 999.5)) {
            assertEquals(w, parseWeight(formatWeight(w)), "round trip of $w")
        }
    }

    @Test
    fun inviteCodesAreSixUnambiguousCharacters() {
        repeat(500) {
            val code = newInviteCode()
            assertEquals(6, code.length)
            assertTrue(code.none { it in "01IO" }, "ambiguous character in $code")
            assertTrue(code.all { it.isUpperCase() || it.isDigit() })
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = List(5_000) { newId() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun errorsAreTranslatedForPeople() {
        assertEquals("Add an account first.", friendlyError(UserFacingException("Add an account first.")))
        assertTrue(friendlyError(Exception("PERMISSION_DENIED: Missing or insufficient permissions.")).contains("access"))
        assertTrue(friendlyError(Exception("Failed to get document because the client is offline.")).contains("connection"))
        assertTrue(friendlyError(Exception("UNAVAILABLE: io exception")).contains("connection"))
        val other = friendlyError(IllegalStateException("kotlinx.serialization weirdness"))
        assertTrue("serialization" !in other, "internal details must not leak: $other")
    }

    @Test
    fun durationFormatting() {
        assertEquals("0 min", formatDuration(0, 30_000))
        assertEquals("45 min", formatDuration(0, 45 * 60_000L))
        assertEquals("1 h 5 min", formatDuration(0, 65 * 60_000L))
        // A clock that went backwards (phone time change) must not show a negative duration.
        assertEquals("0 min", formatDuration(10_000, 0))
    }
}
