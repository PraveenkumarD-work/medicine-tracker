package com.familyhealth.medicinetracker.domain.model

import androidx.room.*

// ─────────────────────────────────────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────────────────────────────────────

enum class DoseStatus {
    PENDING,    // Alarm scheduled, not yet fired
    NAGGING,    // Alarm fired, 5-min loop active
    TAKEN,      // User confirmed taken
    SKIPPED,    // User explicitly skipped
    SNOOZED,    // Muted for 1hr (visual only, sound suppressed)
    SUPPRESSED  // Suppressed by driving mode — logged so adherence stays clean
}

enum class FoodRelation {
    BEFORE_FOOD,
    AFTER_FOOD,
    WITH_FOOD,
    ANYTIME
}

enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}

enum class FrequencyType {
    ONCE_DAILY,
    TWICE_DAILY,
    THRICE_DAILY,
    EVERY_X_HOURS,
    AS_NEEDED
}

// ─────────────────────────────────────────────────────────────────────────────
// MEDICINE CATALOG (pre-populated offline, FTS4 indexed)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "medicine_catalog")
data class MedicineCatalog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val name: String,
    val genericName: String = "",
    val category: String = "",          // e.g. Antibiotic, Painkiller
    val defaultDosage: String = ""      // e.g. "500mg"
)

// FTS virtual table for fast offline search
@Fts4(contentEntity = MedicineCatalog::class)
@Entity(tableName = "medicine_catalog_fts")
data class MedicineCatalogFts(
    val name: String,
    val genericName: String,
    val category: String
)

// ─────────────────────────────────────────────────────────────────────────────
// MEDICATIONS (what a user is taking)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosageAmount: String,           // "500mg", "1 tablet"
    val totalStock: Int,                // Number of pills/tablets on hand
    val dailyDosageCount: Int,          // How many doses per day (for stock math)
    val foodRelation: FoodRelation,
    val frequencyType: FrequencyType,
    val intervalHours: Int = 0,         // Used if EVERY_X_HOURS
    val expiryDate: Long = 0L,          // epoch ms; 0 = no expiry
    val notes: String = "",
    val doctorId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULES (when to take each medication)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "schedules",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("medicationId")]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val targetHour: Int,                // 0-23
    val targetMinute: Int,              // 0-59
    val label: String = "",             // "Morning dose", "Night dose"
    val isEnabled: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// DOSE LOG (every alarm firing & user action, with full state machine)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "dose_log",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("medicationId"), Index("scheduleId"), Index("scheduledAt")]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long?,
    val scheduledAt: Long,              // epoch ms — the intended dose time
    val actualAt: Long? = null,         // epoch ms — when user actually took it
    val status: DoseStatus = DoseStatus.PENDING,
    val nextNagAt: Long? = null,        // epoch ms — when next 5-min nag fires
    val nagCount: Int = 0,              // how many times we've nagged
    val suppressReason: String? = null, // "DRIVING" if status=SUPPRESSED
    val notes: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// USER EVENTS (meal logs — the food context anchors)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "user_events")
data class UserEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealType: MealType,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// DOCTORS
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "doctors")
data class Doctor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val specialty: String = "",
    val phone: String = "",
    val nextAppointment: Long? = null,  // epoch ms
    val notes: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// RELATION HELPERS
// ─────────────────────────────────────────────────────────────────────────────

data class MedicationWithSchedules(
    @Embedded val medication: Medication,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val schedules: List<Schedule>
)

data class TodayDose(
    @Embedded val doseLog: DoseLog,
    @Relation(parentColumn = "medicationId", entityColumn = "id")
    val medication: Medication
)
