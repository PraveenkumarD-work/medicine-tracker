package com.familyhealth.medicinetracker.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.data.repository.MedicineRepository
import com.familyhealth.medicinetracker.domain.model.*
import com.familyhealth.medicinetracker.service.alarm.AlarmScheduler
import com.familyhealth.medicinetracker.service.worker.StockCheckWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: MedicineRepository =
        (application as MedicineTrackerApp).repository

    // ── Observables ───────────────────────────────────────────────────────────
    val todayDoses: LiveData<List<TodayDose>> = repo.getTodayDoses()
    val todayEvents: LiveData<List<UserEvent>> = repo.getTodayEvents()
    val lowStockMeds: LiveData<List<Medication>> = repo.lowStockMedications
    val nextAppointment: LiveData<Doctor?> = repo.nextAppointment

    /** Countdown in ms to next appointment — updated every minute by UI */
    private val _appointmentCountdown = MutableLiveData<Long?>()
    val appointmentCountdown: LiveData<Long?> = _appointmentCountdown

    fun refreshAppointmentCountdown(appointmentTimestamp: Long?) {
        _appointmentCountdown.value = appointmentTimestamp?.let {
            it - System.currentTimeMillis()
        }
    }

    // ── Meal Logging ──────────────────────────────────────────────────────────
    fun logMeal(mealType: MealType) = viewModelScope.launch {
        repo.logMeal(mealType)
        // After logging meal, trigger meal-window alarms for any AFTER_FOOD meds
        // that are currently in NAGGING or PENDING state for this meal period
        triggerPostMealAlarms(mealType)
    }

    private suspend fun triggerPostMealAlarms(mealType: MealType) {
        val context = getApplication<MedicineTrackerApp>()
        val todayDoseList = todayDoses.value ?: return
        for (todayDose in todayDoseList) {
            val med = todayDose.medication
            val doseLog = todayDose.doseLog
            if (med.foodRelation != FoodRelation.AFTER_FOOD &&
                med.foodRelation != FoodRelation.WITH_FOOD) continue
            if (doseLog.status == DoseStatus.TAKEN ||
                doseLog.status == DoseStatus.SKIPPED) continue

            // Schedule 10-min window alarm now that meal is logged
            AlarmScheduler.scheduleMealWindowAlarm(
                context, doseLog.id, med.id, med.name, mealType.name
            )
        }
    }

    // ── Dose actions (called from notification or UI) ─────────────────────────
    fun markTaken(doseLogId: Long, medId: Long) = viewModelScope.launch {
        repo.updateDoseStatus(doseLogId, DoseStatus.TAKEN, actualAt = System.currentTimeMillis())
        repo.decrementStock(medId)
        // Trigger a stock check after taking — the level may have dropped
        StockCheckWorker.runNow(getApplication())
    }

    fun markSkipped(doseLogId: Long) = viewModelScope.launch {
        repo.updateDoseStatus(doseLogId, DoseStatus.SKIPPED, actualAt = System.currentTimeMillis())
    }
}

class DashboardViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(application) as T
    }
}
