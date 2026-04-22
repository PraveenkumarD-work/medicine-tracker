package com.familyhealth.medicinetracker.service.driving

import android.app.*
import android.content.*
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.util.Constants
import com.google.android.gms.location.*
import java.util.Locale

/**
 * Foreground service that monitors ActivityRecognition and toggles
 * driving mode. When IN_VEHICLE is detected at high confidence:
 *  - Sets PREF_DRIVING_ACTIVE = true
 *  - Suppresses sound in all alarms
 *  - Speaks pending medicine names via TTS
 *
 * Important: Driving mode only starts if the user enables it in Settings.
 * This avoids battery drain for family members who don't drive.
 */
class DrivingModeService : Service(), TextToSpeech.OnInitListener {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var tts: TextToSpeech? = null
    private var pendingTtsMessage: String? = null

    private val ttsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val medName = intent.getStringExtra(Constants.EXTRA_MED_NAME) ?: return
            speakMedicineAlert(medName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        registerReceiver(ttsReceiver,
            IntentFilter("com.familyhealth.medicinetracker.ACTION_TTS"),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(9999, buildForegroundNotification())
        startActivityRecognition()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ttsReceiver)
        stopActivityRecognition()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── TextToSpeech ──────────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            pendingTtsMessage?.let {
                tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "med_alert")
                pendingTtsMessage = null
            }
        }
    }

    private fun speakMedicineAlert(medName: String) {
        val message = "You have $medName due. Please take it when you reach your destination."
        if (tts?.isSpeaking == true) tts?.stop()
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "med_alert")
        if (result == TextToSpeech.ERROR) {
            pendingTtsMessage = message // Will speak once TTS is ready
        }
    }

    // ── Activity Recognition ──────────────────────────────────────────────────

    private fun startActivityRecognition() {
        activityRecognitionClient = ActivityRecognition.getClient(this)
        val request = ActivityTransitionRequest(buildTransitions())

        activityRecognitionClient.requestActivityTransitionUpdates(
            request,
            getPendingIntent()
        )
    }

    private fun stopActivityRecognition() {
        if (::activityRecognitionClient.isInitialized) {
            activityRecognitionClient.removeActivityTransitionUpdates(getPendingIntent())
        }
    }

    private fun buildTransitions(): List<ActivityTransition> {
        return listOf(
            // Enter vehicle
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            // Exit vehicle
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            // Also track STILL — when they park and stop moving
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, ActivityTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_DRIVING)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Driving Mode Active")
            .setContentText("Medicine alerts will be spoken, not sounded.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
