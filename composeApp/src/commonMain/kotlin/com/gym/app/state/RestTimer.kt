package com.gym.app.state

import com.gym.app.UserProfile
import com.gym.app.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow

/** The countdown after ticking a set. It runs on this phone only; its length is in your profile. */
class RestTimer internal constructor(
    private val ctx: VmContext,
    private val profile: () -> UserProfile?,
) {
    /** When the current rest ends (epoch millis), or null when no rest is running. */
    val endsAt = MutableStateFlow<Long?>(null)

    fun seconds(): Int = profile()?.restSeconds ?: 90

    fun start() {
        val s = seconds()
        endsAt.value = if (s > 0) nowMillis() + s * 1000L else null
    }

    fun adjust(deltaSeconds: Int) {
        val end = endsAt.value ?: return
        endsAt.value = maxOf(nowMillis(), end + deltaSeconds * 1000L)
    }

    /** Stops the rest; with [onlyIfEndsAt], only if it's still that same rest (not a newer one). */
    fun stop(onlyIfEndsAt: Long? = null) {
        if (onlyIfEndsAt == null || endsAt.value == onlyIfEndsAt) endsAt.value = null
    }

    /** New default length (0 = off), saved to your profile. */
    fun setSeconds(seconds: Int) {
        val p = profile() ?: return
        if (p.restSeconds == seconds) return
        ctx.task { ctx.repo.saveProfile(p.copy(restSeconds = seconds)) }
    }
}
