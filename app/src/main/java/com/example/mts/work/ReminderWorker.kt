package com.example.mts.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mts.MainActivity
import com.example.mts.R
import com.example.mts.SmartPantryApp
import com.example.mts.ui.common.millisToIso
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "pantry_reminders"
private const val WORK_NAME = "daily_reminders"
private const val EXPIRY_NOTIFICATION = 1
private const val MEAL_NOTIFICATION = 2

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SmartPantryApp
        if (app.tokens.refreshToken() == null) return Result.success()

        return try {
            val prefs = app.api.me().notifications
            if (prefs.expiryEnabled) notifyExpiring(app)
            if (prefs.mealPlanEnabled) notifyMeals(app)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun notifyExpiring(app: SmartPantryApp) {
        val groups = app.api.expiring()
        val names = (groups.expiringToday + groups.expiringSoon).map { it.name }
        if (names.isEmpty()) return
        post(
            EXPIRY_NOTIFICATION,
            app.getString(R.string.notif_expiry_title, names.size),
            names.take(4).joinToString(", "),
        )
    }

    private suspend fun notifyMeals(app: SmartPantryApp) {
        val today = millisToIso(Calendar.getInstance().run { timeInMillis + timeZone.getOffset(timeInMillis) })
        val meals = app.api.mealWeek(today).items.filter { it.date == today }
        if (meals.isEmpty()) return
        post(MEAL_NOTIFICATION, app.getString(R.string.notif_meal_title), meals.joinToString(", ") { it.recipe.title })
    }

    private fun post(id: Int, title: String, body: String) {
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
