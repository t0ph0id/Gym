package com.gym.app

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FieldsAndValuesUpdateDSL
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.WriteBatch
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * All Firestore access. Reads are live `snapshots` flows: Firestore pushes every change from either
 * phone to the other within a second or so, and serves the local cache while offline.
 */
class GymRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private fun userRef(uid: String) = db.collection("users").document(uid)
    private fun inviteRef(code: String) = db.collection("invites").document(code)
    private fun pairRef(pid: String) = db.collection("partnerships").document(pid)
    private fun plansRef(pid: String) = pairRef(pid).collection("plans")
    private fun workoutsRef(pid: String) = pairRef(pid).collection("workouts")
    private fun setsRef(pid: String, wid: String) = workoutsRef(pid).document(wid).collection("sets")
    private fun lastWeightsRef(pid: String) = pairRef(pid).collection("lastWeights")
    private fun customExercisesRef(pid: String) = pairRef(pid).collection("exercises")
    private fun gymsRef(pid: String) = pairRef(pid).collection("gyms")
    private fun notesRef(pid: String) = pairRef(pid).collection("machineNotes")
    private fun progressRef(pid: String) = pairRef(pid).collection("progress")
    private fun photosRef(pid: String) = pairRef(pid).collection("exercisePhotos")
    private fun recipesRef(pid: String) = pairRef(pid).collection("recipes")

    // ---- auth (Google accounts, so data follows you to a new phone) ----

    /** The signed-in user's uid, or null when signed out. Emits on every change. */
    val authState: Flow<String?> = auth.authStateChanged.map { it?.uid }

    suspend fun signInWithGoogle(tokens: GoogleTokens) {
        auth.signInWithCredential(GoogleAuthProvider.credential(tokens.idToken, tokens.accessToken))
    }

    fun googleDisplayName(): String = auth.currentUser?.displayName.orEmpty()

    fun googleEmail(): String = auth.currentUser?.email.orEmpty()

    suspend fun signOut() = auth.signOut()

    // ---- profile & pairing ----

    fun profile(uid: String): Flow<UserProfile?> =
        userRef(uid).snapshots
            // On a fresh install the empty local cache answers first; wait for the server so a
            // returning user isn't briefly shown the "new pair" screen.
            .filter { it.exists || !it.metadata.isFromCache }
            .map { if (it.exists) it.data<UserProfile>() else null }

    fun partnership(pid: String): Flow<Partnership?> =
        pairRef(pid).snapshots.map { if (it.exists) it.data<Partnership>() else null }

    /** Creates a new pair with just this user in it and returns its invite code. */
    suspend fun createPartnership(uid: String, name: String): String {
        var code: String
        do {
            code = newInviteCode()
        } while (inviteRef(code).get().exists)
        val pid = newId()
        pairRef(pid).set(
            Partnership(id = pid, code = code, members = listOf(uid), names = mapOf(uid to name), gymsSeeded = true)
        )
        gymsRef(pid).document(HOME_GYM_ID).set(DEFAULT_HOME_GYM)
        inviteRef(code).set(Invite(pid))
        userRef(uid).set(UserProfile(uid = uid, name = name, partnershipId = pid))
        return code
    }

    /** Returns null on success, or a message to show. */
    suspend fun joinPartnership(uid: String, name: String, rawCode: String): String? {
        val code = rawCode.trim().uppercase()
        val invite = inviteRef(code).get()
        if (!invite.exists) return "No pair found with code $code"
        val pid = invite.data<Invite>().partnershipId
        val snap = pairRef(pid).get()
        if (!snap.exists) return "That pair no longer exists"
        val pair = snap.data<Partnership>()
        if (uid !in pair.members) {
            if (pair.members.size >= 2) return "That pair already has two people"
            // Only touch members/names: the security rules reject a newcomer changing anything else.
            val members: List<String> = pair.members + uid
            val names: Map<String, String> = pair.names + Pair(uid, name)
            pairRef(pid).updateFields {
                "members" to members
                "names" to names
            }
        }
        userRef(uid).set(UserProfile(uid = uid, name = name, partnershipId = pid))
        return null
    }

    /**
     * Leaves the pair and retires its invite code, so the old code can't be used to join and read
     * the remaining partner's history. Whoever stays gets a fresh code to invite someone new.
     */
    suspend fun leavePartnership(uid: String, profile: UserProfile) {
        val pid = profile.partnershipId ?: return
        val snap = pairRef(pid).get(Source.SERVER)
        if (snap.exists) {
            val pair = snap.data<Partnership>()
            val remaining = pair.members - uid
            val batch = db.batch()
            if (pair.code.isNotEmpty()) batch.delete(inviteRef(pair.code))
            var newCode = ""
            if (remaining.isNotEmpty()) {
                do {
                    newCode = newInviteCode()
                } while (inviteRef(newCode).get(Source.SERVER).exists)
                batch.set(inviteRef(newCode), Invite(pid))
            }
            val names: Map<String, String> = pair.names - uid
            batch.updateFields(pairRef(pid)) {
                "members" to remaining
                "code" to newCode
                "names" to names
            }
            batch.commit()
        }
        userRef(uid).set(profile.copy(partnershipId = null))
    }

    // ---- plans ----

    fun plans(pid: String): Flow<List<Plan>> =
        plansRef(pid).snapshots.map { q -> q.documents.map { it.data<Plan>() }.sortedBy { it.name.lowercase() } }

    suspend fun savePlan(pid: String, plan: Plan) = plansRef(pid).document(plan.id).set(plan)

    suspend fun deletePlan(pid: String, planId: String) = plansRef(pid).document(planId).delete()

    // ---- workouts ----

    /** The newest [limit] workouts (History pages in more on request). */
    fun workouts(pid: String, limit: Int): Flow<List<Workout>> =
        workoutsRef(pid).orderBy("startedAt", Direction.DESCENDING).limit(limit).snapshots
            .map { q -> q.documents.map { it.data<Workout>() }.sortedByDescending { it.startedAt } }

    /** Current sets straight from Firestore (server, or the local cache when offline). */
    suspend fun getSets(pid: String, wid: String): List<WorkoutSet> =
        setsRef(pid, wid).get().documents.map { it.data<WorkoutSet>() }

    /** One workout as it is now (server, or the local cache when offline). */
    suspend fun getWorkout(pid: String, wid: String): Workout? =
        workoutsRef(pid).document(wid).get().let { if (it.exists) it.data<Workout>() else null }

    fun workout(pid: String, wid: String): Flow<Workout?> =
        workoutsRef(pid).document(wid).snapshots.map { if (it.exists) it.data<Workout>() else null }

    fun sets(pid: String, wid: String): Flow<List<WorkoutSet>> =
        setsRef(pid, wid).snapshots.map { q -> q.documents.map { it.data<WorkoutSet>() }.sortedBy { it.order } }

    fun lastWeights(pid: String): Flow<Map<String, LastWeights>> =
        lastWeightsRef(pid).snapshots.map { q -> q.documents.associate { it.id to it.data<LastWeights>() } }

    /**
     * Writes the workout doc plus any sets in one batch. Used to start a workout, add exercises,
     * and change gym/brand (which re-fills weights).
     */
    suspend fun saveWorkoutAndSets(pid: String, workout: Workout, sets: List<WorkoutSet>) {
        val batch = db.batch()
        batch.set(workoutsRef(pid).document(workout.id), workout)
        for (s in sets) batch.set(setsRef(pid, workout.id).document(s.id), s)
        batch.commit()
    }

    /**
     * Adds exercises with arrayUnion, so two phones adding at the same moment both keep theirs
     * (rewriting the whole workout would let one overwrite the other).
     */
    suspend fun addExercisesToWorkout(pid: String, wid: String, exercises: List<WorkoutExercise>, sets: List<WorkoutSet>) {
        val batch = db.batch()
        val maps = exercises.map { it.toFirestoreMap() }.toTypedArray<Any>()
        batch.updateFields(workoutsRef(pid).document(wid)) { "exercises" to FieldValue.arrayUnion(*maps) }
        for (s in sets) batch.set(setsRef(pid, wid).document(s.id), s)
        batch.commit()
    }

    /**
     * Changes only the given fields of a workout (plus writes/deletes some sets) in one batch, so
     * a partner's simultaneous change to other fields isn't lost.
     */
    suspend fun updateWorkout(
        pid: String,
        wid: String,
        sets: List<WorkoutSet> = emptyList(),
        deleteSetIds: List<String> = emptyList(),
        fields: FieldsAndValuesUpdateDSL.() -> Unit,
    ) {
        val batch = db.batch()
        batch.updateFields(workoutsRef(pid).document(wid), fields)
        for (s in sets) batch.set(setsRef(pid, wid).document(s.id), s)
        for (id in deleteSetIds) batch.delete(setsRef(pid, wid).document(id))
        batch.commit()
    }

    // ---- gyms ----

    fun gyms(pid: String): Flow<List<GymLocation>> =
        gymsRef(pid).snapshots.map { q -> q.documents.map { it.data<GymLocation>() } }

    /**
     * For pairs created before gyms existed: creates "Our gym" (Technogym + gym80) exactly once.
     * Reads from the server only (never a possibly-empty offline cache) and records that it's done,
     * so a gym you later delete is never recreated.
     */
    suspend fun ensureHomeGym(pid: String) {
        val pairSnap = pairRef(pid).get(Source.SERVER)
        if (!pairSnap.exists || pairSnap.data<Partnership>().gymsSeeded) return
        if (gymsRef(pid).get(Source.SERVER).documents.isEmpty()) {
            gymsRef(pid).document(HOME_GYM_ID).set(DEFAULT_HOME_GYM)
        }
        pairRef(pid).updateFields { "gymsSeeded" to true }
    }

    suspend fun saveGym(pid: String, gym: GymLocation) = gymsRef(pid).document(gym.id).set(gym)

    suspend fun deleteGym(pid: String, gymId: String) = gymsRef(pid).document(gymId).delete()

    suspend fun setDefaultSetsReps(pid: String, sets: Int, reps: Int) {
        pairRef(pid).updateFields {
            "defaultSets" to sets
            "defaultReps" to reps
        }
    }

    // Just this field, so a simultaneous rename or join isn't overwritten.
    suspend fun setHomeGym(pid: String, gymId: String) = pairRef(pid).updateFields { "homeGymId" to gymId }

    // ---- machine notes ----

    fun machineNotes(pid: String): Flow<Map<String, Map<String, String>>> =
        notesRef(pid).snapshots.map { q -> q.documents.associate { it.id to it.data<MachineNotes>().notes } }

    suspend fun saveMachineNotes(pid: String, uid: String, notes: Map<String, String>) =
        notesRef(pid).document(uid).set(MachineNotes(notes))

    suspend fun saveProfile(profile: UserProfile) = userRef(profile.uid).set(profile)

    // ---- photos for custom exercises ----

    fun exercisePhotos(pid: String): Flow<Map<String, String>> =
        photosRef(pid).snapshots.map { q -> q.documents.associate { it.id to it.data<ExercisePhoto>().jpegBase64 } }

    suspend fun saveExercisePhoto(pid: String, customId: String, jpegBase64: String) =
        photosRef(pid).document(customId).set(ExercisePhoto(jpegBase64))

    suspend fun deleteExercisePhoto(pid: String, customId: String) = photosRef(pid).document(customId).delete()

    // ---- recipes ----

    /** Newest first. */
    fun recipes(pid: String): Flow<List<Recipe>> =
        recipesRef(pid).snapshots.map { q -> q.documents.map { it.data<Recipe>() }.sortedByDescending { it.addedAt } }

    suspend fun saveRecipe(pid: String, recipe: Recipe) = recipesRef(pid).document(recipe.id).set(recipe)

    suspend fun setRecipeFavourite(pid: String, id: String, favourite: Boolean) =
        recipesRef(pid).document(id).updateFields { "favourite" to favourite }

    /** Only the preview fields, so a note or tags typed meanwhile on the other phone are kept. */
    suspend fun setRecipePreview(pid: String, id: String, title: String, site: String, imageUrl: String?) =
        recipesRef(pid).document(id).updateFields {
            "title" to title
            "site" to site
            "imageUrl" to imageUrl
        }

    suspend fun deleteRecipe(pid: String, id: String) = recipesRef(pid).document(id).delete()

    // ---- custom exercises ----

    fun customExercises(pid: String): Flow<List<CustomExercise>> =
        customExercisesRef(pid).snapshots.map { q -> q.documents.map { it.data<CustomExercise>() } }

    /** Removes one of your own exercises and its photo (past workouts keep the name). */
    suspend fun deleteCustomExercise(pid: String, id: String) {
        val batch = db.batch()
        batch.delete(customExercisesRef(pid).document(id))
        batch.delete(photosRef(pid).document(id))
        batch.commit()
    }

    suspend fun saveCustomExercise(pid: String, exercise: CustomExercise) =
        customExercisesRef(pid).document(exercise.id).set(exercise)

    /**
     * Saves one of your own exercises under a new name and moves everything stored under the old
     * name with it: plans, past workouts and their personal bests, remembered weights, machine notes
     * and progress records. Rare, so it simply reads them all.
     */
    suspend fun renameCustomExercise(pid: String, exercise: CustomExercise, oldName: String) {
        val newName = exercise.name
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        writes += { it.set(customExercisesRef(pid).document(exercise.id), exercise) }
        plansRef(pid).get().documents.forEach { doc ->
            doc.data<Plan>().renamed(oldName, newName)?.let { p -> writes += { it.set(doc.reference, p) } }
        }
        workoutsRef(pid).get().documents.forEach { doc ->
            doc.data<Workout>().renamed(oldName, newName)?.let { w ->
                writes += {
                    it.updateFields(doc.reference) {
                        exercises(w.exercises)
                        "warmUp".to(Circuit.serializer(), w.warmUp)
                        "mobility".to(Circuit.serializer(), w.mobility)
                        "prs".to(MapSerializer(String.serializer(), ListSerializer(String.serializer())), w.prs)
                    }
                }
            }
        }
        lastWeightsRef(pid).get().documents.forEach { doc ->
            val before = doc.data<LastWeights>()
            val after = before.renamed(oldName, newName)
            if (after != before) writes += { it.set(doc.reference, after) }
        }
        notesRef(pid).get().documents.forEach { doc ->
            val before = doc.data<MachineNotes>()
            val after = MachineNotes(before.notes.renameKeys(oldName, newName))
            if (after != before) writes += { it.set(doc.reference, after) }
        }
        progressRef(pid).where { "exerciseKey" equalTo exerciseKey(oldName) }.get().documents.forEach { doc ->
            val p = doc.data<ProgressPoint>().renamed(oldName, newName)
            writes += { it.set(doc.reference, p) }
        }
        // A batch holds at most 500 writes.
        writes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit()
        }
    }

    suspend fun saveSet(pid: String, wid: String, set: WorkoutSet) =
        setsRef(pid, wid).document(set.id).set(set)

    suspend fun deleteSet(pid: String, wid: String, setId: String) =
        setsRef(pid, wid).document(setId).delete()

    /** Marks the workout finished; stores each partner's memory and the progress records. */
    suspend fun finishWorkout(
        pid: String,
        workout: Workout,
        weightsByUid: Map<String, LastWeights>,
        progress: List<ProgressPoint>,
    ) {
        val batch = db.batch()
        // Only these two fields: a full rewrite could drop an exercise the partner just added.
        batch.updateFields(workoutsRef(pid).document(workout.id)) {
            "finishedAt" to workout.finishedAt
            "prs".to(MapSerializer(String.serializer(), ListSerializer(String.serializer())), workout.prs)
        }
        for ((uid, weights) in weightsByUid) {
            batch.set(lastWeightsRef(pid).document(uid), weights)
        }
        for (p in progress) batch.set(progressRef(pid).document(p.id), p)
        batch.commit()
    }

    /** Every progress record of the pair, once (for recalculating a corrected workout). */
    suspend fun getProgress(pid: String): List<ProgressPoint> =
        progressRef(pid).get().documents.map { it.data<ProgressPoint>() }

    /** Writes what fixing a finished workout changed (see [WeightMemory.refinish]). */
    suspend fun refinishWorkout(
        pid: String,
        wid: String,
        prs: Map<String, List<String>>,
        weightsByUid: Map<String, LastWeights>,
        upserts: List<ProgressPoint>,
        deleteIds: List<String>,
    ) {
        val batch = db.batch()
        batch.updateFields(workoutsRef(pid).document(wid)) {
            "prs".to(MapSerializer(String.serializer(), ListSerializer(String.serializer())), prs)
        }
        for ((uid, weights) in weightsByUid) batch.set(lastWeightsRef(pid).document(uid), weights)
        for (p in upserts) batch.set(progressRef(pid).document(p.id), p)
        for (id in deleteIds) batch.delete(progressRef(pid).document(id))
        batch.commit()
    }

    fun progress(pid: String): Flow<List<ProgressPoint>> =
        progressRef(pid).snapshots.map { q -> q.documents.map { it.data<ProgressPoint>() }.sortedBy { it.at } }

    /** New display name on your profile and in the pair (members may update their own pair). */
    suspend fun renameSelf(uid: String, name: String, profile: UserProfile) {
        userRef(uid).set(profile.copy(name = name))
        val pid = profile.partnershipId ?: return
        val snap = pairRef(pid).get()
        if (!snap.exists) return
        val names: Map<String, String> = snap.data<Partnership>().names + Pair(uid, name)
        pairRef(pid).updateFields { "names" to names }
    }

    /** Deletes the workout with its sets and progress records. */
    suspend fun deleteWorkout(pid: String, wid: String) {
        val batch = db.batch()
        val sets = setsRef(pid, wid).get().documents
        // Progress record ids are "<workout>_<person>_<exercise>", so they can be removed without a query.
        sets.map { it.data<WorkoutSet>() }.map { it.uid to it.exerciseId }.distinct().forEach { (uid, exId) ->
            batch.delete(progressRef(pid).document("${wid}_${uid}_$exId"))
        }
        sets.forEach { batch.delete(it.reference) }
        batch.delete(workoutsRef(pid).document(wid))
        batch.commit()
    }
}

/** Exercises list as written into a workout document. */
fun FieldsAndValuesUpdateDSL.exercises(list: List<WorkoutExercise>) =
    "exercises".to(ListSerializer(WorkoutExercise.serializer()), list)

/** Firestore-native form for arrayUnion (which takes raw maps, not @Serializable classes). */
internal fun WorkoutExercise.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "targetSets" to targetSets,
    "targetReps" to targetReps,
    "gymId" to gymId,
    "brand" to brand,
    "isometric" to isometric,
    "grip" to grip,
)
