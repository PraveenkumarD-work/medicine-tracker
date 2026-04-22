package com.familyhealth.medicinetracker.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.familyhealth.medicinetracker.MedicineTrackerApp
import com.familyhealth.medicinetracker.data.repository.MedicineRepository
import com.familyhealth.medicinetracker.domain.model.*
import com.familyhealth.medicinetracker.service.alarm.AlarmScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddMedicineViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: MedicineRepository =
        (application as MedicineTrackerApp).repository

    // ── FTS Search ────────────────────────────────────────────────────────────
    private val _searchResults = MutableLiveData<List<MedicineCatalog>>()
    val searchResults: LiveData<List<MedicineCatalog>> = _searchResults

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(150) // debounce — don't hit DB on every keystroke
            val results = repo.searchMedicines(query)
            _searchResults.value = results
        }
    }

    // ── Save Medication ───────────────────────────────────────────────────────
    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    fun saveMedication(
        name: String,
        dosageAmount: String,
        totalStock: Int,
        dailyDosageCount: Int,
        foodRelation: FoodRelation,
        frequencyType: FrequencyType,
        intervalHours: Int,
        expiryDate: Long,
        notes: String,
        doctorId: Long?,
        scheduleTimes: List<Pair<Int, Int>>, // List of (hour, minute)
        scheduleLabels: List<String>
    ) = viewModelScope.launch {
        val context = getApplication<MedicineTrackerApp>()

        val medication = Medication(
            name = name,
            dosageAmount = dosageAmount,
            totalStock = totalStock,
            dailyDosageCount = dailyDosageCount,
            foodRelation = foodRelation,
            frequencyType = frequencyType,
            intervalHours = intervalHours,
            expiryDate = expiryDate,
            notes = notes,
            doctorId = doctorId
        )

        val medId = repo.insertMedication(medication)

        // Insert schedules and register alarms
        val schedules = scheduleTimes.mapIndexed { index, (hour, minute) ->
            Schedule(
                medicationId = medId,
                targetHour = hour,
                targetMinute = minute,
                label = scheduleLabels.getOrElse(index) { "Dose ${index + 1}" }
            )
        }
        schedules.forEach { schedule ->
            val scheduleId = repo.insertSchedule(schedule)
            val savedSchedule = schedule.copy(id = scheduleId)
            AlarmScheduler.scheduleDoseAlarm(context, savedSchedule, medId, name)
        }

        _saveSuccess.value = true
    }

    // ── Edit Existing Medication ──────────────────────────────────────────────
    private val _currentMedication = MutableLiveData<Medication?>()
    val currentMedication: LiveData<Medication?> = _currentMedication

    fun loadMedication(medId: Long) = viewModelScope.launch {
        _currentMedication.value = repo.getMedicationById(medId)
    }

    fun updateMedication(medication: Medication) = viewModelScope.launch {
        repo.updateMedication(medication)
        _saveSuccess.value = true
    }

    fun deleteMedication(medId: Long) = viewModelScope.launch {
        repo.deactivateMedication(medId)
    }
}

class AddMedicineViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AddMedicineViewModel(application) as T
    }
}
