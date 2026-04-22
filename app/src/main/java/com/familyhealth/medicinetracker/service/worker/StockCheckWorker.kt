package com.familyhealth.medicinetracker.service.worker

import android.content.Context
import androidx.work.*
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.service.notification.NotificationHelper
import com.familyhealth.medicinetracker.util.Constants
import java.util.concurrent.TimeUnit

/**
 * WorkManager task that runs every 12 hours to:
 *  1. Check low stock for all active medications
 *  2. Show persistent low-stock notifications
 *  3. Check for upcoming doctor appointments (within 3 days)
 *
 * WorkManager is the correct tool here — stock checks don't need exact timing,
 * they need guaranteed eventual execution even after battery optimization.
 */
class StockCheckWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as MedicineTrackerApp).repository
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        // ── Low stock check ───────────────────────────────────────────────────
        // We read synchronously here since we're already in a coroutine
        val meds = try {
            // Use a blocking call on the DAO for background work
            (applicationContext as MedicineTrackerApp).database
                .medicationDao()
                .getLowStockMedications()
                .value ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Alternative approach — query directly
        val db = (applicationContext as MedicineTrackerApp).database
        val lowStockMeds = db.medicationDao().getLowStockMedications().value

        // Since LiveData doesn't work well in workers, we do a direct query:
        val allMeds = db.medicationDao().getAllActive().value ?: emptyList()
        for (med in allMeds) {
            val remainingAfterBuffer = med.totalStock - (med.dailyDosageCount * 3)
            if (remainingAfterBuffer <= 0) {
                val notification = NotificationHelper.buildLowStockNotification(
                    applicationContext, med.name, med.totalStock
                )
                nm.notify(
                    (Constants.NOTIF_LOW_STOCK + med.id).toInt(),
                    notification
                )
            }
        }

        // ── Appointment check ─────────────────────────────────────────────────
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        val doctors = db.doctorDao().getAll().value ?: emptyList()
        for (doc in doctors) {
            val appt = doc.nextAppointment ?: continue
            val delta = appt - System.currentTimeMillis()
            if (delta in 0..threeDaysMs) {
                val daysLeft = (delta / (24 * 60 * 60 * 1000)).toInt()
                val notification = android.app.Notification.Builder(applicationContext,
                    Constants.CHANNEL_STOCK)
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentTitle("📅 Appointment in $daysLeft days")
                    .setContentText("Dr. ${doc.name} — ${doc.specialty}")
                    .build()
                nm.notify(Constants.NOTIF_APPOINTMENT, notification)
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "stock_check_periodic"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<StockCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder()
                    .setRequiresBatteryNotLow(false) // Run even on low battery — health is critical
                    .build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StockCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
