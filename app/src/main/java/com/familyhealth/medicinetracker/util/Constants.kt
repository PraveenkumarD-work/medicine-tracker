package com.familyhealth.medicinetracker.util

object Constants {
    // Actions
    const val ACTION_DOSE_ALARM   = "com.familyhealth.medicinetracker.ACTION_DOSE_ALARM"
    const val ACTION_NAG_ALARM    = "com.familyhealth.medicinetracker.ACTION_NAG_ALARM"
    const val ACTION_TAKEN        = "com.familyhealth.medicinetracker.ACTION_TAKEN"
    const val ACTION_SKIP         = "com.familyhealth.medicinetracker.ACTION_SKIP"
    const val ACTION_MUTE         = "com.familyhealth.medicinetracker.ACTION_MUTE"
    const val ACTION_MEAL_ALARM   = "com.familyhealth.medicinetracker.ACTION_MEAL_ALARM"

    // Intent Extras
    const val EXTRA_DOSE_LOG_ID   = "extra_dose_log_id"
    const val EXTRA_MED_ID        = "extra_med_id"
    const val EXTRA_SCHEDULE_ID   = "extra_schedule_id"
    const val EXTRA_SCHEDULED_AT  = "extra_scheduled_at"
    const val EXTRA_MEAL_TYPE     = "extra_meal_type"
    const val EXTRA_MED_NAME      = "extra_med_name"
    const val EXTRA_NAG_COUNT     = "extra_nag_count"

    // Notification Channels
    const val CHANNEL_DOSE        = "channel_dose_reminder"
    const val CHANNEL_STOCK       = "channel_stock_alert"
    const val CHANNEL_MEAL        = "channel_meal_context"
    const val CHANNEL_DRIVING     = "channel_driving"

    // Notification IDs (base — doseLogId is added as offset)
    const val NOTIF_BASE_DOSE     = 1000
    const val NOTIF_LOW_STOCK     = 9001
    const val NOTIF_APPOINTMENT   = 9002

    // Timing
    const val NAG_INTERVAL_MS     = 5 * 60 * 1000L   // 5 minutes
    const val MUTE_DURATION_MS    = 60 * 60 * 1000L  // 1 hour
    const val MEAL_WINDOW_MS      = 10 * 60 * 1000L  // 10 minutes after eating
    const val MAX_NAG_COUNT       = 12               // 1 hour max nagging

    // Prefs
    const val PREFS_NAME          = "medicine_tracker_prefs"
    const val PREF_MUTE_UNTIL     = "mute_until"
    const val PREF_DRIVING_ACTIVE = "driving_mode_active"
}
