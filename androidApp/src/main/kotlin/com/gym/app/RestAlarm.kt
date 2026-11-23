package com.gym.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The rest timer outside the app. While Gym is on screen the in-app rest bar does everything; once
 * it's in the background (or the phone is locked) a countdown notification shows, and an alarm buzzes
 * and posts "Rest over" when time's up.
 */
object RestAlarm {
    private const val CHANNEL_COUNTDOWN = "rest_countdown"
    private const val CHANNEL_OVER = "rest_over"
    private const val ID_COUNTDOWN = 1
    private const val ID_OVER = 2

    /** When the current rest ends (epoch millis), or null. Lives as long as the process. */
    @Volatile var endsAt: Long? = null
        private set

    /** True while Gym is on screen (MainActivity started). */
    @Volatile var appVisible = false

    fun set(context: Context, end: Long?) {
        endsAt = end
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        NotificationManagerCompat.from(context).cancel(ID_COUNTDOWN)
        if (end == null || end <= System.currentTimeMillis()) return
        NotificationManagerCompat.from(context).cancel(ID_OVER)
        // Exact so it buzzes on time with the phone locked; Gym has USE_EXACT_ALARM (a timer app).
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending)
        }
        if (!appVisible) showCountdown(context)
    }

    /** Gym went to the background: show the countdown if a rest is running. */
    fun onAppHidden(context: Context) {
        appVisible = false
        if ((endsAt ?: 0) > System.currentTimeMillis()) showCountdown(context)
    }

    /** Gym is back on screen: the rest bar takes over again. */
    fun onAppShown(context: Context) {
        appVisible = true
        NotificationManagerCompat.from(context).cancel(ID_COUNTDOWN)
        NotificationManagerCompat.from(context).cancel(ID_OVER)
    }

    internal fun onAlarm(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_COUNTDOWN)
        if (appVisible) return // the in-app timer buzzes and shows "Rest over"
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            ensureChannels(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_OVER)
                .setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle("Rest over")
                .setContentText("Next set!")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setTimeoutAfter(120_000)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            try {
                manager.notify(ID_OVER, notification)
            } catch (_: SecurityException) {
            }
        }
        buzz(context)
    }

    private fun showCountdown(context: Context) {
        val end = endsAt ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Resting")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(end)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setTimeoutAfter((end - System.currentTimeMillis()).coerceAtLeast(1))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
        try {
            manager.notify(ID_COUNTDOWN, notification)
        } catch (_: SecurityException) {
        }
    }

    /** Same buzz-buzz as in the app, as an alarm so Android lets it through from the background. */
    private fun buzz(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        } ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, "Rest countdown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Time left while resting between sets"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVER, "Rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When it's time for the next set"
                setSound(null, null)
                enableVibration(false) // Gym buzzes itself, so it also works with notifications off
                setShowBadge(false)
            }
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.onAlarm(context)
}
