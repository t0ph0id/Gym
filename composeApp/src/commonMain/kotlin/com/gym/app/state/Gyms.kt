package com.gym.app.state

import com.gym.app.DEFAULT_HOME_GYM
import com.gym.app.GymLocation
import com.gym.app.HOME_GYM_ID
import com.gym.app.Partnership
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The gyms you train at, which one is home, and the machine brands each has. */
class Gyms internal constructor(private val ctx: VmContext, private val partnership: StateFlow<Partnership?>) {

    /** Home gym first, then the rest by name. Falls back to the default until Firestore answers. */
    val list: StateFlow<List<GymLocation>> = combine(
        ctx.perPair(emptyList()) { ctx.repo.gyms(it) },
        partnership,
    ) { gyms, pair ->
        val home = pair?.homeGymId ?: HOME_GYM_ID
        gyms.ifEmpty { listOf(DEFAULT_HOME_GYM) }
            .sortedWith(compareBy<GymLocation> { it.id != home }.thenBy { it.name.lowercase() })
    }.stateIn(ctx.scope, SharingStarted.Eagerly, listOf(DEFAULT_HOME_GYM))

    init {
        // Pairs from before gyms existed get "Our gym" once (server-checked; offline: next launch).
        ctx.scope.launch {
            ctx.pid.collect { p ->
                if (p != null) {
                    try {
                        ctx.repo.ensureHomeGym(p)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
            }
        }
    }

    fun homeId(): String = partnership.value?.homeGymId ?: HOME_GYM_ID

    /** The gym with [id] (null = home). */
    fun byId(id: String?): GymLocation? = list.value.firstOrNull { it.id == (id ?: homeId()) }

    fun name(id: String?): String = byId(id)?.name ?: "Removed gym"

    /** Brands at this gym first, then those at your other gyms, for brand pickers. */
    fun brandOptions(gymId: String?): List<String> {
        val atGym = byId(gymId)?.brands.orEmpty()
        return atGym + list.value.flatMap { it.brands }.filter { it !in atGym }.distinct()
    }

    fun save(gym: GymLocation) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.saveGym(pid, gym) }
    }

    fun delete(gymId: String) {
        if (gymId == homeId()) return
        val pid = ctx.currentPid
        ctx.task { ctx.repo.deleteGym(pid, gymId) }
    }

    fun setHome(gymId: String) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.setHomeGym(pid, gymId) }
    }
}
