package com.familyhealth.medicinetracker.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.domain.model.DoseStatus
import com.familyhealth.medicinetracker.service.worker.StockCheckWorker
import kotlinx.coroutines.*

/**
 * Fires on device boot. Re-registers all active alarms since AlarmManager
 * does NOT survive reboot. Also restores any mid-nag doses.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreAlarms(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreAlarms(context: Context) {
        val repo = (context.applicationContext as MedicineTrackerApp).repository

        // ── Restore all active schedule alarms ────────────────────────────────
        val schedules = repo.getAllEnabledSchedules()
        for (schedule in schedules) {
            val med = repo.getMedicationById(schedule.medicationId) ?: continue
            if (!med.isActive) continue
            AlarmScheduler.scheduleDoseAlarm(context, schedule, med.id, med.name)
        }

        // ── Restore any in-flight NAGGING doses ───────────────────────────────
        val naggingDoses = repo.getAllNaggingLogs()
        for (dose in naggingDoses) {
            val med = repo.getMedicationById(dose.medicationId) ?: continue
            // Re-fire the nag immediately (device was rebooted mid-nag)
            AlarmScheduler.scheduleNagAlarm(
                context,
                doseLogId = dose.id,
                medId = med.id,
                medName = med.name,
                nagCount = dose.nagCount,
                scheduledAt = dose.scheduledAt
            )
        }

        // ── Re-schedule periodic stock check via WorkManager ──────────────────
        StockCheckWorker.enqueue(context)
    }
}
