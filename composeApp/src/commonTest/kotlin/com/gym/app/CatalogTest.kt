package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards the built-in exercise catalogue against duplicates and broken photo/instruction links. */
class CatalogTest {

    private val all = Catalog.exercises

    @Test
    fun namesAreUnique() {
        val dupes = all.groupBy { exerciseKey(it.name) }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate exercise names: $dupes")
    }

    @Test
    fun everyImageIdHasInstructions() {
        val missing = all.mapNotNull { it.imageId }.filter { it !in ExerciseInstructions.byId }
        assertTrue(missing.isEmpty(), "image ids without generated instructions: $missing")
    }

    @Test
    fun noPhotoEntriesHaveNoUrls() {
        all.filter { it.imageId in ExerciseInstructions.noPhotos }.forEach {
            assertTrue(it.imageUrls.isEmpty(), "${it.name} should not point at missing photos")
            assertEquals(null, it.thumbnailUrl)
        }
    }

    @Test
    fun photoUrlsArePinnedToACommit() {
        all.mapNotNull { it.thumbnailUrl }.forEach {
            assertTrue(it.startsWith("https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@"), it)
            assertTrue(!it.contains("@main") && !it.contains("@master"), "unpinned: $it")
        }
    }

    @Test
    fun findIsCaseInsensitive() {
        assertNotNull(Catalog.find("leg PRESS"))
        assertEquals(null, Catalog.find("Not a real exercise"))
    }

    @Test
    fun everyMuscleGroupExceptOtherHasExercises() {
        Muscle.entries.filter { it != Muscle.Other }.forEach { m ->
            assertTrue(all.any { it.muscle == m }, "no exercises for $m")
        }
    }

    @Test
    fun hasKettlebellExercises() {
        assertTrue(all.count { it.equipment == Equipment.Kettlebell } >= 15)
    }

    @Test
    fun customExerciseWithUnknownEnumsFallsBackToOther() {
        val c = CustomExercise(id = "x", name = "Sled push", muscle = "NotAMuscle", equipment = "Spaceship").toCatalog()
        assertEquals(Muscle.Other, c.muscle)
        assertEquals(Equipment.Other, c.equipment)
        assertTrue(c.custom)
        assertEquals("x", c.customId, "custom exercises keep their id so their photo can be found")
        assertTrue(c.imageUrls.isEmpty())
    }
}
