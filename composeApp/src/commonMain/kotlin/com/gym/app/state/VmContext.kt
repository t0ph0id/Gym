package com.gym.app.state

import com.gym.app.GymRepository
import com.gym.app.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the parts of the view model share: the current pair, a scope for background writes, and
 * the snackbar message that errors end up in.
 */
internal class VmContext(
    val scope: CoroutineScope,
    val repo: GymRepository,
    /** Shown in the snackbar. */
    val message: MutableStateFlow<String?>,
    /** The pair you're in (null while signed out or not paired). */
    val pid: Flow<String?>,
    private val currentPidNow: () -> String,
) {
    val currentPid: String get() = currentPidNow()

    /** Runs a write in the background; a failure becomes a message for the snackbar. */
    fun task(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = friendlyError(e)
            }
        }
    }

    /** On a read error: show it and carry on with [fallback]. */
    fun <T> Flow<T>.safe(fallback: T): Flow<T> = catch { e ->
        message.value = friendlyError(e)
        emit(fallback)
    }

    /** A live value for the current pair ([empty] while not in one). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> perPair(empty: T, started: SharingStarted = SharingStarted.Eagerly, load: (pid: String) -> Flow<T>): StateFlow<T> =
        pid.flatMapLatest { p -> if (p == null) flowOf(empty) else load(p).safe(empty) }
            .stateIn(scope, started, empty)
}
