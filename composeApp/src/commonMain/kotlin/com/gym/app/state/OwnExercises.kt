package com.gym.app.state

import com.gym.app.Catalog
import com.gym.app.MobilityCatalog
import com.gym.app.CustomExercise
import com.gym.app.Equipment
import com.gym.app.Muscle
import com.gym.app.exerciseKey
import com.gym.app.newId
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exercises you've added to the shared catalogue yourselves, and their photos. */
class OwnExercises internal constructor(private val ctx: VmContext) {

    val list: StateFlow<List<CustomExercise>> = ctx.perPair(emptyList()) { ctx.repo.customExercises(it) }

    /** Photos by CustomExercise id (decoded JPEG bytes). */
    @OptIn(ExperimentalEncodingApi::class)
    val photos: StateFlow<Map<String, ByteArray>> = ctx.perPair(emptyMap()) { ctx.repo.exercisePhotos(it) }
        .map { encoded -> encoded.mapNotNull { (id, b64) -> runCatching { id to Base64.decode(b64) }.getOrNull() }.toMap() }
        .flowOn(Dispatchers.Default) // decoding can be ~100 KB per photo: keep it off the UI thread
        .stateIn(ctx.scope, SharingStarted.Eagerly, emptyMap())

    fun add(name: String, muscle: Muscle, equipment: Equipment, photo: ByteArray? = null) {
        val pid = ctx.currentPid
        // "sled push" -> "Sled push", to match the built-in names.
        val clean = name.trim().replaceFirstChar { it.uppercase() }
        val exercise = CustomExercise(id = newId(), name = clean, muscle = muscle.name, equipment = equipment.name)
        ctx.task { ctx.repo.saveCustomExercise(pid, exercise) }
        if (photo != null) setPhoto(exercise.id, photo)
    }

    /**
     * Changes one of your exercises. A new name also moves its plans, history, remembered weights,
     * notes and charts over; returns a message instead if the name is already taken.
     */
    fun edit(customId: String, name: String, muscle: Muscle, equipment: Equipment): String? {
        val old = list.value.firstOrNull { it.id == customId } ?: return null
        val clean = name.trim().take(40).replaceFirstChar { it.uppercase() }
        if (clean.isEmpty()) return "Give it a name"
        val renamed = exerciseKey(clean) != exerciseKey(old.name)
        if (renamed && (Catalog.find(clean) != null || MobilityCatalog.find(clean) != null || list.value.any { exerciseKey(it.name) == exerciseKey(clean) })) {
            return "There's already an exercise called $clean"
        }
        val updated = old.copy(name = clean, muscle = muscle.name, equipment = equipment.name)
        if (updated == old) return null
        val pid = ctx.currentPid
        ctx.task {
            if (renamed) ctx.repo.renameCustomExercise(pid, updated, old.name)
            else ctx.repo.saveCustomExercise(pid, updated) // same name (maybe other capitals): nothing to move
        }
        return null
    }

    fun delete(customId: String) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.deleteCustomExercise(pid, customId) }
    }

    /** Sets (or with null, removes) an exercise's photo. */
    @OptIn(ExperimentalEncodingApi::class)
    fun setPhoto(customId: String, jpeg: ByteArray?) {
        val pid = ctx.currentPid
        ctx.task {
            if (jpeg == null) ctx.repo.deleteExercisePhoto(pid, customId)
            else ctx.repo.saveExercisePhoto(pid, customId, Base64.encode(jpeg))
        }
    }
}
