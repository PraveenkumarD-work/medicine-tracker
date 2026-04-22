package com.familyhealth.medicinetracker.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.presentation.ui.MainActivity
import com.familyhealth.medicinetracker.util.Constants

object NotificationHelper {

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ── Dose reminder channel — HIGH importance, custom sound ─────────────
        val pillSoundUri: Uri = Uri.parse(
            "android.resource://${context.packageName}/raw/pills_shaking"
        )
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val doseChannel = NotificationChannel(
            Constants.CHANNEL_DOSE,
            "Dose Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pill-taking reminders with sound"
            setSound(pillSoundUri, audioAttr)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // ── Stock alert channel — DEFAULT importance ───────────────────────────
        val stockChannel = NotificationChannel(
            Constants.CHANNEL_STOCK,
            "Low Stock Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when medicine is running low"
        }

        // ── Meal context channel — DEFAULT importance ──────────────────────────
        val mealChannel = NotificationChannel(
            Constants.CHANNEL_MEAL,
            "Meal Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Post-meal medicine reminders"
        }

        // ── Driving mode channel — LOW importance (visual only) ───────────────
        val drivingChannel = NotificationChannel(
            Constants.CHANNEL_DRIVING,
            "Driving Mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent alerts while driving"
        }

        nm.createNotificationChannels(listOf(doseChannel, stockChannel, mealChannel, drivingChannel))
    }

    // ── Dose notification with action buttons ─────────────────────────────────

    fun buildDoseNotification(
        context: Context,
        doseLogId: Long,
        medId: Long,
        medName: String,
        scheduledAt: Long,
        nagCount: Int,
        isMuted: Boolean,
        isDriving: Boolean
    ): Notification {
        val channel = if (isDriving) Constants.CHANNEL_DRIVING else Constants.CHANNEL_DOSE

        val tapIntent = PendingIntent.getActivity(
            context,
            (doseLogId + 100).toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(Constants.EXTRA_DOSE_LOG_ID, doseLogId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            isDriving -> "⚠️ Medicine Due (Driving)"
            nagCount == 0 -> "💊 Time for $medName"
            else -> "⏰ Reminder #${nagCount + 1}: $medName"
        }

        val message = when {
            isDriving -> "You have $medName due. Take it when you reach your destination."
            nagCount == 0 -> "It's time to take your $medName."
            else -> "You still haven't taken $medName. Please take it now."
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .setOngoing(true)                    // sticky — can't be swiped away
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Suppress sound in driving or muted mode
        if (isMuted || isDriving) {
            builder.setSilent(true)
        }

        // ── Action buttons ────────────────────────────────────────────────────
        if (!isDriving) {
            builder.addAction(buildAction(context, "✅ Taken", Constants.ACTION_TAKEN, doseLogId, medId))
            builder.addAction(buildAction(context, "⏭ Skip", Constants.ACTION_SKIP, doseLogId, medId))
            if (!isMuted) {
                builder.addAction(buildAction(context, "🔇 Mute 1hr", Constants.ACTION_MUTE, doseLogId, medId))
            }
        } else {
            builder.addAction(buildAction(context, "✅ Taken", Constants.ACTION_TAKEN, doseLogId, medId))
        }

        return builder.build()
    }

    fun buildMealWindowNotification(
        context: Context,
        medId: Long,
        medName: String,
        mealType: String,
        doseLogId: Long
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            (medId + 200).toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.CHANNEL_MEAL)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("🍽️ Ready for $medName")
            .setContentText("You ate 10 mins ago. Your stomach is ready for $medName.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You ate $mealType 10 minutes ago. Your stomach is now ready — take your $medName."))
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(buildAction(context, "✅ Taken", Constants.ACTION_TAKEN, doseLogId, medId))
            .addAction(buildAction(context, "⏭ Skip", Constants.ACTION_SKIP, doseLogId, medId))
            .build()
    }

    fun buildLowStockNotification(context: Context, medName: String, stock: Int): Notification {
        return NotificationCompat.Builder(context, Constants.CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("📦 Low Stock: $medName")
            .setContentText("Only $stock tablets remaining. Time to refill.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    fun buildMissingMealNotification(
        context: Context,
        medName: String,
        doseLogId: Long,
        medId: Long,
        mealType: String
    ): Notification {
        return NotificationCompat.Builder(context, Constants.CHANNEL_MEAL)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("🍽️ Did you eat? — $medName")
            .setContentText("$medName should be taken after $mealType. Log your meal or tap Skip.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(buildAction(context, "✅ Taken", Constants.ACTION_TAKEN, doseLogId, medId))
            .addAction(buildAction(context, "⏭ Skip", Constants.ACTION_SKIP, doseLogId, medId))
            .build()
    }

    fun cancelNotification(context: Context, doseLogId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel((Constants.NOTIF_BASE_DOSE + doseLogId).toInt())
    }

    fun showNotification(context: Context, id: Int, notification: Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun buildAction(
        context: Context,
        label: String,
        action: String,
        doseLogId: Long,
        medId: Long
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(Constants.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(Constants.EXTRA_MED_ID, medId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (doseLogId * 10 + action.hashCode()).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pi)
    }
}
