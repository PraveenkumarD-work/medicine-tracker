package com.familyhealth.medicinetracker.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.familyhealth.medicinetracker.domain.model.DoseLog
import com.familyhealth.medicinetracker.domain.model.Schedule
import com.familyhealth.medicinetracker.util.Constants
import java.util.Calendar

/**
 * Central scheduler — wraps AlarmManager with exact alarm support.
 * All alarms set here survive device restarts (BootReceiver re-registers them).
 */
object AlarmScheduler {

    /**
     * Schedule today's and tomorrow's dose alarm for a given Schedule.
     * Called when a new medication is added or schedules change.
     */
    fun scheduleDoseAlarm(
        context: Context,
        schedule: Schedule,
        medId: Long,
        medName: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calculate next trigger time for this schedule
        val triggerAt = nextTriggerTime(schedule.targetHour, schedule.targetMinute)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = Constants.ACTION_DOSE_ALARM
            putExtra(Constants.EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(Constants.EXTRA_MED_ID, medId)
            putExtra(Constants.EXTRA_SCHEDULED_AT, triggerAt)
            putExtra(Constants.EXTRA_MED_NAME, medName)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            scheduleRequestCode(schedule.id, triggerAt),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setAlarmClock — it is the only method guaranteed to fire on all
        // Android 10+ manufacturers (Samsung, Xiaomi, OnePlus). It shows a
        // clock icon in the status bar — acceptable UX for a medicine app.
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, pi)
        alarmManager.setAlarmClock(alarmClockInfo, pi)
    }

    /**
     * Schedule the 5-minute nag alarm (repeat until TAKEN/SKIPPED).
     */
    fun scheduleNagAlarm(
        context: Context,
        doseLogId: Long,
        medId: Long,
        medName: String,
        nagCount: Int,
        scheduledAt: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + Constants.NAG_INTERVAL_MS

        val intent = Intent(context, NagReceiver::class.java).apply {
            action = Constants.ACTION_NAG_ALARM
            putExtra(Constants.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(Constants.EXTRA_MED_ID, medId)
            putExtra(Constants.EXTRA_MED_NAME, medName)
            putExtra(Constants.EXTRA_NAG_COUNT, nagCount)
            putExtra(Constants.EXTRA_SCHEDULED_AT, scheduledAt)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            nagRequestCode(doseLogId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, pi)
        alarmManager.setAlarmClock(alarmClockInfo, pi)
    }

    /**
     * Schedule the 10-minute post-meal alarm for after-food medications.
     */
    fun scheduleMealWindowAlarm(
        context: Context,
        doseLogId: Long,
        medId: Long,
        medName: String,
        mealType: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + Constants.MEAL_WINDOW_MS

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = Constants.ACTION_MEAL_ALARM
            putExtra(Constants.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(Constants.EXTRA_MED_ID, medId)
            putExtra(Constants.EXTRA_MED_NAME, medName)
            putExtra(Constants.EXTRA_MEAL_TYPE, mealType)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            mealRequestCode(medId, mealType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, pi)
        alarmManager.setAlarmClock(alarmClockInfo, pi)
    }

    /**
     * Cancel a previously set nag alarm (called when user taps TAKEN/SKIP/MUTE).
     */
    fun cancelNagAlarm(context: Context, doseLogId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NagReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            nagRequestCode(doseLogId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    /**
     * Cancel a schedule's dose alarm (called when medication is deactivated).
     */
    fun cancelScheduleAlarm(context: Context, scheduleId: Long, triggerAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            scheduleRequestCode(scheduleId, triggerAt),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun nextTriggerTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If today's time has already passed, schedule for tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // Unique request codes — must not collide between different alarm types
    private fun scheduleRequestCode(scheduleId: Long, triggerAt: Long): Int =
        (scheduleId * 1000 + (triggerAt / 60000) % 1000).toInt()

    private fun nagRequestCode(doseLogId: Long): Int =
        (50000 + doseLogId).toInt()

    private fun mealRequestCode(medId: Long, mealType: String): Int =
        (80000 + medId + mealType.hashCode()).toInt()
}
