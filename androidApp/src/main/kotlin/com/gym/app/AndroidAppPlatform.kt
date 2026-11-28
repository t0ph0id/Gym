package com.gym.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppPlatform(private val activity: ComponentActivity) : AppPlatform {

    // ---- Health Connect: exercise sessions (e.g. from a Fitbit) for cardio minutes ----

    private val exercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val totalCaloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    private val activeCaloriesPermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val allHealthPermissions = setOf(exercisePermission, totalCaloriesPermission, activeCaloriesPermission, heartRatePermission)
    private var pendingHealth: CompletableDeferred<Set<String>>? = null
    private val askHealth = activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        pendingHealth?.complete(it)
    }

    override val canReadHealth: Boolean
        get() = HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE

    override suspend fun healthSessions(from: Long, to: Long, ask: Boolean): List<HealthSession>? {
        if (!canReadHealth) return null
        val client = HealthConnectClient.getOrCreate(activity)
        var granted = grantedHealth(client)
        // Asks for whatever's missing (e.g. calories and heart rate, added after exercise sessions).
        if (ask && !granted.containsAll(allHealthPermissions)) {
            val result = CompletableDeferred<Set<String>>()
            pendingHealth = result
            askHealth.launch(allHealthPermissions)
            result.await()
            granted = grantedHealth(client)
        }
        if (exercisePermission !in granted) return null
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))),
            ).records.map { r ->
                HealthSession(
                    title = r.title?.takeIf { it.isNotBlank() } ?: exerciseName(r.exerciseType),
                    start = r.startTime.toEpochMilli(),
                    end = r.endTime.toEpochMilli(),
                    source = appName(r.metadata.dataOrigin.packageName),
                    stats = stats(client, granted, r.startTime, r.endTime, prefer = r.metadata.dataOrigin.packageName),
                    cardio = cardioMove(r.exerciseType),
                )
            }.sortedByDescending { it.start }
        }.getOrElse { emptyList() }
    }

    override suspend fun healthStats(from: Long, to: Long): HealthStats? {
        if (!canReadHealth) return null
        val client = HealthConnectClient.getOrCreate(activity)
        val granted = grantedHealth(client)
        if (granted.none { it != exercisePermission && it in allHealthPermissions }) return null
        return stats(client, granted, Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
    }

    private suspend fun grantedHealth(client: HealthConnectClient): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /**
     * Calories (total, as the watch shows for a session; active if that's all there is) and heart
     * rate between [from] and [to], for whichever of them is allowed. Calories are added up from the
     * records ([watchCalories]): Health Connect's totals skip apps missing from its priority list.
     */
    private suspend fun stats(
        client: HealthConnectClient,
        granted: Set<String>,
        from: Instant,
        to: Instant,
        prefer: String? = null,
    ): HealthStats? {
        val range = TimeRangeFilter.between(from, to)
        val (a, b) = from.toEpochMilli() to to.toEpochMilli()
        var kcal: Double? = null
        if (totalCaloriesPermission in granted) {
            kcal = watchCalories(
                readAll(client, TotalCaloriesBurnedRecord::class, range).map {
                    EnergySample(it.metadata.dataOrigin.packageName, it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories)
                },
                a, b, prefer,
            )
        }
        if (kcal == null && activeCaloriesPermission in granted) {
            kcal = watchCalories(
                readAll(client, ActiveCaloriesBurnedRecord::class, range).map {
                    EnergySample(it.metadata.dataOrigin.packageName, it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories)
                },
                a, b, prefer,
            )
        }
        val heart = if (heartRatePermission in granted) {
            runCatching {
                client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX), range))
            }.getOrNull()
        } else null
        return HealthStats(
            kcal = kcal?.roundToInt()?.takeIf { it > 0 },
            avgBpm = heart?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            maxBpm = heart?.get(HeartRateRecord.BPM_MAX)?.toInt(),
        ).takeUnless { it.isEmpty }
    }

    /** Every record of [type] in [range], over as many pages as it takes. */
    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> =
        runCatching {
            val all = mutableListOf<T>()
            var token: String? = null
            do {
                val page = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
                all += page.records
                token = page.pageToken
            } while (token != null && all.size < 20_000)
            all
        }.getOrDefault(emptyList())

    /** The [MobilityCatalog] cardio move for a gym-machine session type; null for anything else. */
    private fun cardioMove(type: Int): String? = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair climber"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Exercise bike"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing machine"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill"
        else -> null
    }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair climber"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Stairs"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Exercise bike"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing machine"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
        else -> "Workout"
    }

    /** "Fitbit" for com.fitbit.FitbitMobile, etc.: the app's own name, if it's installed. */
    private fun appName(pkg: String): String? = runCatching {
        val pm = activity.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    // ---- photos: system photo picker (no permission needed) or the camera app ----

    private var pendingPhoto: CompletableDeferred<Uri?>? = null
    private var captureUri: Uri? = null

    // Must be registered while the activity is being created (this class is built in onCreate).
    private val pickMedia = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingPhoto?.complete(uri)
    }
    private val takePicture = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingPhoto?.complete(if (saved) captureUri else null)
    }

    override suspend fun pickPhoto(fromCamera: Boolean): ByteArray? {
        val result = CompletableDeferred<Uri?>()
        pendingPhoto = result
        if (fromCamera) {
            val dir = File(activity.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "capture.jpg")
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            captureUri = uri
            takePicture.launch(uri)
        } else {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) { runCatching { shrink(uri) }.getOrNull() }
    }

    /** Decodes (respecting the photo's rotation), scales to at most 800 px and re-encodes as JPEG. */
    private fun shrink(uri: Uri, maxSide: Int = 800): ByteArray {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(activity.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = activity.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts)!! }
            val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
            if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.toByteArray()
        }
    }

    // ---- rest timer, screen, sharing ----

    private var askedForNotifications = false
    private val notificationPermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun setRestAlarm(endsAt: Long?) {
        // Ask once, the first time a rest starts, so the countdown can show with the phone locked.
        if (endsAt != null && Build.VERSION.SDK_INT >= 33 && !askedForNotifications &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RestAlarm.set(activity, endsAt)
    }

    override fun keepScreenOn(on: Boolean) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(send, null))
    }

    // ---- text shared into the app (Share → Gym) ----

    private val shared = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    override val sharedText: kotlinx.coroutines.flow.StateFlow<String?> = shared

    override fun consumeSharedText() {
        shared.value = null
    }

    /** Called with the intent the activity was started (or re-used) with. */
    fun receive(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT)).joinToString(" ")
            if (text.isNotBlank()) shared.value = text
        }
    }

    // ---- per-phone settings and system bars ----

    private val prefs = activity.getSharedPreferences("gym_settings", android.content.Context.MODE_PRIVATE)

    override fun loadSetting(key: String): String? = prefs.getString(key, null)

    override fun saveSetting(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun setDarkSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun vibrate() {
        if (!RestAlarm.appVisible) return // in the background the rest alarm buzzes instead
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            activity.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") activity.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
    }

    override suspend fun clearLocalDataAndRestart() {
        withContext(Dispatchers.IO) {
            // clearPersistence only works on a terminated instance, and a terminated instance can't
            // be reused in this process — hence the restart below.
            val firestore = FirebaseFirestore.getInstance()
            runCatching {
                Tasks.await(firestore.terminate())
                Tasks.await(firestore.clearPersistence())
            }
        }
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
