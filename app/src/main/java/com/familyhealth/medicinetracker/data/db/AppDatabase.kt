package com.familyhealth.medicinetracker.data.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.familyhealth.medicinetracker.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Medication::class,
        Schedule::class,
        DoseLog::class,
        UserEvent::class,
        Doctor::class,
        MedicineCatalog::class,
        MedicineCatalogFts::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun userEventDao(): UserEventDao
    abstract fun doctorDao(): DoctorDao
    abstract fun medicineCatalogDao(): MedicineCatalogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "medicine_tracker.db"
            )
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed medicine catalog on first launch
                    CoroutineScope(Dispatchers.IO).launch {
                        INSTANCE?.let { seedMedicineCatalog(it) }
                    }
                }
            })
            .build()
        }

        /**
         * Seeds ~200 common Indian medicines into the catalog for offline FTS search.
         * All data is bundled in the APK — zero network required.
         */
        private suspend fun seedMedicineCatalog(db: AppDatabase) {
            val dao = db.medicineCatalogDao()
            if (dao.count() > 0) return // Already seeded

            val medicines = listOf(
                // ── Antibiotics ──────────────────────────────────────────
                MedicineCatalog(name = "Amoxicillin", genericName = "Amoxicillin", category = "Antibiotic", defaultDosage = "500mg"),
                MedicineCatalog(name = "Azithromycin", genericName = "Azithromycin", category = "Antibiotic", defaultDosage = "500mg"),
                MedicineCatalog(name = "Ciprofloxacin", genericName = "Ciprofloxacin", category = "Antibiotic", defaultDosage = "500mg"),
                MedicineCatalog(name = "Doxycycline", genericName = "Doxycycline", category = "Antibiotic", defaultDosage = "100mg"),
                MedicineCatalog(name = "Metronidazole", genericName = "Metronidazole", category = "Antibiotic", defaultDosage = "400mg"),
                MedicineCatalog(name = "Norfloxacin", genericName = "Norfloxacin", category = "Antibiotic", defaultDosage = "400mg"),
                MedicineCatalog(name = "Cefixime", genericName = "Cefixime", category = "Antibiotic", defaultDosage = "200mg"),
                MedicineCatalog(name = "Cefpodoxime", genericName = "Cefpodoxime", category = "Antibiotic", defaultDosage = "200mg"),
                MedicineCatalog(name = "Clindamycin", genericName = "Clindamycin", category = "Antibiotic", defaultDosage = "300mg"),
                MedicineCatalog(name = "Levofloxacin", genericName = "Levofloxacin", category = "Antibiotic", defaultDosage = "500mg"),
                // ── Painkillers / NSAIDs ─────────────────────────────────
                MedicineCatalog(name = "Paracetamol", genericName = "Acetaminophen", category = "Painkiller", defaultDosage = "500mg"),
                MedicineCatalog(name = "Crocin", genericName = "Paracetamol", category = "Painkiller", defaultDosage = "500mg"),
                MedicineCatalog(name = "Ibuprofen", genericName = "Ibuprofen", category = "NSAID", defaultDosage = "400mg"),
                MedicineCatalog(name = "Brufen", genericName = "Ibuprofen", category = "NSAID", defaultDosage = "400mg"),
                MedicineCatalog(name = "Diclofenac", genericName = "Diclofenac Sodium", category = "NSAID", defaultDosage = "50mg"),
                MedicineCatalog(name = "Naproxen", genericName = "Naproxen", category = "NSAID", defaultDosage = "250mg"),
                MedicineCatalog(name = "Mefenamic Acid", genericName = "Mefenamic Acid", category = "NSAID", defaultDosage = "500mg"),
                MedicineCatalog(name = "Aspirin", genericName = "Acetylsalicylic Acid", category = "NSAID", defaultDosage = "75mg"),
                MedicineCatalog(name = "Etoricoxib", genericName = "Etoricoxib", category = "NSAID", defaultDosage = "60mg"),
                MedicineCatalog(name = "Aceclofenac", genericName = "Aceclofenac", category = "NSAID", defaultDosage = "100mg"),
                // ── Antacids / GI ────────────────────────────────────────
                MedicineCatalog(name = "Omeprazole", genericName = "Omeprazole", category = "Antacid", defaultDosage = "20mg"),
                MedicineCatalog(name = "Pantoprazole", genericName = "Pantoprazole", category = "Antacid", defaultDosage = "40mg"),
                MedicineCatalog(name = "Rabeprazole", genericName = "Rabeprazole", category = "Antacid", defaultDosage = "20mg"),
                MedicineCatalog(name = "Ranitidine", genericName = "Ranitidine", category = "Antacid", defaultDosage = "150mg"),
                MedicineCatalog(name = "Domperidone", genericName = "Domperidone", category = "Antiemetic", defaultDosage = "10mg"),
                MedicineCatalog(name = "Ondansetron", genericName = "Ondansetron", category = "Antiemetic", defaultDosage = "4mg"),
                MedicineCatalog(name = "Metoclopramide", genericName = "Metoclopramide", category = "Antiemetic", defaultDosage = "10mg"),
                MedicineCatalog(name = "Lactulose", genericName = "Lactulose", category = "Laxative", defaultDosage = "15ml"),
                MedicineCatalog(name = "Loperamide", genericName = "Loperamide", category = "Antidiarrheal", defaultDosage = "2mg"),
                MedicineCatalog(name = "Sucralfate", genericName = "Sucralfate", category = "Antacid", defaultDosage = "1g"),
                // ── Diabetes ─────────────────────────────────────────────
                MedicineCatalog(name = "Metformin", genericName = "Metformin HCl", category = "Antidiabetic", defaultDosage = "500mg"),
                MedicineCatalog(name = "Glimepiride", genericName = "Glimepiride", category = "Antidiabetic", defaultDosage = "2mg"),
                MedicineCatalog(name = "Glibenclamide", genericName = "Glibenclamide", category = "Antidiabetic", defaultDosage = "5mg"),
                MedicineCatalog(name = "Sitagliptin", genericName = "Sitagliptin", category = "Antidiabetic", defaultDosage = "100mg"),
                MedicineCatalog(name = "Vildagliptin", genericName = "Vildagliptin", category = "Antidiabetic", defaultDosage = "50mg"),
                MedicineCatalog(name = "Dapagliflozin", genericName = "Dapagliflozin", category = "Antidiabetic", defaultDosage = "10mg"),
                MedicineCatalog(name = "Empagliflozin", genericName = "Empagliflozin", category = "Antidiabetic", defaultDosage = "10mg"),
                MedicineCatalog(name = "Pioglitazone", genericName = "Pioglitazone", category = "Antidiabetic", defaultDosage = "15mg"),
                // ── Blood Pressure ───────────────────────────────────────
                MedicineCatalog(name = "Amlodipine", genericName = "Amlodipine", category = "Antihypertensive", defaultDosage = "5mg"),
                MedicineCatalog(name = "Telmisartan", genericName = "Telmisartan", category = "Antihypertensive", defaultDosage = "40mg"),
                MedicineCatalog(name = "Losartan", genericName = "Losartan", category = "Antihypertensive", defaultDosage = "50mg"),
                MedicineCatalog(name = "Enalapril", genericName = "Enalapril", category = "Antihypertensive", defaultDosage = "5mg"),
                MedicineCatalog(name = "Lisinopril", genericName = "Lisinopril", category = "Antihypertensive", defaultDosage = "10mg"),
                MedicineCatalog(name = "Ramipril", genericName = "Ramipril", category = "Antihypertensive", defaultDosage = "5mg"),
                MedicineCatalog(name = "Atenolol", genericName = "Atenolol", category = "Beta-blocker", defaultDosage = "50mg"),
                MedicineCatalog(name = "Metoprolol", genericName = "Metoprolol", category = "Beta-blocker", defaultDosage = "50mg"),
                MedicineCatalog(name = "Nebivolol", genericName = "Nebivolol", category = "Beta-blocker", defaultDosage = "5mg"),
                MedicineCatalog(name = "Hydrochlorothiazide", genericName = "Hydrochlorothiazide", category = "Diuretic", defaultDosage = "25mg"),
                MedicineCatalog(name = "Furosemide", genericName = "Furosemide", category = "Diuretic", defaultDosage = "40mg"),
                MedicineCatalog(name = "Spironolactone", genericName = "Spironolactone", category = "Diuretic", defaultDosage = "25mg"),
                // ── Cholesterol ──────────────────────────────────────────
                MedicineCatalog(name = "Atorvastatin", genericName = "Atorvastatin", category = "Statin", defaultDosage = "10mg"),
                MedicineCatalog(name = "Rosuvastatin", genericName = "Rosuvastatin", category = "Statin", defaultDosage = "10mg"),
                MedicineCatalog(name = "Simvastatin", genericName = "Simvastatin", category = "Statin", defaultDosage = "20mg"),
                MedicineCatalog(name = "Fenofibrate", genericName = "Fenofibrate", category = "Lipid-lowering", defaultDosage = "160mg"),
                MedicineCatalog(name = "Ezetimibe", genericName = "Ezetimibe", category = "Lipid-lowering", defaultDosage = "10mg"),
                // ── Thyroid ──────────────────────────────────────────────
                MedicineCatalog(name = "Levothyroxine", genericName = "Levothyroxine", category = "Thyroid", defaultDosage = "50mcg"),
                MedicineCatalog(name = "Thyroxine", genericName = "Levothyroxine", category = "Thyroid", defaultDosage = "25mcg"),
                MedicineCatalog(name = "Carbimazole", genericName = "Carbimazole", category = "Antithyroid", defaultDosage = "5mg"),
                // ── Vitamins & Supplements ───────────────────────────────
                MedicineCatalog(name = "Vitamin D3", genericName = "Cholecalciferol", category = "Supplement", defaultDosage = "60000 IU"),
                MedicineCatalog(name = "Vitamin B12", genericName = "Methylcobalamin", category = "Supplement", defaultDosage = "500mcg"),
                MedicineCatalog(name = "Folic Acid", genericName = "Folic Acid", category = "Supplement", defaultDosage = "5mg"),
                MedicineCatalog(name = "Iron (Ferrous Sulfate)", genericName = "Ferrous Sulfate", category = "Supplement", defaultDosage = "200mg"),
                MedicineCatalog(name = "Calcium Carbonate", genericName = "Calcium Carbonate", category = "Supplement", defaultDosage = "500mg"),
                MedicineCatalog(name = "Zinc Sulfate", genericName = "Zinc Sulfate", category = "Supplement", defaultDosage = "20mg"),
                MedicineCatalog(name = "Vitamin C", genericName = "Ascorbic Acid", category = "Supplement", defaultDosage = "500mg"),
                MedicineCatalog(name = "Multivitamin", genericName = "Multivitamin", category = "Supplement", defaultDosage = "1 tablet"),
                MedicineCatalog(name = "Omega-3", genericName = "Fish Oil", category = "Supplement", defaultDosage = "1000mg"),
                MedicineCatalog(name = "Biotin", genericName = "Biotin", category = "Supplement", defaultDosage = "5mg"),
                // ── Allergy / Respiratory ────────────────────────────────
                MedicineCatalog(name = "Cetirizine", genericName = "Cetirizine", category = "Antihistamine", defaultDosage = "10mg"),
                MedicineCatalog(name = "Fexofenadine", genericName = "Fexofenadine", category = "Antihistamine", defaultDosage = "120mg"),
                MedicineCatalog(name = "Loratadine", genericName = "Loratadine", category = "Antihistamine", defaultDosage = "10mg"),
                MedicineCatalog(name = "Chlorpheniramine", genericName = "Chlorpheniramine", category = "Antihistamine", defaultDosage = "4mg"),
                MedicineCatalog(name = "Montelukast", genericName = "Montelukast", category = "Antiasthmatic", defaultDosage = "10mg"),
                MedicineCatalog(name = "Salbutamol", genericName = "Salbutamol", category = "Bronchodilator", defaultDosage = "2mg"),
                MedicineCatalog(name = "Theophylline", genericName = "Theophylline", category = "Bronchodilator", defaultDosage = "200mg"),
                MedicineCatalog(name = "Budesonide", genericName = "Budesonide", category = "Corticosteroid", defaultDosage = "200mcg"),
                // ── Steroids ─────────────────────────────────────────────
                MedicineCatalog(name = "Prednisolone", genericName = "Prednisolone", category = "Corticosteroid", defaultDosage = "10mg"),
                MedicineCatalog(name = "Dexamethasone", genericName = "Dexamethasone", category = "Corticosteroid", defaultDosage = "0.5mg"),
                MedicineCatalog(name = "Methylprednisolone", genericName = "Methylprednisolone", category = "Corticosteroid", defaultDosage = "4mg"),
                MedicineCatalog(name = "Hydrocortisone", genericName = "Hydrocortisone", category = "Corticosteroid", defaultDosage = "10mg"),
                // ── Neurological / Psychiatric ───────────────────────────
                MedicineCatalog(name = "Alprazolam", genericName = "Alprazolam", category = "Anxiolytic", defaultDosage = "0.25mg"),
                MedicineCatalog(name = "Clonazepam", genericName = "Clonazepam", category = "Anxiolytic", defaultDosage = "0.5mg"),
                MedicineCatalog(name = "Escitalopram", genericName = "Escitalopram", category = "Antidepressant", defaultDosage = "10mg"),
                MedicineCatalog(name = "Sertraline", genericName = "Sertraline", category = "Antidepressant", defaultDosage = "50mg"),
                MedicineCatalog(name = "Fluoxetine", genericName = "Fluoxetine", category = "Antidepressant", defaultDosage = "20mg"),
                MedicineCatalog(name = "Amitriptyline", genericName = "Amitriptyline", category = "Antidepressant", defaultDosage = "25mg"),
                MedicineCatalog(name = "Gabapentin", genericName = "Gabapentin", category = "Anticonvulsant", defaultDosage = "300mg"),
                MedicineCatalog(name = "Pregabalin", genericName = "Pregabalin", category = "Anticonvulsant", defaultDosage = "75mg"),
                MedicineCatalog(name = "Phenytoin", genericName = "Phenytoin", category = "Anticonvulsant", defaultDosage = "100mg"),
                MedicineCatalog(name = "Levetiracetam", genericName = "Levetiracetam", category = "Anticonvulsant", defaultDosage = "500mg"),
                MedicineCatalog(name = "Zolpidem", genericName = "Zolpidem", category = "Sedative", defaultDosage = "10mg"),
                MedicineCatalog(name = "Melatonin", genericName = "Melatonin", category = "Sleep Aid", defaultDosage = "3mg"),
                // ── Cardiovascular ───────────────────────────────────────
                MedicineCatalog(name = "Clopidogrel", genericName = "Clopidogrel", category = "Antiplatelet", defaultDosage = "75mg"),
                MedicineCatalog(name = "Warfarin", genericName = "Warfarin", category = "Anticoagulant", defaultDosage = "5mg"),
                MedicineCatalog(name = "Digoxin", genericName = "Digoxin", category = "Cardiac", defaultDosage = "0.25mg"),
                MedicineCatalog(name = "Nitroglycerine", genericName = "Nitroglycerin", category = "Vasodilator", defaultDosage = "0.5mg"),
                MedicineCatalog(name = "Isosorbide Mononitrate", genericName = "Isosorbide Mononitrate", category = "Vasodilator", defaultDosage = "20mg"),
                // ── Anti-fungal / Anti-parasitic ─────────────────────────
                MedicineCatalog(name = "Fluconazole", genericName = "Fluconazole", category = "Antifungal", defaultDosage = "150mg"),
                MedicineCatalog(name = "Itraconazole", genericName = "Itraconazole", category = "Antifungal", defaultDosage = "100mg"),
                MedicineCatalog(name = "Albendazole", genericName = "Albendazole", category = "Antiparasitic", defaultDosage = "400mg"),
                MedicineCatalog(name = "Mebendazole", genericName = "Mebendazole", category = "Antiparasitic", defaultDosage = "100mg"),
                // ── Pain / Muscle Relaxants ──────────────────────────────
                MedicineCatalog(name = "Tramadol", genericName = "Tramadol", category = "Opioid Analgesic", defaultDosage = "50mg"),
                MedicineCatalog(name = "Tizanidine", genericName = "Tizanidine", category = "Muscle Relaxant", defaultDosage = "4mg"),
                MedicineCatalog(name = "Baclofen", genericName = "Baclofen", category = "Muscle Relaxant", defaultDosage = "10mg"),
                MedicineCatalog(name = "Thiocolchicoside", genericName = "Thiocolchicoside", category = "Muscle Relaxant", defaultDosage = "4mg"),
                // ── Urology ──────────────────────────────────────────────
                MedicineCatalog(name = "Tamsulosin", genericName = "Tamsulosin", category = "Alpha Blocker", defaultDosage = "0.4mg"),
                MedicineCatalog(name = "Sildenafil", genericName = "Sildenafil", category = "PDE5 Inhibitor", defaultDosage = "50mg"),
                // ── Skin / Topical (Oral forms) ──────────────────────────
                MedicineCatalog(name = "Isotretinoin", genericName = "Isotretinoin", category = "Retinoid", defaultDosage = "10mg"),
                MedicineCatalog(name = "Doxycycline (Acne)", genericName = "Doxycycline", category = "Antibiotic", defaultDosage = "100mg"),
                // ── Malaria / Tropical ───────────────────────────────────
                MedicineCatalog(name = "Hydroxychloroquine", genericName = "Hydroxychloroquine", category = "Antimalarial", defaultDosage = "200mg"),
                MedicineCatalog(name = "Chloroquine", genericName = "Chloroquine", category = "Antimalarial", defaultDosage = "250mg"),
                // ── Gout ─────────────────────────────────────────────────
                MedicineCatalog(name = "Allopurinol", genericName = "Allopurinol", category = "Antigout", defaultDosage = "100mg"),
                MedicineCatalog(name = "Colchicine", genericName = "Colchicine", category = "Antigout", defaultDosage = "0.5mg"),
                // ── Cough & Cold ─────────────────────────────────────────
                MedicineCatalog(name = "Ambroxol", genericName = "Ambroxol", category = "Expectorant", defaultDosage = "30mg"),
                MedicineCatalog(name = "Bromhexine", genericName = "Bromhexine", category = "Expectorant", defaultDosage = "8mg"),
                MedicineCatalog(name = "Dextromethorphan", genericName = "Dextromethorphan", category = "Antitussive", defaultDosage = "15mg"),
                MedicineCatalog(name = "Guaifenesin", genericName = "Guaifenesin", category = "Expectorant", defaultDosage = "200mg"),
                MedicineCatalog(name = "Pseudoephedrine", genericName = "Pseudoephedrine", category = "Decongestant", defaultDosage = "60mg"),
                MedicineCatalog(name = "Phenylephrine", genericName = "Phenylephrine", category = "Decongestant", defaultDosage = "10mg"),
                // ── Women's Health ───────────────────────────────────────
                MedicineCatalog(name = "Progesterone", genericName = "Progesterone", category = "Hormone", defaultDosage = "200mg"),
                MedicineCatalog(name = "Clomiphene", genericName = "Clomiphene", category = "Fertility", defaultDosage = "50mg"),
                MedicineCatalog(name = "Norethisterone", genericName = "Norethisterone", category = "Hormone", defaultDosage = "5mg"),
                MedicineCatalog(name = "Mifepristone", genericName = "Mifepristone", category = "Hormone", defaultDosage = "200mg"),
                // ── Eye Drops (oral companion) ───────────────────────────
                MedicineCatalog(name = "Acetazolamide", genericName = "Acetazolamide", category = "Glaucoma", defaultDosage = "250mg")
            )
            dao.insertAll(medicines)
        }
    }
}
