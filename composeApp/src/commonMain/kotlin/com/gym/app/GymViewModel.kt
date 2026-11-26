package com.gym.app

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gym.app.state.Gyms
import com.gym.app.state.OwnExercises
import com.gym.app.state.Recipes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gym.app.state.RestTimer
import com.gym.app.state.VmContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class EditPlan(val planId: String?) : Screen
    data class Workout(val workoutId: String) : Screen
    data object Gyms : Screen
    data object Settings : Screen
    data class Progress(val exerciseKey: String) : Screen
}

data class Session(
    val loading: Boolean = true,
    /** null once loaded means signed out. */
    val uid: String? = null,
    val profile: UserProfile? = null,
)

/** A snackbar message with an Undo button. */
class UndoNotice(val text: String, val undo: () -> Unit)

/** Wraps a nullable result so `null` from collectAsState can mean "still loading". */
data class Found<T>(val value: T?)

private const val HISTORY_PAGE = 30

@OptIn(ExperimentalCoroutinesApi::class)
class GymViewModel(private val repo: GymRepository = GymRepository()) : ViewModel() {

    val message = MutableStateFlow<String?>(null)
    /** Something just removed that can be put back (shown as a snackbar with Undo). */
    val undoNotice = MutableStateFlow<UndoNotice?>(null)
    val busy = MutableStateFlow(false)
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val session: StateFlow<Session> = repo.authState.distinctUntilChanged().flatMapLatest { u ->
        if (u == null) flowOf(Session(loading = false))
        else repo.profile(u).map { Session(loading = false, uid = u, profile = it) }
            .catch { e -> message.value = friendlyError(e); emit(Session(loading = false, uid = u)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Session())

    val uid: String get() = session.value.uid.orEmpty()

    /** Pre-fills the name box when setting up a pair. */
    fun googleDisplayName(): String = repo.googleDisplayName().substringBefore(' ')

    fun googleEmail(): String = repo.googleEmail()

    // ---- sign-in ----

    fun signInWithGoogle(googleAuth: GoogleAuth) = busyTask {
        val tokens = googleAuth.signIn() ?: return@busyTask // user backed out of the picker
        repo.signInWithGoogle(tokens)
    }

    fun signOut(googleAuth: GoogleAuth, platform: AppPlatform) {
        stack.clear(); stack.add(Screen.Home)
        task {
            googleAuth.signOut()
            repo.signOut()
            platform.clearLocalDataAndRestart()
        }
    }

    private val pid: Flow<String?> = session.map { it.profile?.partnershipId }.distinctUntilChanged()
    private val currentPid: String get() = session.value.profile?.partnershipId.orEmpty()
    private val ctx = VmContext(viewModelScope, repo, message, pid) { currentPid }

    val partnership: StateFlow<Partnership?> = ctx.perPair(null) { repo.partnership(it) }

    val plans: StateFlow<List<Plan>> = ctx.perPair(emptyList()) { repo.plans(it) }

    // The parts with their own files (see the state package).
    val gyms = Gyms(ctx, partnership)
    val rest = RestTimer(ctx) { session.value.profile }
    val ownExercises = OwnExercises(ctx)
    val recipes = Recipes(ctx, { uid }) { undoNotice.value = it }

    /** Home's selected tab (0 Plans, 1 History, 2 Progress, 3 Recipes); kept while you visit other screens. */
    var homeTab by mutableIntStateOf(0)

    /** Back to Home on the Recipes tab (after sharing a link into the app). */
    fun showRecipes() {
        stack.clear(); stack.add(Screen.Home)
        homeTab = 3
    }

    /** How many recent workouts to keep in sync; History's "Show older" raises it. */
    val historyLimit = MutableStateFlow(HISTORY_PAGE)

    val workouts: StateFlow<List<Workout>> = combine(pid, historyLimit) { p, n -> p to n }
        .distinctUntilChanged()
        .flatMapLatest { (p, n) -> if (p == null) flowOf(emptyList()) else with(ctx) { repo.workouts(p, n).safe(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun showOlderWorkouts() {
        historyLimit.value += HISTORY_PAGE
    }

    private val lastWeights: StateFlow<Map<String, LastWeights>> = ctx.perPair(emptyMap()) { repo.lastWeights(it) }

    private val machineNotes: StateFlow<Map<String, Map<String, String>>> = ctx.perPair(emptyMap()) { repo.machineNotes(it) }

    /** Remembered weights, "Last time" and machine notes; the UI collects it so it updates live. */
    val memory: StateFlow<WeightMemory> = combine(lastWeights, partnership, machineNotes) { lw, pair, notes ->
        WeightMemory(lw, pair?.homeGymId ?: HOME_GYM_ID, notes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightMemory(emptyMap(), HOME_GYM_ID))

    /** Saves [member]'s machine note for this exercise/gym/brand; blank text removes it. */
    fun saveNote(member: String, workout: Workout, ex: WorkoutExercise, text: String) {
        val pid = currentPid
        val key = memory.value.noteKey(workout, ex)
        val current = machineNotes.value[member].orEmpty()
        val clean = text.trim().take(120)
        val updated = if (clean.isEmpty()) current - key else current + (key to clean)
        if (updated == current) return
        task { repo.saveMachineNotes(pid, member, updated) }
    }

    /** Me first, then my partner (if they've joined). */
    fun members(): List<String> {
        val others = partnership.value?.members.orEmpty().filter { it != uid }
        return listOf(uid) + others
    }

    /** The people in [workout] (me first): both of us, or just one for a "Just me" workout. */
    fun members(workout: Workout): List<String> = workout.peopleIn(members())

    fun hasPartner(): Boolean = members().size > 1

    /** Who [solo] leaves in a new workout: just me, or empty = both of us. */
    private fun peopleFor(solo: Boolean): List<String> = if (solo && hasPartner()) listOf(uid) else emptyList()

    /** The running workout I'm in (my own "Just me" one, or one we're doing together). */
    fun myActive(workouts: List<Workout>): Workout? = workouts.firstOrNull { it.finishedAt == null && it.includes(uid) }

    /** My partner's running "Just me" workout, which I could join. */
    fun partnerSolo(workouts: List<Workout>): Workout? = workouts.firstOrNull { it.finishedAt == null && !it.includes(uid) }

    fun nameOf(memberUid: String): String =
        partnership.value?.names?.get(memberUid)?.takeIf { it.isNotBlank() }
            ?: if (memberUid == uid) "You" else "Partner"

    // ---- navigation ----

    fun navigate(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun clearMessage() {
        message.value = null
    }

    // ---- pairing ----

    fun createPair(name: String) = busyTask { repo.createPartnership(uid, name.trim()) }

    fun joinPair(name: String, code: String) = busyTask {
        repo.joinPartnership(uid, name.trim(), code)?.let { message.value = it }
    }

    /** Changes your display name for both of you. */
    fun renameSelf(name: String) {
        val clean = name.trim().take(24)
        val profile = session.value.profile ?: return
        if (clean.isEmpty() || clean == profile.name) return
        task { repo.renameSelf(uid, clean, profile) }
    }

    fun leavePair() {
        val profile = session.value.profile ?: return
        stack.clear(); stack.add(Screen.Home)
        task { repo.leavePartnership(uid, profile) }
    }

    // ---- plans ----

    fun savePlan(plan: Plan) {
        val pid = currentPid
        task { repo.savePlan(pid, plan) }
        back()
    }

    /** Saves a copy of [plan] ("Push day (copy)") with fresh ids. */
    fun duplicatePlan(plan: Plan) {
        val pid = currentPid
        val copy = plan.copy(
            id = newId(),
            name = (plan.name + " (copy)").take(40),
            exercises = plan.exercises.map { it.copy(id = newId()) },
            updatedAt = nowMillis(),
        )
        task { repo.savePlan(pid, copy) }
        message.value = "Copied as \"${copy.name}\""
    }

    fun deletePlan(planId: String) {
        val pid = currentPid
        task { repo.deletePlan(pid, planId) }
        back()
    }

    // ---- default sets × reps for newly added exercises ----

    fun defaultSets(): Int = partnership.value?.defaultSets ?: 3
    fun defaultReps(): Int = partnership.value?.defaultReps ?: 10

    fun setDefaultSetsReps(sets: Int, reps: Int) {
        val pid = currentPid
        task { repo.setDefaultSetsReps(pid, sets.coerceIn(1, 10), reps.coerceIn(1, 50)) }
    }

    fun homeGymId(): String = gyms.homeId()

    fun gymName(id: String?): String = gyms.name(id)

    /** Where this exercise was actually done. */
    fun gymOf(workout: Workout, exercise: WorkoutExercise): String = memory.value.gymOf(workout, exercise)

    // ---- last weights: see WeightMemory ----

    private fun newSets(workout: Workout, ex: WorkoutExercise, members: List<String>): List<WorkoutSet> =
        memory.value.newSets(workout, ex, members, uid, nowMillis(), ::newId)

    private fun refillSets(before: Workout, after: Workout, sets: List<WorkoutSet>): List<WorkoutSet> =
        memory.value.refill(before, after, sets, uid, nowMillis())

    // ---- workouts ----

    private var justStarted: String? = null

    /**
     * Writes go through Firestore's local cache, so we navigate straight away; the suspend call only
     * returns once the server confirms (which may be much later in a gym basement).
     */
    fun startWorkout(plan: Plan?, gymId: String = homeGymId(), solo: Boolean = false) {
        val pid = currentPid
        val workout = Workout(
            id = newId(),
            planId = plan?.id,
            name = plan?.name ?: "Workout",
            gymId = gymId,
            exercises = plan?.exercises.orEmpty().map {
                WorkoutExercise(
                    id = newId(), name = it.name, targetSets = it.sets, targetReps = it.reps,
                    brand = it.brand?.takeIf { b -> gyms.byId(gymId)?.brands?.contains(b) != false },
                    isometric = it.isometric,
                )
            },
            startedAt = nowMillis(),
            warmUp = plan?.warmUp ?: Circuit(),
            mobility = plan?.mobility ?: Circuit(),
            people = peopleFor(solo),
        )
        justStarted = workout.id
        val members = members(workout)
        val sets = workout.exercises.flatMap { newSets(workout, it, members) }
        task { repo.saveWorkoutAndSets(pid, workout, sets) }
        navigate(Screen.Workout(workout.id))
    }

    /** Starts a new workout with the same exercises as [source] (e.g. "repeat last workout"). */
    fun repeatWorkout(source: Workout, gymId: String = source.gymId ?: homeGymId(), solo: Boolean = false) {
        val pid = currentPid
        val workout = repeatOf(source, newId(), gymId, gyms.byId(gymId), nowMillis(), peopleFor(solo), ::newId)
        justStarted = workout.id
        val members = members(workout)
        val sets = workout.exercises.flatMap { newSets(workout, it, members) }
        task { repo.saveWorkoutAndSets(pid, workout, sets) }
        stack.clear(); stack.add(Screen.Home)
        navigate(Screen.Workout(workout.id))
    }

    fun workout(wid: String): Flow<Found<Workout>> =
        with(ctx) {
            repo.workout(currentPid, wid).map { Found(it) }
                // The brand-new workout may not be in the local cache for the very first snapshot.
                .filter { it.value != null || wid != justStarted }
                // Once it's there, a later "gone" is real (e.g. your partner discarded it).
                .onEach { if (it.value != null && wid == justStarted) justStarted = null }
                .safe(Found(null))
        }

    fun sets(wid: String): Flow<List<WorkoutSet>> = with(ctx) { repo.sets(currentPid, wid).safe(emptyList()) }

    fun updateSet(wid: String, set: WorkoutSet) {
        val pid = currentPid
        task { repo.saveSet(pid, wid, set.copy(updatedBy = uid, updatedAt = nowMillis())) }
    }

    fun addSet(workout: Workout, exercise: WorkoutExercise, member: String, existing: List<WorkoutSet>) {
        val pid = currentPid
        val last = existing.maxByOrNull { it.order }
        val set = WorkoutSet(
            id = newId(),
            exerciseId = exercise.id,
            uid = member,
            order = (last?.order ?: -1) + 1,
            weight = last?.weight ?: memory.value.weight(member, workout, exercise),
            reps = last?.reps ?: exercise.targetReps,
            updatedBy = uid,
            updatedAt = nowMillis(),
        )
        task { repo.saveSet(pid, workout.id, set) }
    }

    /** Removes a set; one with anything entered can be put back with Undo. */
    fun removeSet(wid: String, set: WorkoutSet) {
        val pid = currentPid
        task { repo.deleteSet(pid, wid, set.id) }
        if (set.weight != null || set.done) {
            undoNotice.value = UndoNotice("${nameOf(set.uid)}'s set ${set.order + 1} removed") {
                task { repo.saveSet(pid, wid, set) }
            }
        }
    }

    fun addExercises(workout: Workout, names: List<String>) {
        if (names.isEmpty()) return
        val pid = currentPid
        val exercises = names.map {
            WorkoutExercise(id = newId(), name = it.trim(), targetSets = defaultSets(), targetReps = defaultReps())
        }
        val updated = workout.copy(exercises = workout.exercises + exercises)
        val members = members(workout)
        val sets = exercises.flatMap { newSets(updated, it, members) }
        task { repo.addExercisesToWorkout(pid, workout.id, exercises, sets) }
    }

    /**
     * Adds [member] to a "Just me" workout (joining your partner's, or adding them to yours), with
     * sets for every exercise pre-filled from their remembered weights. Afterwards it's a workout
     * for both of you.
     */
    fun joinWorkout(workout: Workout, member: String = uid) {
        if (workout.includes(member)) return
        val pid = currentPid
        val updated = workout.copy(people = emptyList())
        val sets = workout.exercises.flatMap { newSets(updated, it, listOf(member)) }
        task { repo.updateWorkout(pid, workout.id, sets = sets) { "people" to emptyList<String>() } }
    }

    /** Changes where the whole workout happened. Exercises with their own gym keep it. */
    fun changeWorkoutGym(workout: Workout, sets: List<WorkoutSet>, gymId: String) {
        if (gymId == (workout.gymId ?: homeGymId())) return
        val pid = currentPid
        val newGym = gyms.byId(gymId)
        val updated = workout.copy(
            gymId = gymId,
            // A brand the new gym doesn't have can't have been used there.
            exercises = workout.exercises.map { ex ->
                if (ex.gymId == null && ex.brand != null && newGym != null && ex.brand !in newGym.brands) ex.copy(brand = null) else ex
            },
        )
        val refilled = refillSets(workout, updated, sets)
        task {
            repo.updateWorkout(pid, workout.id, refilled) {
                "gymId" to gymId
                exercises(updated.exercises)
            }
        }
    }

    /**
     * Sets one exercise's gym (null = same as the workout), machine brand, and whether it's an
     * isometric version.
     */
    fun changeExerciseSetup(
        workout: Workout,
        sets: List<WorkoutSet>,
        exerciseId: String,
        gymId: String?,
        brand: String?,
        isometric: Boolean,
    ) {
        val pid = currentPid
        val workoutGym = workout.gymId ?: homeGymId()
        val updated = workout.copy(
            exercises = workout.exercises.map {
                if (it.id == exerciseId) {
                    it.copy(
                        gymId = gymId?.takeIf { g -> g != workoutGym },
                        brand = brand,
                        isometric = isometric,
                    )
                } else it
            },
        )
        if (updated == workout) return
        val refilled = refillSets(workout, updated, sets.filter { it.exerciseId == exerciseId })
        task { repo.updateWorkout(pid, workout.id, refilled) { exercises(updated.exercises) } }
    }

    /**
     * Finishes the workout: remembers each person's weights and sets for this gym + brand, saves
     * progress records for the charts, and marks new personal bests on the workout.
     */
    fun finishWorkout(workout: Workout, sets: List<WorkoutSet>) {
        val pid = currentPid
        val now = nowMillis()
        task {
            // Re-read the sets so a partner's last-second edit counts (falls back to what's on screen).
            val fresh = runCatching { repo.getSets(pid, workout.id) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: sets
            val result = memory.value.finish(workout, fresh, now)
            val finished = workout.copy(finishedAt = now, prs = result.prs)
            repo.finishWorkout(pid, finished, result.memory, result.progress)
        }
        rest.stop() // no point resting after the last set
        back()
    }

    /** All progress records for the pair (oldest first), for the Progress tab and charts. */
    // Only listened to while the Progress tab or a chart is on screen (it grows with every workout).
    val progress: StateFlow<List<ProgressPoint>> =
        ctx.perPair(emptyList(), SharingStarted.WhileSubscribed(5_000)) { repo.progress(it) }

    /** Throws a running workout away without remembering anything from it; Undo brings it back. */
    fun discardWorkout(workout: Workout, sets: List<WorkoutSet>) {
        val pid = currentPid
        deleteWorkout(workout.id)
        rest.stop() // nothing left to rest for
        undoNotice.value = UndoNotice("Workout discarded") {
            justStarted = workout.id
            task { repo.saveWorkoutAndSets(pid, workout, sets) }
            stack.clear(); stack.add(Screen.Home)
            navigate(Screen.Workout(workout.id))
        }
    }

    /** Saves a workout's warm-up ([warmUp] true) or cool-down, e.g. moves added or minutes logged mid-workout. */
    fun updateCircuit(workout: Workout, warmUp: Boolean, circuit: Circuit) {
        val pid = currentPid
        task { repo.updateWorkout(pid, workout.id) { (if (warmUp) "warmUp" else "mobility").to(Circuit.serializer(), circuit) } }
    }

    /** Saves my watch's calories and heart rate for the whole workout. */
    fun saveWorkoutHealth(workout: Workout, stats: HealthStats) {
        val pid = currentPid
        val me = uid
        task { repo.updateWorkout(pid, workout.id) { "health.$me".to(HealthStats.serializer(), stats.copy(uid = me)) } }
    }

    fun renameWorkout(workout: Workout, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty() || clean == workout.name) return
        val pid = currentPid
        task { repo.updateWorkout(pid, workout.id) { "name" to clean } }
    }

    /** Removes an exercise and all its sets (both people's) from the workout; Undo puts it back. */
    fun removeExercise(workout: Workout, exerciseId: String, sets: List<WorkoutSet>) {
        val pid = currentPid
        val index = workout.exercises.indexOfFirst { it.id == exerciseId }
        val exercise = workout.exercises.getOrNull(index) ?: return
        val remaining = workout.exercises.filter { it.id != exerciseId }
        val removedSets = sets.filter { it.exerciseId == exerciseId }
        task { repo.updateWorkout(pid, workout.id, deleteSetIds = removedSets.map { it.id }) { exercises(remaining) } }
        undoNotice.value = UndoNotice("${exercise.name} removed") {
            task {
                // Re-insert into the workout as it is now (your partner may have changed it since).
                val current = repo.getWorkout(pid, workout.id) ?: return@task
                if (current.exercises.any { it.id == exerciseId }) return@task
                val list = current.exercises.toMutableList().apply { add(index.coerceAtMost(size), exercise) }
                repo.updateWorkout(pid, workout.id, sets = removedSets) { exercises(list) }
            }
        }
    }

    /** Moves an exercise up (-1) or down (+1) in the workout. */
    fun moveExercise(workout: Workout, exerciseId: String, delta: Int) {
        val list = workout.exercises.toMutableList()
        val i = list.indexOfFirst { it.id == exerciseId }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        val pid = currentPid
        task { repo.updateWorkout(pid, workout.id) { exercises(list) } }
    }

    fun deleteWorkout(wid: String) {
        val pid = currentPid
        task { repo.deleteWorkout(pid, wid) }
        back()
    }

    override fun onCleared() {
        recipes.close()
    }

    // ---- helpers ----

    private fun task(block: suspend () -> Unit) = ctx.task(block)

    private fun busyTask(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = friendlyError(e)
            } finally {
                busy.value = false
            }
        }
    }
}
