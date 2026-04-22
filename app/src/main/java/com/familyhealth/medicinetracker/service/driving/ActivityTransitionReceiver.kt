package com.familyhealth.medicinetracker.service.driving

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyhealth.medicinetracker.util.Constants
import com.google.android.gms.location.*

/**
 * Receives activity transition updates from Google Play Services.
 * Toggles the driving mode preference flag.
 *
 * NOTE: We record the transition in SharedPreferences so every
 * BroadcastReceiver/alarm can check it instantly without IPC.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        for (event in result.transitionEvents) {
            when {
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // Entered vehicle
                    prefs.edit().putBoolean(Constants.PREF_DRIVING_ACTIVE, true).apply()
                }
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    // Exited vehicle
                    prefs.edit().putBoolean(Constants.PREF_DRIVING_ACTIVE, false).apply()
                }
                event.activityType == DetectedActivity.STILL &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // Parked/stopped — clear driving mode
                    prefs.edit().putBoolean(Constants.PREF_DRIVING_ACTIVE, false).apply()
                }
            }
        }
    }
}
