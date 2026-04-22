package com.familyhealth.medicinetracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.familyhealth.medicinetracker.domain.model.*

// ─────────────────────────────────────────────────────────────────────────────
// MEDICATION DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(med: Medication): Long

    @Update
    suspend fun update(med: Medication)

    @Delete
    suspend fun delete(med: Medication)

    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): LiveData<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Transaction
    @Query("SELECT * FROM medications WHERE isActive = 1")
    fun getAllWithSchedules(): LiveData<List<MedicationWithSchedules>>

    // Stock check: stock - (dailyDosageCount * 3) <= 0
    @Query("""
        SELECT * FROM medications
        WHERE isActive = 1
        AND (totalStock - (dailyDosageCount * 3)) <= 0
    """)
    fun getLowStockMedications(): LiveData<List<Medication>>

    @Query("UPDATE medications SET totalStock = totalStock - 1 WHERE id = :id AND totalStock > 0")
    suspend fun decrementStock(id: Long)

    @Query("UPDATE medications SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULE DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<Schedule>)

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("SELECT * FROM schedules WHERE medicationId = :medId AND isEnabled = 1")
    suspend fun getForMedication(medId: Long): List<Schedule>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<Schedule>

    @Query("DELETE FROM schedules WHERE medicationId = :medId")
    suspend fun deleteForMedication(medId: Long)
}

// ─────────────────────────────────────────────────────────────────────────────
// DOSE LOG DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface DoseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DoseLog): Long

    @Update
    suspend fun update(log: DoseLog)

    // Get today's doses (midnight to midnight)
    @Transaction
    @Query("""
        SELECT * FROM dose_log
        WHERE scheduledAt >= :dayStart AND scheduledAt < :dayEnd
        ORDER BY scheduledAt ASC
    """)
    fun getTodayDoses(dayStart: Long, dayEnd: Long): LiveData<List<TodayDose>>

    // Get a specific dose log entry
    @Query("SELECT * FROM dose_log WHERE id = :id")
    suspend fun getById(id: Long): DoseLog?

    // Find an existing log for a specific schedule + time (avoid duplicates)
    @Query("""
        SELECT * FROM dose_log
        WHERE scheduleId = :scheduleId AND scheduledAt = :scheduledAt
        LIMIT 1
    """)
    suspend fun findExisting(scheduleId: Long, scheduledAt: Long): DoseLog?

    // All NAGGING entries (for boot-restore of the nag loop)
    @Query("SELECT * FROM dose_log WHERE status = 'NAGGING'")
    suspend fun getAllNagging(): List<DoseLog>

    // Update status + actual time
    @Query("""
        UPDATE dose_log
        SET status = :status, actualAt = :actualAt, nagCount = :nagCount, nextNagAt = :nextNagAt
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: Long,
        status: DoseStatus,
        actualAt: Long? = null,
        nagCount: Int = 0,
        nextNagAt: Long? = null
    )

    // History for a medication (last 30 days)
    @Transaction
    @Query("""
        SELECT * FROM dose_log
        WHERE medicationId = :medId
        AND scheduledAt >= :since
        ORDER BY scheduledAt DESC
    """)
    fun getHistory(medId: Long, since: Long): LiveData<List<DoseLog>>

    // Adherence rate: taken / (taken + skipped) in a window
    @Query("""
        SELECT COUNT(*) FROM dose_log
        WHERE medicationId = :medId
        AND status = 'TAKEN'
        AND scheduledAt >= :since
    """)
    suspend fun countTaken(medId: Long, since: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_log
        WHERE medicationId = :medId
        AND status IN ('TAKEN','SKIPPED','SUPPRESSED')
        AND scheduledAt >= :since
    """)
    suspend fun countTotal(medId: Long, since: Long): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// USER EVENTS (MEALS) DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface UserEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: UserEvent): Long

    // Get most recent meal of a type within a time window
    @Query("""
        SELECT * FROM user_events
        WHERE mealType = :mealType
        AND timestamp >= :since
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getRecentMeal(mealType: MealType, since: Long): UserEvent?

    // Today's meals
    @Query("""
        SELECT * FROM user_events
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
        ORDER BY timestamp DESC
    """)
    fun getTodayEvents(dayStart: Long, dayEnd: Long): LiveData<List<UserEvent>>
}

// ─────────────────────────────────────────────────────────────────────────────
// DOCTOR DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface DoctorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doctor: Doctor): Long

    @Update
    suspend fun update(doctor: Doctor)

    @Delete
    suspend fun delete(doctor: Doctor)

    @Query("SELECT * FROM doctors ORDER BY nextAppointment ASC")
    fun getAll(): LiveData<List<Doctor>>

    @Query("SELECT * FROM doctors WHERE nextAppointment IS NOT NULL AND nextAppointment > :now ORDER BY nextAppointment ASC LIMIT 1")
    fun getNextAppointment(now: Long): LiveData<Doctor?>
}

// ─────────────────────────────────────────────────────────────────────────────
// MEDICINE CATALOG DAO (offline search)
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface MedicineCatalogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(medicines: List<MedicineCatalog>)

    // FTS4 full-text search — returns up to 20 results
    @Query("""
        SELECT medicine_catalog.* FROM medicine_catalog
        JOIN medicine_catalog_fts ON medicine_catalog.rowid = medicine_catalog_fts.rowid
        WHERE medicine_catalog_fts MATCH :query
        LIMIT 20
    """)
    suspend fun search(query: String): List<MedicineCatalog>

    @Query("SELECT COUNT(*) FROM medicine_catalog")
    suspend fun count(): Int
}
