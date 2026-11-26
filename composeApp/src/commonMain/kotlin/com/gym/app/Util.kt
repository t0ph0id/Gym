package com.gym.app

import androidx.compose.runtime.Composable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** What Google sign-in hands back; Firebase on iOS also needs the access token. */
data class GoogleTokens(val idToken: String, val accessToken: String? = null)

/** Platform Google sign-in UI. Android: Credential Manager. iOS: the GoogleSignIn SDK. */
interface GoogleAuth {
    /** Shows the account picker and returns the Google tokens, or null if the user cancelled. */
    suspend fun signIn(): GoogleTokens?

    /** Forgets the chosen account so the next sign-in shows the picker again. */
    suspend fun signOut()
}

/** Platform services that need the running app (Android: the Activity). */
interface AppPlatform {
    /** Wipes the offline Firestore cache (so the next account on this phone can't see it) and restarts. */
    suspend fun clearLocalDataAndRestart()

    /** A short buzz-buzz, e.g. when the rest timer ends. */
    fun vibrate()

    /**
     * Lets the user take a photo (camera) or choose one (gallery) and returns it as a small JPEG
     * (longest side ~800 px), or null if they backed out.
     */
    suspend fun pickPhoto(fromCamera: Boolean): ByteArray?

    /** Stops the screen turning off (during a workout). */
    fun keepScreenOn(on: Boolean)

    /**
     * Mirrors the rest timer outside the app: a countdown notification while Gym is in the
     * background and a buzz when it ends, even with the phone locked. null cancels it.
     */
    fun setRestAlarm(endsAt: Long?)

    /** Opens the system share sheet (e.g. to send the pair code). */
    fun shareText(text: String)

    /** Small settings kept on this phone only (e.g. light/dark). null removes. */
    fun loadSetting(key: String): String?
    fun saveSetting(key: String, value: String?)

    /** Light or dark status/navigation bar icons, to match the app's theme. */
    fun setDarkSystemBars(dark: Boolean)

    /** Text shared into Gym from another app (e.g. a recipe link), until [consumeSharedText]. */
    val sharedText: kotlinx.coroutines.flow.StateFlow<String?>
    fun consumeSharedText()

    /** Whether this phone has a health store to read workouts from (Health Connect on Android). */
    val canReadHealth: Boolean get() = false

    /**
     * Exercise sessions (e.g. from a Fitbit) that overlap [from]..[to], newest first. With [ask], asks
     * for permission if it isn't granted yet. null = not allowed (or not available).
     */
    suspend fun healthSessions(from: Long, to: Long, ask: Boolean): List<HealthSession>? = null

    /** Calories and heart rate between [from] and [to] (whatever was allowed), or null if not allowed. */
    suspend fun healthStats(from: Long, to: Long): HealthStats? = null
}

/**
 * One exercise session from the phone's health data, e.g. 12 minutes on the elliptical, with its
 * calories and heart rate when allowed.
 */
data class HealthSession(
    val title: String,
    val start: Long,
    val end: Long,
    val source: String?,
    val stats: HealthStats? = null,
) {
    /** Rounded to the nearest minute, at least 1. */
    val minutes: Int get() = ((end - start + 30_000) / 60_000).toInt().coerceAtLeast(1)
}

/** e.g. "18:05" */
@OptIn(ExperimentalTime::class)
fun formatTime(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return dt.hour.toString().padStart(2, '0') + ":" + dt.minute.toString().padStart(2, '0')
}

private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I

/** Cryptographically strong randomness (platform-provided) for ids and invite codes. */
expect fun secureRandom(): Random

fun newId(): String {
    val r = secureRandom()
    return buildString { repeat(20) { append(ID_CHARS[r.nextInt(ID_CHARS.length)]) } }
}

fun newInviteCode(): String {
    val r = secureRandom()
    return buildString { repeat(6) { append(CODE_CHARS[r.nextInt(CODE_CHARS.length)]) } }
}

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun formatWeight(w: Double?): String = when {
    w == null -> ""
    w % 1.0 == 0.0 -> w.toLong().toString()
    else -> w.toString()
}

fun parseWeight(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

/** e.g. "Sun 22 Nov, 18:05" */
@OptIn(ExperimentalTime::class)
fun formatDateTime(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val dow = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val day = dt.date.toString().takeLast(2).trimStart('0')
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "$dow $day $month, $hh:$mm"
}

fun formatDuration(fromMillis: Long, toMillis: Long): String {
    val minutes = ((toMillis - fromMillis) / 60_000).coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** An error whose message is already written for the user and can be shown as-is. */
class UserFacingException(message: String) : Exception(message)

/** Turns Firebase/network exceptions into something a person at the gym can act on. */
fun friendlyError(e: Throwable): String {
    if (e is UserFacingException) return e.message.orEmpty()
    val msg = (e.message ?: "") + " " + e::class.simpleName
    return when {
        "PERMISSION_DENIED" in msg || "insufficient permissions" in msg ->
            "You don't have access to that any more. If you changed pairs, restart the app."
        "UNAVAILABLE" in msg || "offline" in msg || "from server" in msg || "network" in msg.lowercase() ->
            "Can't reach the server. Check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}

/**
 * The time of a whole gym visit for its calories and heart rate: the workout, stretched to cover
 * any watch sessions that overlap it (give or take [margin]), e.g. a warm-up recorded on the watch
 * before the workout was started in the app.
 */
fun visitRange(start: Long, end: Long, sessions: List<HealthSession>, margin: Long = 30 * 60_000L): LongRange {
    val near = sessions.filter { it.end > start - margin && it.start < end + margin }
    return minOf(start, near.minOfOrNull { it.start } ?: start)..maxOf(end, near.maxOfOrNull { it.end } ?: end)
}
