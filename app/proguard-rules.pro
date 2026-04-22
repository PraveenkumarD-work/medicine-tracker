# Keep Room entities
-keep class com.familyhealth.medicinetracker.domain.model.** { *; }
-keep class com.familyhealth.medicinetracker.data.db.** { *; }

# Keep enum names (used as type converters)
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Google Play Services (Activity Recognition)
-keep class com.google.android.gms.** { *; }
