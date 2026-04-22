package com.familyhealth.medicinetracker.data.repository

import androidx.lifecycle.LiveData
import com.familyhealth.medicinetracker.data.db.*
import com.familyhealth.medicinetracker.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class MedicineRepository(private val db: AppDatabase) {

    // ── Medication CRUD ───────────────────────────────────────────────────────
    val allActiveMedications: LiveData<List<Medication>> = db.medicationDao().getAllActive()
    val allMedicationsWithSchedules: LiveData<List<MedicationWithSchedules>> =
        db.medicationDao().getAllWithSchedules()
    val lowStockMedications: LiveData<List<Medication>> = db.medicationDao().getLowStockMedications()

    suspend fun insertMedication(med: Medication): Long =
        withContext(Dispatchers.IO) { db.medicationDao().insert(med) }

    suspend fun updateMedication(med: Medication) =
        withContext(Dispatchers.IO) { db.medicationDao().update(med) }

    suspend fun getMedicationById(id: Long): Medication? =
        withContext(Dispatchers.IO) { db.medicationDao().getById(id) }

    suspend fun decrementStock(medId: Long) =
        withContext(Dispatchers.IO) { db.medicationDao().decrementStock(medId) }

    suspend fun deactivateMedication(medId: Long) =
        withContext(Dispatchers.IO) { db.medicationDao().deactivate(medId) }

    // ── Schedule CRUD ─────────────────────────────────────────────────────────
    suspend fun insertSchedule(schedule: Schedule): Long =
        withContext(Dispatchers.IO) { db.scheduleDao().insert(schedule) }

    suspend fun insertSchedules(schedules: List<Schedule>) =
        withContext(Dispatchers.IO) { db.scheduleDao().insertAll(schedules) }

    suspend fun updateSchedule(schedule: Schedule) =
        withContext(Dispatchers.IO) { db.scheduleDao().update(schedule) }

    suspend fun deleteSchedule(schedule: Schedule) =
        withContext(Dispatchers.IO) { db.scheduleDao().delete(schedule) }

    suspend fun getSchedulesForMedication(medId: Long): List<Schedule> =
        withContext(Dispatchers.IO) { db.scheduleDao().getForMedication(medId) }

    suspend fun getAllEnabledSchedules(): List<Schedule> =
        withContext(Dispatchers.IO) { db.scheduleDao().getAllEnabled() }

    // ── Dose Log ──────────────────────────────────────────────────────────────
    fun getTodayDoses(): LiveData<List<TodayDose>> {
        val (start, end) = todayRange()
        return db.doseLogDao().getTodayDoses(start, end)
    }

    suspend fun insertDoseLog(log: DoseLog): Long =
        withContext(Dispatchers.IO) { db.doseLogDao().insert(log) }

    suspend fun updateDoseLog(log: DoseLog) =
        withContext(Dispatchers.IO) { db.doseLogDao().update(log) }

    suspend fun getDoseLogById(id: Long): DoseLog? =
        withContext(Dispatchers.IO) { db.doseLogDao().getById(id) }

    suspend fun findExistingDoseLog(scheduleId: Long, scheduledAt: Long): DoseLog? =
        withContext(Dispatchers.IO) { db.doseLogDao().findExisting(scheduleId, scheduledAt) }

    suspend fun updateDoseStatus(
        id: Long, status: DoseStatus,
        actualAt: Long? = null, nagCount: Int = 0, nextNagAt: Long? = null
    ) = withContext(Dispatchers.IO) {
        db.doseLogDao().updateStatus(id, status, actualAt, nagCount, nextNagAt)
    }

    suspend fun getAllNaggingLogs(): List<DoseLog> =
        withContext(Dispatchers.IO) { db.doseLogDao().getAllNagging() }

    fun getDoseHistory(medId: Long): LiveData<List<DoseLog>> {
        val since = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
        return db.doseLogDao().getHistory(medId, since)
    }

    suspend fun getAdherenceRate(medId: Long): Float {
        return withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val taken = db.doseLogDao().countTaken(medId, since)
            val total = db.doseLogDao().countTotal(medId, since)
            if (total == 0) 1f else taken.toFloat() / total.toFloat()
        }
    }

    // ── Meal Events ───────────────────────────────────────────────────────────
    fun getTodayEvents(): LiveData<List<UserEvent>> {
        val (start, end) = todayRange()
        return db.userEventDao().getTodayEvents(start, end)
    }

    suspend fun logMeal(mealType: MealType, notes: String = ""): Long =
        withContext(Dispatchers.IO) {
            db.userEventDao().insert(UserEvent(mealType = mealType, notes = notes))
        }

    /**
     * Returns the timestamp of the most recent meal of the given type
     * within the last [windowMinutes] minutes, or null if none found.
     */
    suspend fun getRecentMealTime(mealType: MealType, windowMinutes: Int = 120): Long? =
        withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - (windowMinutes * 60 * 1000L)
            db.userEventDao().getRecentMeal(mealType, since)?.timestamp
        }

    // ── Doctors ───────────────────────────────────────────────────────────────
    val allDoctors: LiveData<List<Doctor>> = db.doctorDao().getAll()
    val nextAppointment: LiveData<Doctor?> = db.doctorDao().getNextAppointment(System.currentTimeMillis())

    suspend fun insertDoctor(doctor: Doctor): Long =
        withContext(Dispatchers.IO) { db.doctorDao().insert(doctor) }

    suspend fun updateDoctor(doctor: Doctor) =
        withContext(Dispatchers.IO) { db.doctorDao().update(doctor) }

    suspend fun deleteDoctor(doctor: Doctor) =
        withContext(Dispatchers.IO) { db.doctorDao().delete(doctor) }

    // ── Medicine Catalog Search ───────────────────────────────────────────────
    suspend fun searchMedicines(query: String): List<MedicineCatalog> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) emptyList()
            else db.medicineCatalogDao().search("$query*") // prefix match
        }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + (24 * 60 * 60 * 1000L)
        return Pair(start, end)
    }
}
