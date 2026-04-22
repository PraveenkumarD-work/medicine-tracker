# Medicine Tracker — Build & Sideload Guide

## Prerequisites

Install these once on your PC:
1. **Android Studio** — download from https://developer.android.com/studio
2. **JDK 17** — Android Studio bundles this; no separate install needed

---

## Step 1: Add the Pill-Shaking Sound

The app expects a custom alarm sound for medicine reminders.

1. Get any short audio file (MP3 or OGG) — name it `pills_shaking.mp3`
   - You can record pills shaking in a bottle, or use any short alert sound
2. Place the file at:
   ```
   MedicineTracker/app/src/main/res/raw/pills_shaking.mp3
   ```
   (Create the `raw/` folder if it doesn't exist)

If you skip this step, the notification will still show — just with the system's default notification sound.

---

## Step 2: Open in Android Studio

1. Open Android Studio
2. Click **File → Open**
3. Navigate to and select the `MedicineTracker/` folder
4. Wait for Gradle to sync (first time may take 3–5 minutes — it downloads dependencies)
5. When you see "BUILD SUCCESSFUL" in the console, you're ready

---

## Step 3: Build the APK

In Android Studio:

**Option A — Debug APK (quick, for testing):**
1. Menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for build to finish
3. Click **"locate"** in the bottom right popup
4. APK is at: `app/build/outputs/apk/debug/app-debug.apk`

**Option B — Release APK (for permanent install):**
1. Menu: **Build → Generate Signed Bundle / APK**
2. Select **APK**
3. Create a new keystore (save the password — you need it for future updates)
4. Choose **release** build variant
5. APK is at: `app/build/outputs/apk/release/app-release.apk`

---

## Step 4: Sideload onto Each Family Phone

Do this on **each Android phone**:

### Enable installation from unknown sources:
1. **Settings → Security** (or **Privacy**)
2. Enable **"Install unknown apps"** or **"Unknown sources"**
3. On newer Android: **Settings → Apps → Special app access → Install unknown apps**
   - Enable for **Files** or **Chrome** (whichever you'll use to open the APK)

### Transfer and install the APK:
- **Via USB:** Copy `app-debug.apk` to the phone's Downloads folder, then tap it in Files
- **Via WhatsApp/Telegram:** Send the APK file to yourself in the app, then tap to install
- **Via Google Drive:** Upload the APK, open on the phone, tap Download, then tap to install

---

## Step 5: First-Time Setup on Each Phone

When the app opens for the first time:

1. **Grant notification permission** — tap Allow when prompted (critical for alarms)
2. **Grant activity recognition** — tap Allow (needed for Driving Mode)
3. **Exact alarm permission** — On Android 12+, the app may redirect you to Settings to grant "Alarms & Reminders" permission — approve it
4. Tap **+ Add Medicine** to add your first medicine
5. The medicine catalog (120+ medicines) is pre-loaded — just type the name to search

---

## Step 6: Manufacturer-Specific Battery Settings

**Samsung:** Settings → Battery → Background usage limits → Never sleeping apps → Add "Medicine Tracker"

**Xiaomi/MIUI:** Settings → Apps → Manage apps → Medicine Tracker → Battery Saver → No restrictions

**OnePlus:** Settings → Battery → Battery optimization → Medicine Tracker → Don't optimize

**Oppo/Vivo:** Settings → Battery → High background power consumption → Add Medicine Tracker

This is critical. Without this, aggressive battery optimization will kill alarms overnight.

---

## Important Notes

- **100% offline** — no internet required, no accounts, no data leaves the device
- **Each phone is independent** — medicines must be added separately on each family member's phone
- **Alarms survive reboot** — the app automatically restores all alarms on device boot
- **Database location** — stored in the app's private storage; cleared only if app is uninstalled
- **Future updates** — re-build the APK and reinstall (existing data is preserved)

---

## File Structure Reference

```
MedicineTracker/
├── app/
│   ├── build.gradle              ← Dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml   ← Permissions
│   │   ├── assets/               ← (empty, DB seeded in code)
│   │   ├── res/
│   │   │   ├── raw/              ← PUT pills_shaking.mp3 HERE
│   │   │   ├── layout/           ← UI screens
│   │   │   └── values/           ← Colors, strings, themes
│   │   └── java/com/familyhealth/medicinetracker/
│   │       ├── data/             ← Room DB, DAOs, Repository
│   │       ├── domain/model/     ← Entities and enums
│   │       ├── presentation/     ← ViewModels and Fragments
│   │       ├── service/
│   │       │   ├── alarm/        ← AlarmReceiver, NagReceiver, BootReceiver
│   │       │   ├── driving/      ← DrivingModeService, ActivityTransitionReceiver
│   │       │   ├── notification/ ← NotificationHelper, ActionReceiver
│   │       │   └── worker/       ← StockCheckWorker
│   │       └── MedicineTrackerApp.kt
└── BUILD_GUIDE.md                ← This file
```
