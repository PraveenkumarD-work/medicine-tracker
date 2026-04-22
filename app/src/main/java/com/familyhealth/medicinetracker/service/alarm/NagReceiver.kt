package com.familyhealth.medicinetracker.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.domain.model.DoseStatus
import com.familyhealth.medicinetracker.service.notification.NotificationHelper
import com.familyhealth.medicinetracker.util.Constants
import kotlinx.coroutines.*

/**
 * Fires every 5 minutes as long as the dose is unacknowledged.
 * Checks state before each nag — stops if TAKEN/SKIPPED/SNOOZED.
 * Max nag count: 12 (1 hour total).
 */
class NagReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleNag(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleNag(context: Context, intent: Intent) {
        val doseLogId   = intent.getLongExtra(Constants.EXTRA_DOSE_LOG_ID, -1L)
        val medId       = intent.getLongExtra(Constants.EXTRA_MED_ID, -1L)
        val medName     = intent.getStringExtra(Constants.EXTRA_MED_NAME) ?: "Medicine"
        val nagCount    = intent.getIntExtra(Constants.EXTRA_NAG_COUNT, 1)
        val scheduledAt = intent.getLongExtra(Constants.EXTRA_SCHEDULED_AT, 0L)

        if (doseLogId == -1L) return

        val repo = (context.applicationContext as MedicineTrackerApp).repository
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        // ── Check current state before nagging ────────────────────────────────
        val doseLog = repo.getDoseLogById(doseLogId) ?: return

        when (doseLog.status) {
            DoseStatus.TAKEN,
            DoseStatus.SKIPPED -> {
                // User already acted — cancel everything, done
                NotificationHelper.cancelNotification(context, doseLogId)
                return
            }
            DoseStatus.SNOOZED -> {
                // Muted — show visual-only notification without sound, don't re-nag yet
                val isMuted = true
                val isDriving = prefs.getBoolean(Constants.PREF_DRIVING_ACTIVE, false)
                val notification = NotificationHelper.buildDoseNotification(
                    context, doseLogId, medId, medName, scheduledAt,
                    nagCount, isMuted, isDriving
                )
                NotificationHelper.showNotification(
                    context, (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(), notification
                )
                // Re-queue one more nag after mute expires
                val muteUntil = prefs.getLong(Constants.PREF_MUTE_UNTIL, 0L)
                val delay = maxOf(muteUntil - System.currentTimeMillis() + 1000, Constants.NAG_INTERVAL_MS)
                AlarmScheduler.scheduleNagAlarm(context, doseLogId, medId, medName, nagCount + 1, scheduledAt)
                return
            }
            else -> { /* NAGGING or SUPPRESSED — continue */ }
        }

        // ── Hard stop after MAX_NAG_COUNT ─────────────────────────────────────
        if (nagCount > Constants.MAX_NAG_COUNT) {
            // Auto-mark as skipped after 1 hour of nagging with no response
            repo.updateDoseStatus(doseLogId, DoseStatus.SKIPPED, actualAt = System.currentTimeMillis())
            NotificationHelper.cancelNotification(context, doseLogId)
            return
        }

        // ── Check driving mode ────────────────────────────────────────────────
        val isDriving = prefs.getBoolean(Constants.PREF_DRIVING_ACTIVE, false)

        // ── Check mute ────────────────────────────────────────────────────────
        val muteUntil = prefs.getLong(Constants.PREF_MUTE_UNTIL, 0L)
        val isMuted   = System.currentTimeMillis() < muteUntil

        // ── Update state ──────────────────────────────────────────────────────
        repo.updateDoseStatus(
            doseLogId, DoseStatus.NAGGING, nagCount = nagCount,
            nextNagAt = System.currentTimeMillis() + Constants.NAG_INTERVAL_MS
        )

        // ── Show notification ─────────────────────────────────────────────────
        val notification = NotificationHelper.buildDoseNotification(
            context, doseLogId, medId, medName, scheduledAt,
            nagCount, isMuted, isDriving
        )
        NotificationHelper.showNotification(
            context, (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(), notification
        )

        // ── TTS if driving ────────────────────────────────────────────────────
        if (isDriving) {
            context.sendBroadcast(Intent("com.familyhealth.medicinetracker.ACTION_TTS").apply {
                putExtra(Constants.EXTRA_MED_NAME, medName)
            })
        }

        // ── Schedule next nag ─────────────────────────────────────────────────
        AlarmScheduler.scheduleNagAlarm(
            context, doseLogId, medId, medName, nagCount + 1, scheduledAt
        )
    }
}
