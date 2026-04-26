package com.foodsaver.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.foodsaver.R
import com.foodsaver.model.FoodEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationScheduler {
    // Ensure the notification channel exists
    fun ensureChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(NotificationReceiver.CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    NotificationReceiver.CHANNEL_ID,
                    context.getString(R.string.notification_channel_expiries),
                    NotificationManager.IMPORTANCE_LOW,
                )
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Schedule notifications for the given entry
    // Multiple notifications are scheduled: on the expiry day and up to 3 days before it
    // Notifications trigger at 17:00 local time
    fun schedule(
        context: Context,
        entry: FoodEntry,
    ) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()

        for (daysOffset in 0..3) {
            val notificationDate = entry.expiry.minusDays(daysOffset.toLong())
            if (notificationDate.isBefore(today)) {
                break
            }

            val triggerAtMillis =
                LocalDateTime
                    .of(notificationDate, LocalTime.of(17, 0))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val notificationId = notificationId(entry, daysOffset)
            val pendingIntent =
                buildPendingIntent(
                    context = context,
                    entryId = entry.id,
                    name = entry.name,
                    daysLeft = daysOffset,
                    notificationId = notificationId,
                )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun scheduleAll(
        context: Context,
        entries: List<FoodEntry>,
    ) {
        entries.forEach { schedule(context, it) }
    }

    // Cancel all notification alarms for the given entry.
    fun cancelAlarms(
        context: Context,
        entry: FoodEntry,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (daysOffset in 0..3) {
            val pendingIntent =
                buildPendingIntent(
                    context = context,
                    entryId = entry.id,
                    name = entry.name,
                    daysLeft = daysOffset,
                    notificationId = notificationId(entry, daysOffset),
                )
            alarmManager.cancel(pendingIntent)
        }
    }

//    Make it a unique and positive int
    private fun notificationId(
        entry: FoodEntry,
        daysOffset: Int,
    ): Int = "${entry.id}_$daysOffset".hashCode() and Int.MAX_VALUE

    private fun buildPendingIntent(
        context: Context,
        entryId: String,
        name: String,
        daysLeft: Int,
        notificationId: Int,
    ): PendingIntent {
        val intent =
            Intent(context, NotificationReceiver::class.java).apply {
                action = "com.foodsaver.notification.EXPIRY.$entryId.$daysLeft"
                putExtra(NotificationReceiver.EXTRA_FOOD_NAME, name)
                putExtra(NotificationReceiver.EXTRA_DAYS_LEFT, daysLeft)
                putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }

        return PendingIntent.getBroadcast(
            context,
            daysLeft,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
