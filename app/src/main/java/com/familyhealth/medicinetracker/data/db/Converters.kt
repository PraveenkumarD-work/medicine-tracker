package com.familyhealth.medicinetracker.data.db

import androidx.room.TypeConverter
import com.familyhealth.medicinetracker.domain.model.*

class Converters {
    @TypeConverter fun fromDoseStatus(v: DoseStatus): String = v.name
    @TypeConverter fun toDoseStatus(v: String): DoseStatus = DoseStatus.valueOf(v)

    @TypeConverter fun fromFoodRelation(v: FoodRelation): String = v.name
    @TypeConverter fun toFoodRelation(v: String): FoodRelation = FoodRelation.valueOf(v)

    @TypeConverter fun fromMealType(v: MealType): String = v.name
    @TypeConverter fun toMealType(v: String): MealType = MealType.valueOf(v)

    @TypeConverter fun fromFrequencyType(v: FrequencyType): String = v.name
    @TypeConverter fun toFrequencyType(v: String): FrequencyType = FrequencyType.valueOf(v)
}
