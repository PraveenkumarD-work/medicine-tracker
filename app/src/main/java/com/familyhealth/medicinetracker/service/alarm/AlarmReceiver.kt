package com.familyhealth.medicinetracker.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.domain.model.*
import com.familyhealth.medicinetracker.service.notification.NotificationHelper
import com.familyhealth.medicinetracker.util.Constants
import kotlinx.coroutines.*

/**
 * Fires when an exact alarm triggers (initial dose time).
 * Checks meal context, driving mode, and mute state before deciding
 * which notification to show. Kicks off the 5-minute nag loop.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repo = (context.applicationContext as MedicineTrackerApp).repository
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        when (action) {
            Constants.ACTION_DOSE_ALARM -> handleDoseAlarm(context, intent, repo, prefs)
            Constants.ACTION_MEAL_ALARM -> handleMealAlarm(context, intent, repo, prefs)
        }
    }

    private suspend fun handleDoseAlarm(
        context: Context,
        intent: Intent,
        repo: com.familyhealth.medicinetracker.data.repository.MedicineRepository,
        prefs: android.content.SharedPreferences
    ) {
        val scheduleId  = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        val medId       = intent.getLongExtra(Constants.EXTRA_MED_ID, -1L)
        val scheduledAt = intent.getLongExtra(Constants.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        val medName     = intent.getStringExtra(Constants.EXTRA_MED_NAME) ?: "Medicine"

        if (scheduleId == -1L || medId == -1L) return

        val medication = repo.getMedicationById(medId) ?: return

        // ── Create or retrieve the DoseLog entry ──────────────────────────────
        val existing = repo.findExistingDoseLog(scheduleId, scheduledAt)
        val doseLogId: Long
        if (existing != null) {
            if (existing.status == DoseStatus.TAKEN || existing.status == DoseStatus.SKIPPED) return
            doseLogId = existing.id
        } else {
            doseLogId = repo.insertDoseLog(
                DoseLog(
                    medicationId = medId,
                    scheduleId = scheduleId,
                    scheduledAt = scheduledAt,
                    status = DoseStatus.PENDING
                )
            )
        }

        // ── Check driving mode ────────────────────────────────────────────────
        val isDriving = prefs.getBoolean(Constants.PREF_DRIVING_ACTIVE, false)

        // ── Check mute state ──────────────────────────────────────────────────
        val muteUntil = prefs.getLong(Constants.PREF_MUTE_UNTIL, 0L)
        val isMuted = System.currentTimeMillis() < muteUntil

        // ── Check food context ────────────────────────────────────────────────
        val requiresMeal = medication.foodRelation == FoodRelation.AFTER_FOOD ||
                           medication.foodRelation == FoodRelation.WITH_FOOD

        if (requiresMeal && !isDriving) {
            // Determine which meal to look for based on time of day
            val mealType = inferMealType()
            val recentMealTime = repo.getRecentMealTime(mealType, windowMinutes = 90)

            if (recentMealTime == null) {
                // Meal not logged yet — show "Did you eat?" notification
                val notification = NotificationHelper.buildMissingMealNotification(
                    context, medName, doseLogId, medId, mealType.name
                )
                NotificationHelper.showNotification(
                    context,
                    (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(),
                    notification
                )
                // Schedule the meal-window alarm so it re-fires 10 mins after user logs meal
                AlarmScheduler.scheduleMealWindowAlarm(context, doseLogId, medId, medName, mealType.name)
                // Update status to NAGGING
                repo.updateDoseStatus(doseLogId, DoseStatus.NAGGING, nagCount = 0,
                    nextNagAt = System.currentTimeMillis() + Constants.NAG_INTERVAL_MS)
                return
            }
            // Meal was logged — check if 10 min window has passed
            val mealElapsed = System.currentTimeMillis() - recentMealTime
            if (mealElapsed < Constants.MEAL_WINDOW_MS) {
                // Schedule to fire when 10 min window opens
                AlarmScheduler.scheduleMealWindowAlarm(context, doseLogId, medId, medName, mealType.name)
                return
            }
        }

        // ── If driving, log as SUPPRESSED and use TTS ─────────────────────────
        if (isDriving) {
            repo.updateDoseStatus(doseLogId, DoseStatus.SUPPRESSED,
                suppressReason = "DRIVING") // Keep in NAGGING so it retries on park
            val notification = NotificationHelper.buildDoseNotification(
                context, doseLogId, medId, medName, scheduledAt, 0,
                isMuted = false, isDriving = true
            )
            NotificationHelper.showNotification(
                context, (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(), notification
            )
            // TTS is handled by DrivingModeService — fire intent
            context.sendBroadcast(Intent("com.familyhealth.medicinetracker.ACTION_TTS").apply {
                putExtra(Constants.EXTRA_MED_NAME, medName)
            })
            return
        }

        // ── Normal dose notification + start nag loop ─────────────────────────
        repo.updateDoseStatus(
            doseLogId, DoseStatus.NAGGING, nagCount = 0,
            nextNagAt = System.currentTimeMillis() + Constants.NAG_INTERVAL_MS
        )

        val notification = NotificationHelper.buildDoseNotification(
            context, doseLogId, medId, medName, scheduledAt,
            nagCount = 0, isMuted = isMuted, isDriving = false
        )
        NotificationHelper.showNotification(
            context, (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(), notification
        )

        // Schedule the first nag
        AlarmScheduler.scheduleNagAlarm(
            context, doseLogId, medId, medName, nagCount = 1, scheduledAt
        )

        // Schedule tomorrow's dose alarm
        val schedule = repo.getSchedulesForMedication(medId).find { it.id == scheduleId }
        schedule?.let {
            val medication2 = repo.getMedicationById(medId)
            medication2?.let { med ->
                AlarmScheduler.scheduleDoseAlarm(context, it, medId, med.name)
            }
        }
    }

    private suspend fun handleMealAlarm(
        context: Context,
        intent: Intent,
        repo: com.familyhealth.medicinetracker.data.repository.MedicineRepository,
        prefs: android.content.SharedPreferences
    ) {
        val doseLogId = intent.getLongExtra(Constants.EXTRA_DOSE_LOG_ID, -1L)
        val medId     = intent.getLongExtra(Constants.EXTRA_MED_ID, -1L)
        val medName   = intent.getStringExtra(Constants.EXTRA_MED_NAME) ?: "Medicine"
        val mealType  = intent.getStringExtra(Constants.EXTRA_MEAL_TYPE) ?: "meal"

        if (doseLogId == -1L) return

        val doseLog = repo.getDoseLogById(doseLogId) ?: return
        if (doseLog.status == DoseStatus.TAKEN || doseLog.status == DoseStatus.SKIPPED) return

        val isMuted = System.currentTimeMillis() <
                prefs.getLong(Constants.PREF_MUTE_UNTIL, 0L)

        repo.updateDoseStatus(doseLogId, DoseStatus.NAGGING, nagCount = 0,
            nextNagAt = System.currentTimeMillis() + Constants.NAG_INTERVAL_MS)

        val notification = NotificationHelper.buildMealWindowNotification(
            context, medId, medName, mealType, doseLogId
        )
        NotificationHelper.showNotification(
            context, (Constants.NOTIF_BASE_DOSE + doseLogId).toInt(), notification
        )

        AlarmScheduler.scheduleNagAlarm(context, doseLogId, medId, medName, 1, doseLog.scheduledAt)
    }

    /** Infer which meal type is most appropriate based on current time */
    private fun inferMealType(): MealType {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 11 -> MealType.BREAKFAST
            hour < 16 -> MealType.LUNCH
            else      -> MealType.DINNER
        }
    }
}
