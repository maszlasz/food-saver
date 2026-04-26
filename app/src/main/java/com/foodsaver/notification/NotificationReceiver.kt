package com.foodsaver.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.foodsaver.MainActivity
import com.foodsaver.R

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "foodsaver_expiry"
        const val EXTRA_FOOD_NAME = "food_name"
        const val EXTRA_DAYS_LEFT = "days_left"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    // Create a notification based on intent and push it
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val foodName = intent.getStringExtra(EXTRA_FOOD_NAME) ?: return
        val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val message =
            when (daysLeft) {
                0 -> context.getString(R.string.notification_expiring_today, foodName)
                1 -> context.getString(R.string.notification_expiring_tomorrow, foodName)
                else -> context.getString(R.string.notification_expiring_in_days, foodName, daysLeft)
            }

        val openAppPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        NotificationScheduler.ensureChannel(context)

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clock_down)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(message)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .setSilent(true)
                .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
