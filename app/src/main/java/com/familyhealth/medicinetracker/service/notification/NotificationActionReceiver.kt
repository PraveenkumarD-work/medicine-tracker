package com.familyhealth.medicinetracker.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.domain.model.DoseStatus
import com.familyhealth.medicinetracker.service.alarm.AlarmScheduler
import com.familyhealth.medicinetracker.util.Constants
import kotlinx.coroutines.*

/**
 * Handles the three notification action buttons: Taken, Skip, Mute 1hr.
 * This is the STOP CONDITION for the nag loop.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, intent: Intent) {
        val doseLogId = intent.getLongExtra(Constants.EXTRA_DOSE_LOG_ID, -1L)
        val medId     = intent.getLongExtra(Constants.EXTRA_MED_ID, -1L)
        if (doseLogId == -1L) return

        val repo = (context.applicationContext as MedicineTrackerApp).repository
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        when (intent.action) {
            Constants.ACTION_TAKEN -> {
                // ── Mark dose as taken ────────────────────────────────────────
                repo.updateDoseStatus(
                    doseLogId, DoseStatus.TAKEN,
                    actualAt = System.currentTimeMillis()
                )
                // Decrement stock
                repo.decrementStock(medId)
                // Cancel nag alarm + notification
                AlarmScheduler.cancelNagAlarm(context, doseLogId)
                NotificationHelper.cancelNotification(context, doseLogId)
            }

            Constants.ACTION_SKIP -> {
                // ── Mark dose as skipped ──────────────────────────────────────
                repo.updateDoseStatus(
                    doseLogId, DoseStatus.SKIPPED,
                    actualAt = System.currentTimeMillis()
                )
                AlarmScheduler.cancelNagAlarm(context, doseLogId)
                NotificationHelper.cancelNotification(context, doseLogId)
            }

            Constants.ACTION_MUTE -> {
                // ── Mute sound for 1 hour; keep visual notification ───────────
                val muteUntil = System.currentTimeMillis() + Constants.MUTE_DURATION_MS
                prefs.edit().putLong(Constants.PREF_MUTE_UNTIL, muteUntil).apply()

                repo.updateDoseStatus(doseLogId, DoseStatus.SNOOZED)

                // Show a silent "muted" notification so user knows it's snoozed
                val doseLog = repo.getDoseLogById(doseLogId)
                if (doseLog != null) {
                    val med = repo.getMedicationById(medId)
                    if (med != null) {
                        val notification = NotificationHelper.buildDoseNotification(
                            context, doseLogId, medId, med.name,
                            doseLog.scheduledAt, nagCount = 0,
                            isMuted = true, isDriving = false
                        )
                        NotificationHelper.showNotification(
                            context,
                            (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(),
                            notification
                        )
                    }
                }
                // The NagReceiver will check SNOOZED state and handle re-nagging
                // after mute expires — no need to cancel the nag alarm chain.
            }
        }
    }
}
