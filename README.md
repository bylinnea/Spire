# 🌿 Spire

A personal Android app for tracking plant care with watering, fertilizing, repotting, misting, rotating, and cleaning schedules, with push reminders, health logs, a plant graveyard, and AI-powered plant identification.

Built in Kotlin for Android 7.0+ (API 24+).

---

## Features

**Care & scheduling**
- Per-plant schedules for watering, fertilizing, repotting, misting, rotating, and cleaning
- Winter/seasonal mode - switch all plants to reduced care schedules at once
- Overdue dashboard - see at a glance what needs attention
- Care detail view - full history and tips per care type

**Plants**
- Photos with circular crop, camera or gallery
- Growth photo timeline
- Species, location, date acquired, notes, pet safety flag
- Light meter - measure light levels where your plant sits
- QR code sharing - share a plant with another Spire user
- Rooms - group plants by location

**Graveyard**
- Mark plants as dead instead of deleting them
- Browse fallen plants with date of death
- Restore accidentally killed plants
- Permanently delete when you're ready to let go

**Backup & restore**
- Export all plants and care logs to a JSON file
- Import to restore or move to a new device
- Merge import - skips plants that already exist by name

**Notifications**
- Daily reminders via WorkManager when plants are overdue
- Per-plant notifications or a single combined summary (toggle in Settings)

**AI features** (optional, requires API keys)
- Plant identification from photos via PlantNet
- AI care recommendations per plant via Claude (Anthropic)
- AI Plant Doctor - describe symptoms and get advice
- AI name suggestions in your preferred style

**Other**
- Home screen widget showing overdue plants
- Plant Sitter mode - temporary care delegation
- Swipe navigation between plant detail screens
- Sort by name, next due, or most overdue
- Undo snackbars for care actions and deletions
- Haptic feedback on key interactions
- Onboarding flow for first launch

---

## Tech Stack

| Layer | Library |
|-------|---------|
| Language | Kotlin |
| UI | XML layouts + Material Design 3 |
| Database | Room (SQLite) |
| Background work | WorkManager |
| Image loading | Glide |
| Image cropping | uCrop |
| Networking | OkHttp |
| API key storage | EncryptedSharedPreferences |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- Android device or emulator running API 24+

### Clone and build
```bash
git clone https://github.com/bylinnea/Spire.git
cd Spire
```
Open in Android Studio and run on a device or emulator. No additional build setup is required.

### AI features (optional)
The app works fully without AI features. To enable them:

1. Open **Settings** in the app
2. Enter your [Anthropic API key](https://console.anthropic.com/) for AI Plant Doctor and care recommendations
3. Enter your [PlantNet API key](https://my.plantnet.org/) for photo-based plant identification (optional)
4. Toggle **AI features** on

Keys are stored locally in `EncryptedSharedPreferences` and never leave your device except for the API calls themselves.

---

## Database

The local SQLite database (`plant_database`) is stored in the app's private data directory. Room migrations cover all schema versions (v1 → v15), so upgrading the app never loses data.

| Version | Change |
|---------|--------|
| 1 | Initial schema |
| 2 | Added `lastWateredDate` |
| 3 | Added `photoUri` |
| 4 | Added `species`, `location`, `dateAcquired`, `notes` |
| 5 | Added fertilizer, repotting, misting, rotating fields |
| 6 | Added `plant_log` table for health notes |
| 7–13 | Cleaning, winter schedules, pet safety, AI tips, rooms, growth photos |
| 14 | Added `PlantPhoto` table for growth timeline |
| 15 | Added `diedDate` for graveyard feature |

---

## Project Structure

```
app/src/main/java/no/bylinnea/spire/
├── data/
│   ├── Plant.kt                  - Room entity
│   ├── PlantDao.kt               - Database queries
│   ├── PlantDatabase.kt          - Room DB + migrations
│   ├── PlantLog.kt               - Health log entry entity
│   ├── PlantLogDao.kt            - Log queries
│   ├── PlantPhoto.kt             - Growth photo entity
│   ├── PlantPhotoDao.kt          - Growth photo queries
│   └── RoomItem.kt               - Sealed class for grouped list
├── ui/
│   ├── MainActivity.kt           - Plant list
│   ├── PlantDetailActivity.kt    - Plant detail + care actions
│   ├── AddPlantActivity.kt       - Add new plant
│   ├── EditPlantActivity.kt      - Edit existing plant
│   ├── CareDetailActivity.kt     - Care type detail + history
│   ├── GraveyardActivity.kt      - Dead plants
│   ├── SettingsActivity.kt       - Settings + backup
│   ├── SplashActivity.kt         - Launch screen
│   ├── OnboardingActivity.kt     - First-run onboarding
│   ├── BaseActivity.kt           - Shared activity base
│   ├── BasePlantFormActivity.kt  - Shared add/edit logic
│   ├── PlantAdapter.kt           - Main list adapter
│   ├── PlantLogAdapter.kt        - Health log adapter
│   └── GrowthPhotoAdapter.kt     - Growth photo adapter
├── service/
│   ├── PlantCareService.kt       - AI care advice (Anthropic)
│   ├── PlantDoctorService.kt     - AI plant doctor
│   ├── WateringWorker.kt         - WorkManager notification worker
│   ├── WateringScheduler.kt      - Schedules daily reminder
│   ├── SpireWidget.kt         - Home screen widget
│   └── WidgetUpdater.kt          - Widget refresh logic
└── util/
    ├── ApiKeyManager.kt          - Encrypted key + prefs storage
    ├── BackupHelper.kt           - JSON export/import
    ├── CareTask.kt               - Care task model + extensions
    ├── CropHelper.kt             - uCrop integration
    ├── HapticHelper.kt           - Haptic feedback
    ├── PlantShareHelper.kt       - QR plant sharing
    ├── ShareQrHelper.kt          - QR code generation
    └── SortOption.kt             - Sort enum + logic
```

---

## Notifications

The app uses WorkManager to schedule a daily check at 9 AM. When plants are overdue:
- One notification per overdue plant listing all overdue tasks
- A summary notification when multiple plants need attention
- Optionally a single combined notification (toggle in Settings)

---

## Privacy

- No analytics, no tracking, no ads
- No data leaves the device except API calls to PlantNet and Anthropic (only when AI features are enabled and you initiate an action)
- All API keys stored in Android's `EncryptedSharedPreferences`
- Backup files are created locally and shared only by your explicit action

---

## License

Personal project - feel free to fork and adapt.