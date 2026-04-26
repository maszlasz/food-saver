package com.foodsaver.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.foodsaver.data.FoodEntryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    // Reschedule notifications after boot
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = FoodEntryRepository.entriesFlow(context).first()
                NotificationScheduler.scheduleAll(context, entries)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
