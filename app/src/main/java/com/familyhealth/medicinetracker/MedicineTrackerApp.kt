package com.familyhealth.medicinetracker

import android.app.Application
import com.familyhealth.medicinetracker.data.db.AppDatabase
import com.familyhealth.medicinetracker.data.repository.MedicineRepository
import com.familyhealth.medicinetracker.service.notification.NotificationHelper
import com.familyhealth.medicinetracker.service.worker.StockCheckWorker

class MedicineTrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: MedicineRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        repository = MedicineRepository(database)

        // Create notification channels (must run before any alarm fires)
        NotificationHelper.createChannels(this)

        // Start periodic stock check
        StockCheckWorker.enqueue(this)
    }
}
